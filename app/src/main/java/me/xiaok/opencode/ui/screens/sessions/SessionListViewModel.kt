package me.xiaok.opencode.ui.screens.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.PtyInfo
import me.xiaok.opencode.domain.model.Session
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.utils.ErrorCollector
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
    val archiveFilter: SessionArchiveFilter = SessionArchiveFilter.Active,
    val unreadSessions: Set<String> = emptySet(),
    val searchQuery: String = "",
    val activePtyCount: Int = 0,
    val ptyList: List<PtyInfo> = emptyList(),
    /** Token usage per session (parent + children). Only includes sessions whose messages are loaded. */
    val sessionTokens: Map<String, Long> = emptyMap(),
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val errorCollector: ErrorCollector,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val directory: String? = savedStateHandle["directory"]

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _selectedSessions = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)
    private val _archiveFilter = MutableStateFlow<SessionArchiveFilter>(SessionArchiveFilter.Active)
    private val _childrenSessions = MutableStateFlow<Map<String, List<Session>>>(emptyMap())
    private val _searchQuery = MutableStateFlow("")

    /**
     * Pre-sorted, debounced session list.
     *
     * Sorting is decoupled from the main uiState combine chain so that rapid
     * SSE events (message.updated, session.updated, status changes…) do NOT
     * trigger visual re-ordering on every emission.  The list is re-sorted at
     * most once per second, which eliminates the "jumping" effect when messages
     * arrive quickly.
     *
     * The first emission passes through immediately so the UI is not delayed
     * on initial load; only subsequent rapid updates are debounced.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val _sortedSessions: StateFlow<List<Session>> = combine(
        eventReducer.serverSessions,
        eventReducer.sessions,
        _archiveFilter,
        _searchQuery,
    ) { serverSessions, allSessions, archiveFilter, searchQuery ->
        val sessionIds = serverSessions[serverId] ?: emptySet()
        sessionIds
            .mapNotNull { allSessions[it] }
            .filter { session -> directory == null || session.directory == directory }
            .filter { session -> session.parentID == null }
            .filter { session ->
                when (archiveFilter) {
                    is SessionArchiveFilter.All -> true
                    is SessionArchiveFilter.Active -> session.time.archived == null
                    is SessionArchiveFilter.Archived -> session.time.archived != null
                }
            }
            .filter { session ->
                if (searchQuery.isBlank()) true
                else session.title.contains(searchQuery, ignoreCase = true)
            }
            .sortedByDescending { it.time.updated }
    }
            // First non-empty emission passes through immediately so the UI is
        // not delayed on initial load; subsequent emissions are debounced to
        // avoid visual jumping when messages arrive quickly.
        .debounceAfterFirst(1.seconds)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionListUiState> = combine(
        _sortedSessions,
        eventReducer.sessionStatuses,
        _isLoading,
        _error,
        eventReducer.vcsBranch,
    ) { sessions, statuses, loading, err, branch ->
        SessionPartialState(
            sessions = sessions,
            statuses = statuses,
            loading = loading,
            error = err,
            vcsBranch = branch,
        )
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
        }.combine(eventReducer.ptySessions) { ui, allPtys ->
            Pair(ui, allPtys)
        }.combine(eventReducer.messages) { pair, allMessages ->
            val (ui, allPtys) = pair
            val projectName = directory?.substringAfterLast("/")?.ifEmpty { null } ?: "Sessions"

            // Compute token totals per session (parent + children)
            val allSessions = eventReducer.sessions.value
            val serverSessionIds = eventReducer.serverSessions.value[serverId] ?: emptySet()
            val sessionTokens = mutableMapOf<String, Long>()
            for (session in partial.sessions) {
                var total = 0L
                // Parent session tokens
                total += computeSessionTokens(allMessages[session.id])
                // Children tokens
                for (id in serverSessionIds) {
                    val s = allSessions[id]
                    if (s != null && s.parentID == session.id) {
                        total += computeSessionTokens(allMessages[id])
                    }
                }
                if (total > 0) {
                    sessionTokens[session.id] = total
                }
            }

            SessionListUiState(
                projectName = projectName,
                sessions = partial.sessions,
                sessionStatuses = partial.statuses,
                vcsBranch = partial.vcsBranch,
                isLoading = partial.loading,
                error = partial.error,
                selectedSessions = ui.selectedSessions,
                isSelectionMode = ui.isSelectionMode,
                collapsedDirectories = ui.collapsedDirectories,
                archiveFilter = ui.archiveFilter,
                unreadSessions = ui.unreadSessions[serverId] ?: emptySet(),
                searchQuery = _searchQuery.value,
                activePtyCount = allPtys[serverId]
                    ?.count { (_, pty) -> pty.status != "exited" } ?: 0,
                ptyList = allPtys[serverId]?.values?.toList() ?: emptyList(),
                sessionTokens = sessionTokens,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUiState())

    private data class SessionPartialState(
        val sessions: List<Session>,
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
                errorCollector.logError(e, "SessionList")
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
                errorCollector.logError(e, "SessionList")
                _error.value = e.message ?: "Failed to create session"
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val deleted = api.deleteSession(server, sessionId, directory = directory)
                if (deleted) {
                    val session = eventReducer.sessions.value[sessionId] ?: return@launch
                    eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionDeleted(session))
                }
            } catch (e: Exception) {
                errorCollector.logError(e, "SessionList")
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
                val updated = api.updateSession(server, sessionId, title = title, directory = directory)
                eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionUpdated(updated))
            } catch (e: Exception) {
                errorCollector.logError(e, "SessionList")
                _error.value = e.message ?: "Failed to update session"
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val timestamp = System.currentTimeMillis()
                val updated = api.updateSession(server, sessionId, archived = timestamp, directory = directory)
                eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionUpdated(updated))
            } catch (e: Exception) {
                errorCollector.logError(e, "SessionList")
                _error.value = e.message ?: "Failed to archive session"
            }
        }
    }

    fun unarchiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val updated = api.updateSession(server, sessionId, unarchive = true, directory = directory)
                eventReducer.processEvent(serverId, me.xiaok.opencode.domain.model.SseEvent.SessionUpdated(updated))
            } catch (e: Exception) {
                errorCollector.logError(e, "SessionList")
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
                val children = api.getSessionChildren(server, sessionId, directory = directory)
                _childrenSessions.value = _childrenSessions.value.toMutableMap().apply {
                    put(sessionId, children)
                }
            } catch (_: Exception) { }
        }
    }

    fun getSessionChildren(sessionId: String): List<Session> {
        return _childrenSessions.value[sessionId] ?: emptyList()
    }

    fun deletePty(ptyId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.deletePty(server, ptyId, directory = directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "SessionList")
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * Compute total token usage for a session from its messages.
     * Uses the last assistant message with tokens (cumulative, same as Web).
     */
    private fun computeSessionTokens(messages: List<Message>?): Long {
        if (messages == null) return 0L
        val lastAssistantWithTokens = messages.lastOrNull {
            it.isAssistant && it.info.tokens != null && it.info.tokens!!.total > 0
        }
        val tokens = lastAssistantWithTokens?.info?.tokens ?: return 0L
        return tokens.input + tokens.output + tokens.reasoning + tokens.cache.read + tokens.cache.write
    }
}

/**
 * Like [kotlinx.coroutines.flow.debounce] but the very first emission passes
 * through immediately.  Only subsequent emissions are delayed by [timeout].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
private fun <T> Flow<T>.debounceAfterFirst(timeout: Duration): Flow<T> {
    var first = true
    return flatMapLatest { value ->
        if (first) {
            first = false
            flowOf(value)
        } else {
            flowOf(value).debounce(timeout)
        }
    }
}
