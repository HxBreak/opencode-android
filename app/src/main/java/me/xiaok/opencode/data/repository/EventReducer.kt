package me.xiaok.opencode.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.xiaok.opencode.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventReducer @Inject constructor(
    private val cacheRepository: CacheRepository,
    private val scope: CoroutineScope,
) {

    // === StateFlows ===

    private val _activeServers = MutableStateFlow<Set<String>>(emptySet())
    val activeServers: StateFlow<Set<String>> = _activeServers.asStateFlow()

    private val _serverSessions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val serverSessions: StateFlow<Map<String, Set<String>>> = _serverSessions.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, Session>>(emptyMap())
    val sessions: StateFlow<Map<String, Session>> = _sessions.asStateFlow()

    private val _sessionStatuses = MutableStateFlow<Map<String, SessionStatus>>(emptyMap())
    val sessionStatuses: StateFlow<Map<String, SessionStatus>> = _sessionStatuses.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    private val _parts = MutableStateFlow<Map<String, List<Part>>>(emptyMap())
    val parts: StateFlow<Map<String, List<Part>>> = _parts.asStateFlow()

    private val _sessionDiffs = MutableStateFlow<Map<String, List<FileDiff>>>(emptyMap())
    val sessionDiffs: StateFlow<Map<String, List<FileDiff>>> = _sessionDiffs.asStateFlow()

    private val _permissions = MutableStateFlow<Map<String, List<PermissionRequest>>>(emptyMap())
    val permissions: StateFlow<Map<String, List<PermissionRequest>>> = _permissions.asStateFlow()

    private val _questions = MutableStateFlow<Map<String, List<QuestionRequest>>>(emptyMap())
    val questions: StateFlow<Map<String, List<QuestionRequest>>> = _questions.asStateFlow()

    private val _todos = MutableStateFlow<Map<String, List<Todo>>>(emptyMap())
    val todos: StateFlow<Map<String, List<Todo>>> = _todos.asStateFlow()

    private val _vcsBranch = MutableStateFlow<String?>(null)
    val vcsBranch: StateFlow<String?> = _vcsBranch.asStateFlow()

    private val _projectInfo = MutableStateFlow<Project?>(null)
    val projectInfo: StateFlow<Project?> = _projectInfo.asStateFlow()

    private val _sessionErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val sessionErrors: StateFlow<Map<String, String>> = _sessionErrors.asStateFlow()

    private val _ptySessions = MutableStateFlow<Map<String, Map<String, PtyInfo>>>(emptyMap())
    val ptySessions: StateFlow<Map<String, Map<String, PtyInfo>>> = _ptySessions.asStateFlow()

    private val _installationVersion = MutableStateFlow<String?>(null)
    val installationVersion: StateFlow<String?> = _installationVersion.asStateFlow()

    private val _installationUpdateAvailable = MutableStateFlow<String?>(null)
    val installationUpdateAvailable: StateFlow<String?> = _installationUpdateAvailable.asStateFlow()

    private val _mcpBrowserOpenFailed = MutableStateFlow<Pair<String, String>?>(null)
    val mcpBrowserOpenFailed: StateFlow<Pair<String, String>?> = _mcpBrowserOpenFailed.asStateFlow()

    private val _lastEditedFile = MutableStateFlow<String?>(null)
    val lastEditedFile: StateFlow<String?> = _lastEditedFile.asStateFlow()

    private val _unreadSessions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val unreadSessions: StateFlow<Map<String, Set<String>>> = _unreadSessions.asStateFlow()

    // === Internal Accessors (for sub-reducers) ===
    internal val messagesFlow get() = _messages
    internal val partsFlow get() = _parts
    internal val sessionsFlow get() = _sessions
    internal val serverSessionsFlow get() = _serverSessions
    internal val activeServersFlow get() = _activeServers
    internal val sessionStatusesFlow get() = _sessionStatuses
    internal val sessionErrorsFlow get() = _sessionErrors
    internal val sessionDiffsFlow get() = _sessionDiffs
    internal val unreadSessionsFlow get() = _unreadSessions
    internal val permissionsFlow get() = _permissions
    internal val questionsFlow get() = _questions
    internal val todosFlow get() = _todos
    internal val ptySessionsFlow get() = _ptySessions
    internal val scopeAccessor get() = scope
    internal val cacheRepositoryAccessor get() = cacheRepository

    // === Sub-Reducers ===
    internal val ptyReducer = PtyReducer(this)
    internal val interactionReducer = InteractionReducer(this)
    internal val sessionReducer = SessionReducer(this)
    internal val messageReducer = MessageReducer(this)

    // === Public Error Helper ===

    fun clearSessionError(sessionId: String) {
        _sessionErrors.value = _sessionErrors.value.toMutableMap().apply {
            remove(sessionId)
        }
    }

    // === Main Dispatch ===

    fun processEvent(serverId: String, event: SseEvent) {
        Log.d(TAG, "processEvent: server=$serverId, type=${event::class.simpleName}")
        try {
            when (event) {
                is SseEvent.ServerConnected -> onServerConnected(serverId)
                is SseEvent.ServerHeartbeat -> { }
                is SseEvent.ServerInstanceDisposed -> onServerInstanceDisposed(serverId)

                is SseEvent.SessionCreated -> sessionReducer.onSessionCreated(serverId, event.session)
                is SseEvent.SessionUpdated -> sessionReducer.onSessionUpdated(event.session)
                is SseEvent.SessionDeleted -> sessionReducer.onSessionDeleted(serverId, event.session)
                is SseEvent.SessionStatusChanged -> sessionReducer.onSessionStatus(event.sessionId, event.status)
                is SseEvent.SessionIdle -> sessionReducer.onSessionIdle(event.sessionId)
                is SseEvent.SessionDiff -> sessionReducer.onSessionDiff(event.sessionId, event.diffs)
                is SseEvent.SessionError -> sessionReducer.onSessionError(event.sessionId, event.error)

                is SseEvent.MessageUpdated -> messageReducer.onMessageUpdated(event.message)
                is SseEvent.MessageRemoved -> messageReducer.onMessageRemoved(event.sessionId, event.messageId)
                is SseEvent.MessagePartUpdated -> messageReducer.onMessagePartUpdated(event.part)
                is SseEvent.MessagePartDelta -> messageReducer.onMessagePartDelta(
                    event.sessionId, event.messageId, event.partId, event.field, event.delta
                )
                is SseEvent.MessagePartRemoved -> messageReducer.onMessagePartRemoved(
                    event.sessionId, event.messageId, event.partId
                )

                is SseEvent.PermissionAsked -> interactionReducer.onPermissionAsked(event.permission)
                is SseEvent.PermissionReplied -> interactionReducer.onPermissionReplied(event.sessionId, event.requestId)
                is SseEvent.QuestionAsked -> interactionReducer.onQuestionAsked(event.question)
                is SseEvent.QuestionReplied -> interactionReducer.onQuestionReplied(event.sessionId, event.requestId)
                is SseEvent.QuestionRejected -> interactionReducer.onQuestionRejected(event.sessionId, event.requestId)

                is SseEvent.TodoUpdated -> onTodoUpdated(event.sessionId, event.todos)
                is SseEvent.VcsBranchUpdated -> onVcsBranchUpdated(event.branch)
                is SseEvent.LspUpdated -> { }
                is SseEvent.ProjectUpdated -> onProjectUpdated(event.project)

                is SseEvent.PtyCreated -> ptyReducer.onPtyCreated(serverId, event.info)
                is SseEvent.PtyUpdated -> ptyReducer.onPtyUpdated(serverId, event.info)
                is SseEvent.PtyExited -> ptyReducer.onPtyExited(serverId, event.id, event.exitCode)
                is SseEvent.PtyDeleted -> ptyReducer.onPtyDeleted(serverId, event.id)

                is SseEvent.McpBrowserOpenFailed -> onMcpBrowserOpenFailed(event.mcpName, event.url)
                is SseEvent.McpToolsChanged -> onMcpToolsChanged(event.server)
                is SseEvent.FileEdited -> onFileEdited(event.file)
                is SseEvent.InstallationUpdated -> onInstallationUpdated(event.version)
                is SseEvent.InstallationUpdateAvailable -> onInstallationUpdateAvailable(event.version)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event ${event::class.simpleName}", e)
        }
        cacheRepository.onSseEvent(serverId, event)
    }

    // === Cleanup ===

    fun clearForServer(serverId: String) {
        val sessionIds = _serverSessions.value[serverId] ?: emptySet()
        messageReducer.clearForServer(sessionIds)
        sessionReducer.clearForServer(serverId)
        interactionReducer.clearForServer(sessionIds)
        ptyReducer.clearForServer(serverId)
        _todos.value = _todos.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }
    }

    fun clearAll() {
        messageReducer.clearAll()
        sessionReducer.clearAll()
        interactionReducer.clearAll()
        ptyReducer.clearAll()
        _todos.value = emptyMap()
        _vcsBranch.value = null
        _projectInfo.value = null
        _installationVersion.value = null
        _installationUpdateAvailable.value = null
        _mcpBrowserOpenFailed.value = null
        _lastEditedFile.value = null
    }

    // === Bulk Init Proxies ===

    fun setSessions(serverId: String, sessions: List<Session>) = sessionReducer.setSessions(serverId, sessions)
    fun setMessages(sessionId: String, messages: List<Message>) = messageReducer.setMessages(sessionId, messages)
    fun prependMessages(sessionId: String, olderMessages: List<Message>) = messageReducer.prependMessages(sessionId, olderMessages)
    fun setParts(messageId: String, parts: List<Part>) = messageReducer.setParts(messageId, parts)
    fun setPtys(serverId: String, ptys: List<PtyInfo>) = ptyReducer.setPtys(serverId, ptys)

    // === Optimistic Update Proxies ===

    fun updateSessionStatus(sessionId: String, status: SessionStatus) {
        _sessionStatuses.value = _sessionStatuses.value.toMutableMap().apply {
            put(sessionId, status)
        }
    }

    fun removeQuestion(sessionId: String, requestId: String) = interactionReducer.removeQuestion(sessionId, requestId)
    fun setQuestions(sessionId: String, questions: List<QuestionRequest>) = interactionReducer.setQuestions(sessionId, questions)
    fun removePermission(sessionId: String, requestId: String) = interactionReducer.removePermission(sessionId, requestId)
    fun updateTodos(sessionId: String, todos: List<Todo>) {
        _todos.value = _todos.value.toMutableMap().apply { put(sessionId, todos) }
    }

    // === Unread Tracking Proxies ===

    suspend fun markSessionViewed(serverId: String, sessionId: String) = sessionReducer.markSessionViewed(serverId, sessionId)
    suspend fun computeUnreadSessions(serverId: String) = sessionReducer.computeUnreadSessions(serverId)
    fun clearViewedSession(serverId: String) { }

    // === Small Handlers (kept here) ===

    private fun onServerConnected(serverId: String) {
        _activeServers.value = _activeServers.value + serverId
    }

    private fun onServerInstanceDisposed(serverId: String) {
        clearForServer(serverId)
    }

    private fun onTodoUpdated(sessionId: String, todoList: List<Todo>) {
        _todos.value = _todos.value.toMutableMap().apply { put(sessionId, todoList) }
    }

    private fun onVcsBranchUpdated(branch: String) { _vcsBranch.value = branch }
    private fun onProjectUpdated(project: Project) { _projectInfo.value = project }

    private fun onMcpBrowserOpenFailed(mcpName: String, url: String) {
        Log.d(TAG, "onMcpBrowserOpenFailed: mcpName=$mcpName, url=$url")
        _mcpBrowserOpenFailed.value = mcpName to url
    }

    private fun onMcpToolsChanged(server: String) {
        Log.d(TAG, "onMcpToolsChanged: server=$server — tools list for MCP server changed, UI should refresh")
    }

    private fun onFileEdited(file: String) {
        Log.d(TAG, "onFileEdited: file=$file")
        _lastEditedFile.value = file
    }

    private fun onInstallationUpdated(version: String) {
        Log.d(TAG, "onInstallationUpdated: version=$version")
        _installationVersion.value = version
    }

    private fun onInstallationUpdateAvailable(version: String) {
        Log.d(TAG, "onInstallationUpdateAvailable: version=$version")
        _installationUpdateAvailable.value = version
    }

    companion object {
        private const val TAG = "EventReducer"
    }
}
