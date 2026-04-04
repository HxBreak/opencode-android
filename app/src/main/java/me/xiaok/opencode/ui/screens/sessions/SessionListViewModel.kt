package me.xiaok.opencode.ui.screens.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.Session
import me.xiaok.opencode.domain.model.SessionStatus
import javax.inject.Inject

sealed class SessionArchiveFilter(val label: String) {
    data object All : SessionArchiveFilter("All")
    data object Active : SessionArchiveFilter("Active")
    data object Archived : SessionArchiveFilter("Archived")
}

data class SessionListUiState(
    val projectName: String = "",
    val sessions: List<Session> = emptyList(),
    val sessionStatuses: Map<String, SessionStatus> = emptyMap(),
    val vcsBranch: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSessions: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val collapsedDirectories: Set<String> = emptySet(),
    val archiveFilter: SessionArchiveFilter = SessionArchiveFilter.All,
    val unreadSessions: Set<String> = emptySet(),
    val searchQuery: String = "",
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val directory: String? = savedStateHandle["directory"]

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _selectedSessions = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)
    private val _archiveFilter = MutableStateFlow<SessionArchiveFilter>(SessionArchiveFilter.All)
    private val _childrenSessions = MutableStateFlow<Map<String, List<Session>>>(emptyMap())
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionListUiState> = combine(
        eventReducer.serverSessions,
        eventReducer.sessions,
        eventReducer.sessionStatuses,
        _isLoading,
        _error,
    ) { serverSessions, allSessions, statuses, loading, err ->
        SessionPartialState(
            serverSessions = serverSessions,
            allSessions = allSessions,
            statuses = statuses,
            loading = loading,
            error = err,
        )
    }.combine(eventReducer.vcsBranch) { partial, branch ->
        partial.copy(vcsBranch = branch)
    }.flatMapLatest { partial ->
        combine(
            _selectedSessions,
            _isSelectionMode,
            settingsRepository.collapsedDirectories,
            _archiveFilter,
            eventReducer.unreadSessions,
        ) { selected, selectionMode, collapsed, archiveFilter, allUnread ->
            SessionListPartialUiState(
                selectedSessions = selected,
                isSelectionMode = selectionMode,
                collapsedDirectories = collapsed,
                archiveFilter = archiveFilter,
                unreadSessions = allUnread,
            )
        }.combine(_searchQuery) { ui, searchQuery ->
            val sessionIds = partial.serverSessions[serverId] ?: emptySet()
            val serverSessionsList = sessionIds
                .mapNotNull { partial.allSessions[it] }
                .filter { session ->
                    // Only show sessions belonging to this project's directory
                    directory == null || session.directory == directory
                }
                .filter { session ->
                    // Only show root sessions (sub-sessions are shown under their parent)
                    session.parentID == null
                }
                .filter { session ->
                    when (ui.archiveFilter) {
                        is SessionArchiveFilter.All -> true
                        is SessionArchiveFilter.Active -> session.time.archived == null
                        is SessionArchiveFilter.Archived -> session.time.archived != null
                    }
                }
                .filter { session ->
                    // Search filter: match against title (case-insensitive)
                    if (searchQuery.isBlank()) true
                    else session.title.contains(searchQuery, ignoreCase = true)
                }
                .sortedByDescending { it.time.updated }

            val projectName = directory?.substringAfterLast("/")?.ifEmpty { null } ?: "Sessions"

            SessionListUiState(
                projectName = projectName,
                sessions = serverSessionsList,
                sessionStatuses = partial.statuses,
                vcsBranch = partial.vcsBranch,
                isLoading = partial.loading,
                error = partial.error,
                selectedSessions = ui.selectedSessions,
                isSelectionMode = ui.isSelectionMode,
                collapsedDirectories = ui.collapsedDirectories,
                archiveFilter = ui.archiveFilter,
                unreadSessions = ui.unreadSessions[serverId] ?: emptySet(),
                searchQuery = searchQuery,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUiState())

    private data class SessionPartialState(
        val serverSessions: Map<String, Set<String>>,
        val allSessions: Map<String, Session>,
        val statuses: Map<String, SessionStatus>,
        val loading: Boolean,
        val error: String?,
        val vcsBranch: String? = null,
    )

    private data class SessionListPartialUiState(
        val selectedSessions: Set<String>,
        val isSelectionMode: Boolean,
        val collapsedDirectories: Set<String>,
        val archiveFilter: SessionArchiveFilter,
        val unreadSessions: Map<String, Set<String>>,
    )

    init {
        refreshSessions()
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _error.value = "Server not found"
                    _isLoading.value = false
                    return@launch
                }
                val sessions = api.listSessions(server, directory = directory, roots = true)
                eventReducer.setSessions(serverId, sessions)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load sessions"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createSession(title: String? = null, onResult: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val session = api.createSession(server, directory = directory, title = title)
                eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionCreated(session))
                onResult?.invoke(session.id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create session"
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val deleted = api.deleteSession(server, sessionId)
                if (deleted) {
                    val session = eventReducer.sessions.value[sessionId] ?: return@launch
                    eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionDeleted(session))
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete session"
            }
        }
    }

    fun deleteSelectedSessions() {
        viewModelScope.launch {
            val selected = _selectedSessions.value
            selected.forEach { sessionId ->
                deleteSession(sessionId)
            }
            exitSelectionMode()
        }
    }

    fun updateSessionTitle(sessionId: String, title: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val updated = api.updateSession(server, sessionId, title = title)
                eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionUpdated(updated))
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update session"
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val timestamp = System.currentTimeMillis()
                val updated = api.updateSession(server, sessionId, archived = timestamp)
                eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionUpdated(updated))
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to archive session"
            }
        }
    }

    fun unarchiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val updated = api.updateSession(server, sessionId, unarchive = true)
                eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionUpdated(updated))
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to unarchive session"
            }
        }
    }

    fun archiveSelectedSessions() {
        viewModelScope.launch {
            val selected = _selectedSessions.value
            selected.forEach { sessionId ->
                archiveSession(sessionId)
            }
            exitSelectionMode()
        }
    }

    fun setArchiveFilter(filter: SessionArchiveFilter) {
        _archiveFilter.value = filter
    }

    // === Selection mode ===

    fun toggleSelection(sessionId: String) {
        val current = _selectedSessions.value
        _selectedSessions.value = if (sessionId in current) {
            current - sessionId
        } else {
            current + sessionId
        }
        if (_selectedSessions.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun enterSelectionMode(sessionId: String) {
        _isSelectionMode.value = true
        _selectedSessions.value = setOf(sessionId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSessions.value = emptySet()
    }

    fun toggleDirectoryCollapsed(directory: String) {
        viewModelScope.launch {
            val current = settingsRepository.collapsedDirectories.first()
            val updated = if (directory in current) current - directory else current + directory
            settingsRepository.setCollapsedDirectories(updated)
        }
    }

    fun selectAll() {
        _selectedSessions.value = uiState.value.sessions.map { it.id }.toSet()
    }

    fun loadSessionChildren(sessionId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val children = api.getSessionChildren(server, sessionId)
                _childrenSessions.value = _childrenSessions.value.toMutableMap().apply {
                    put(sessionId, children)
                }
            } catch (_: Exception) { }
        }
    }

    fun getSessionChildren(sessionId: String): List<Session> {
        return _childrenSessions.value[sessionId] ?: emptyList()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }
}
