package me.xiaok.opencode.ui.screens.diff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.FileDiff
import me.xiaok.opencode.domain.model.SseEvent
import javax.inject.Inject

data class SessionDiffUiState(
    val diffs: List<FileDiff> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SessionDiffViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")
    private val sessionId: String = savedStateHandle["sessionId"]
        ?: throw IllegalArgumentException("sessionId is required")

    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SessionDiffUiState> = combine(
        eventReducer.sessionDiffs,
        _isLoading,
        _error,
    ) { allDiffs, loading, error ->
        val diffs = allDiffs[sessionId] ?: emptyList()
        SessionDiffUiState(
            diffs = diffs,
            isLoading = loading,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDiffUiState())

    init {
        fetchDiffs()
    }

    private fun fetchDiffs() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _error.value = "Server not found"
                    _isLoading.value = false
                    return@launch
                }
                val session = eventReducer.sessions.value[sessionId]
                val directory = session?.directory
                val workspace = session?.workspaceID
                val diffs = api.getSessionDiff(server, sessionId, directory = directory, workspace = workspace)
                // Push into EventReducer so SessionReviewTab picks it up
                eventReducer.processEvent(serverId, SseEvent.SessionDiff(sessionId, diffs))
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load diffs"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() {
        fetchDiffs()
    }
}
