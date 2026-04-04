package me.xiaok.opencode.ui.screens.experimental

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.WorkspaceCreateRequest
import me.xiaok.opencode.domain.model.WorktreeCreateRequest
import me.xiaok.opencode.domain.model.WorktreeDeleteRequest
import me.xiaok.opencode.domain.model.WorktreeResetRequest
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

// === UI State ===

data class ExperimentalUiState(
    val selectedTab: Int = 0,
    // Workspaces
    val workspaces: JsonElement? = null,
    val isLoadingWorkspaces: Boolean = false,
    // Worktrees
    val worktrees: List<String> = emptyList(),
    val isLoadingWorktrees: Boolean = false,
    // Resources
    val resources: JsonElement? = null,
    val isLoadingResources: Boolean = false,
    // General
    val error: String? = null,
    val isCreating: Boolean = false,
)

// === ViewModel ===

@HiltViewModel
class ExperimentalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
    private val errorCollector: ErrorCollector,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val _uiState = MutableStateFlow(ExperimentalUiState())
    val uiState: StateFlow<ExperimentalUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    // === Tab Selection ===

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    // === Data Loading ===

    fun loadAll() {
        loadWorkspaces()
        loadWorktrees()
        loadResources()
    }

    fun loadWorkspaces() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWorkspaces = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val workspaces = api.listWorkspaces(server)
                _uiState.update { it.copy(workspaces = workspaces, isLoadingWorkspaces = false) }
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(isLoadingWorkspaces = false, error = e.message) }
            }
        }
    }

    fun loadWorktrees() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWorktrees = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val worktrees = api.listWorktrees(server)
                _uiState.update { it.copy(worktrees = worktrees, isLoadingWorktrees = false) }
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(isLoadingWorktrees = false, error = e.message) }
            }
        }
    }

    fun loadResources() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingResources = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val resources = api.getExperimentalResources(server)
                _uiState.update { it.copy(resources = resources, isLoadingResources = false) }
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(isLoadingResources = false, error = e.message) }
            }
        }
    }

    // === Workspace Operations ===

    fun createWorkspace(id: String? = null, type: String = "", branch: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.createWorkspace(server, WorkspaceCreateRequest(id = id, type = type, branch = branch))
                _uiState.update { it.copy(isCreating = false) }
                loadWorkspaces()
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(isCreating = false, error = e.message) }
            }
        }
    }

    fun deleteWorkspace(workspaceId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.deleteWorkspace(server, workspaceId)
                loadWorkspaces()
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // === Worktree Operations ===

    fun createWorktree(name: String? = null, startCommand: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.createWorktree(server, WorktreeCreateRequest(name = name, startCommand = startCommand))
                _uiState.update { it.copy(isCreating = false) }
                loadWorktrees()
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(isCreating = false, error = e.message) }
            }
        }
    }

    fun deleteWorktree(directory: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.deleteWorktree(server, WorktreeDeleteRequest(directory = directory))
                loadWorktrees()
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun resetWorktree(directory: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.resetWorktree(server, WorktreeResetRequest(directory = directory))
                loadWorktrees()
            } catch (e: Exception) {
                errorCollector.logError(e, "Experimental")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
