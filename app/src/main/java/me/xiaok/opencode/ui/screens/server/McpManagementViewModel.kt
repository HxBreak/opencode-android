package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.McpServerCreateRequest
import me.xiaok.opencode.domain.model.McpStatus
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

data class McpManagementUiState(
    val mcpServers: Map<String, McpStatus> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
)

@HiltViewModel
class McpManagementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
    private val errorCollector: ErrorCollector,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val _uiState = MutableStateFlow(McpManagementUiState())
    val uiState: StateFlow<McpManagementUiState> = _uiState.asStateFlow()

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val servers = api.listMcpServers(server)
                _uiState.update { it.copy(mcpServers = servers, isLoading = false) }
            } catch (e: Exception) {
                errorCollector.logError(e, "McpManagement")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun addServer(request: McpServerCreateRequest) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.addMcpServer(server, request)
                _uiState.update { it.copy(showAddDialog = false) }
                loadServers()
            } catch (e: Exception) {
                errorCollector.logError(e, "McpManagement")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun connectServer(name: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.connectMcpServer(server, name)
                loadServers()
            } catch (e: Exception) {
                errorCollector.logError(e, "McpManagement")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun disconnectServer(name: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.disconnectMcpServer(server, name)
                loadServers()
            } catch (e: Exception) {
                errorCollector.logError(e, "McpManagement")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeAuth(name: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.removeMcpAuth(server, name)
                loadServers()
            } catch (e: Exception) {
                errorCollector.logError(e, "McpManagement")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
