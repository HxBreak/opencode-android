package me.xiaok.opencode.ui.screens.terminal

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.api.WsClient
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.PtyCreateRequest
import me.xiaok.opencode.domain.model.PtySize
import me.xiaok.opencode.domain.model.PtyUpdateRequest
import me.xiaok.opencode.ui.components.terminal.TerminalState
import javax.inject.Inject

data class TerminalUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val ptyId: String? = null,
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val wsClient: WsClient,
    private val serverRepository: ServerRepository,
    private val eventReducer: EventReducer,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")
    private val sessionId: String? = savedStateHandle["sessionId"]

    // Terminal state
    private val _terminalState = MutableStateFlow<TerminalState?>(null)
    val terminalState: StateFlow<TerminalState?> = _terminalState.asStateFlow()

    private val _terminalConnection = MutableStateFlow<WsClient.InteractiveTerminalConnection?>(null)

    private val _isConnecting = MutableStateFlow(false)
    private val _isConnected = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _ptyId = MutableStateFlow<String?>(null)

    private var terminalOutputJob: kotlinx.coroutines.Job? = null

    /** Last known history cursor from the server control frame. Used to avoid re-downloading history on reconnect. */
    private var _lastCursor: Int = 0

    /** Scope for fire-and-forget cleanup that outlives viewModelScope. Cancelled in onCleared(). */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Current session's directory for PTY cwd and server routing. */
    private val sessionDirectory: String?
        get() = sessionId?.let { eventReducer.sessions.value[it]?.directory }

    val uiState: StateFlow<TerminalUiState> = combine(
        _isConnecting,
        _isConnected,
        _error,
        _ptyId,
    ) { connecting, connected, err, pty ->
        TerminalUiState(
            isConnecting = connecting,
            isConnected = connected,
            error = err,
            ptyId = pty,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TerminalUiState())

    init {
        startTerminal()
    }

    fun startTerminal() {
        if (_terminalConnection.value != null) return // already running

        viewModelScope.launch {
            _isConnecting.value = true
            _error.value = null
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _error.value = "Server not found"
                    _isConnecting.value = false
                    return@launch
                }

                val cwd = sessionDirectory

                // Create PTY via REST API with session's working directory
                val ptyInfo = api.createPty(server, PtyCreateRequest(cwd = cwd), directory = cwd)

                // Validate PTY was created successfully
                if (ptyInfo.id.isBlank()) {
                    _error.value = "Failed to create terminal session"
                    _terminalState.value = null
                    return@launch
                }

                _ptyId.value = ptyInfo.id

                // Create terminal state
                val state = TerminalState()
                _terminalState.value = state

                // Connect via WebSocket, passing directory for routing headers
                val connection = wsClient.connectInteractive(
                    conn = server,
                    ptyId = ptyInfo.id,
                    cursor = _lastCursor,  // Resume from last known cursor to avoid re-downloading history
                    directory = cwd,
                )
                _terminalConnection.value = connection

                // Feed output into TerminalState and observe cursor for reconnection
                terminalOutputJob = viewModelScope.launch {
                    // Launch a collector for cursor updates from control frames
                    val cursorJob = launch {
                        connection.cursorFlow.collect { cursor ->
                            if (cursor != null) {
                                _lastCursor = cursor
                            }
                        }
                    }
                    try {
                        connection.output.collect { data ->
                            state.processData(data)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Terminal output stream error: ${e.message}")
                        _error.value = "Terminal connection failed: ${e.message}"
                        _isConnected.value = false
                    } finally {
                        cursorJob.cancel()
                    }
                }

                _isConnected.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start terminal: ${e.message}", e)
                _error.value = e.message ?: "Failed to start terminal"
                _terminalState.value = null
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun stopTerminal() {
        terminalOutputJob?.cancel()
        terminalOutputJob = null
        val connection = _terminalConnection.value
        _terminalConnection.value = null
        connection?.disconnect()

        _isConnected.value = false

        // Delete PTY via API (best-effort)
        if (connection != null) {
            viewModelScope.launch {
                try {
                    val server = serverRepository.getServer(serverId) ?: return@launch
                    val cwd = sessionDirectory
                    api.deletePty(server, connection.ptyId, directory = cwd)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete PTY ${connection.ptyId}: ${e.message}")
                }
            }
        }

        _terminalState.value = null
        _ptyId.value = null
    }

    fun sendTerminalInput(text: String) {
        _terminalConnection.value?.send(text)
    }

    /**
     * Called when the TerminalView computes new grid dimensions.
     * Updates both the local TerminalState buffer and the server-side PTY size.
     */
    fun resizeTerminal(cols: Int, rows: Int) {
        _terminalState.value?.resize(cols, rows)
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val connection = _terminalConnection.value ?: return@launch
                val cwd = sessionDirectory
                api.updatePty(server, connection.ptyId, PtyUpdateRequest(size = PtySize(rows, cols)), directory = cwd)
            } catch (_: Exception) { }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        terminalOutputJob?.cancel()
        _terminalConnection.value?.disconnect()

        // Best-effort PTY cleanup — fire and forget via cleanupScope
        // (viewModelScope is already cancelled at this point)
        val connection = _terminalConnection.value
        if (connection != null) {
            cleanupScope.launch {
                try {
                    val server = serverRepository.getServer(serverId) ?: return@launch
                    val cwd = sessionDirectory
                    api.deletePty(server, connection.ptyId, directory = cwd)
                } catch (_: Exception) { }
            }
        }

        _terminalState.value = null
        _terminalConnection.value = null

        // Cancel cleanup scope after giving cleanup a chance to run
        cleanupScope.cancel()
    }

    companion object {
        private const val TAG = "TerminalViewModel"
    }
}
