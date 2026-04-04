package me.xiaok.opencode.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.xiaok.opencode.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central state management (Redux / Event Sourcing pattern).
 * All SSE events flow through processEvent(), updating reactive StateFlows.
 *
 * SSE Event → EventReducer → StateFlow → ViewModel → Compose UI
 *
 * Error resilience:
 * - Out-of-order events: upsert — later event always wins
 * - Duplicate events: idempotent by design
 * - Unknown event type: log + skip
 * - Malformed event JSON: catch per-event, continue processing stream
 */
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
    /** Map of sessionId → last error message. Cleared when session status changes away from error. */
    val sessionErrors: StateFlow<Map<String, String>> = _sessionErrors.asStateFlow()

    // === Unread Tracking ===

    private val _unreadSessions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    /** Map of serverId → set of sessionIds with unread messages */
    val unreadSessions: StateFlow<Map<String, Set<String>>> = _unreadSessions.asStateFlow()

    /**
     * Compute unread sessions by comparing session.time.updated against persisted lastViewedAt.
     * Should be called after sessions are loaded or when a session is updated.
     */
    suspend fun computeUnreadSessions(serverId: String) {
        val viewLogs = cacheRepository.getSessionViewLogs(serverId)
        val serverSessionIds = _serverSessions.value[serverId] ?: emptySet()

        val unreadIds = serverSessionIds.filterNotNull().filter { sessionId ->
            val session = _sessions.value[sessionId] ?: return@filter false
            val updatedAt = session.time.updated
            if (updatedAt <= 0) return@filter false
            // Unread if session was never viewed, or updated after last view
            val lastViewed = viewLogs[sessionId]
            lastViewed == null || updatedAt > lastViewed
        }.toSet()

        _unreadSessions.value = _unreadSessions.value.toMutableMap().apply {
            put(serverId, unreadIds)
        }
    }

    /** Mark a session as currently viewed (clears unread for that session) */
    suspend fun markSessionViewed(serverId: String, sessionId: String) {
        cacheRepository.markSessionViewed(serverId, sessionId)
        // Optimistically clear unread for this session immediately
        val current = _unreadSessions.value[serverId] ?: emptySet()
        if (sessionId in current) {
            _unreadSessions.value = _unreadSessions.value.toMutableMap().apply {
                put(serverId, current - sessionId)
            }
        }
    }

    /** Clear viewed session when navigating away — no-op, persistence handles tracking */
    fun clearViewedSession(serverId: String) {
        // No-op: persistence handles actual tracking
    }

    // === Main Dispatch ===

    /**
     * Main dispatch — routes 24 event types to state updates.
     * @param serverId The server this event came from
     * @param event The deserialized SSE event
     */
    fun processEvent(serverId: String, event: SseEvent) {
        Log.d(TAG, "processEvent: server=$serverId, type=${event::class.simpleName}")
        try {
            when (event) {
                // Server events
                is SseEvent.ServerConnected -> onServerConnected(serverId)
                is SseEvent.ServerHeartbeat -> { /* No state change, just reset timeout */ }
                is SseEvent.ServerInstanceDisposed -> onServerInstanceDisposed(serverId)

                // Session events
                is SseEvent.SessionCreated -> onSessionCreated(serverId, event.session)
                is SseEvent.SessionUpdated -> onSessionUpdated(event.session)
                is SseEvent.SessionDeleted -> onSessionDeleted(serverId, event.session)
                is SseEvent.SessionStatusChanged -> onSessionStatus(event.sessionId, event.status)
                is SseEvent.SessionIdle -> onSessionIdle(event.sessionId)
                is SseEvent.SessionDiff -> onSessionDiff(event.sessionId, event.diffs)
                is SseEvent.SessionError -> onSessionError(event.sessionId, event.error)

                // Message events
                is SseEvent.MessageUpdated -> onMessageUpdated(event.message)
                is SseEvent.MessageRemoved -> onMessageRemoved(event.sessionId, event.messageId)
                is SseEvent.MessagePartUpdated -> onMessagePartUpdated(event.part)
                is SseEvent.MessagePartDelta -> onMessagePartDelta(
                    event.sessionId, event.messageId, event.partId, event.field, event.delta
                )
                is SseEvent.MessagePartRemoved -> onMessagePartRemoved(
                    event.sessionId, event.messageId, event.partId
                )

                // Interaction events
                is SseEvent.PermissionAsked -> onPermissionAsked(event.permission)
                is SseEvent.PermissionReplied -> onPermissionReplied(event.sessionId, event.requestId)
                is SseEvent.QuestionAsked -> onQuestionAsked(event.question)
                is SseEvent.QuestionReplied -> onQuestionReplied(event.sessionId, event.requestId)
                is SseEvent.QuestionRejected -> onQuestionRejected(event.sessionId, event.requestId)

                // Other events
                is SseEvent.TodoUpdated -> onTodoUpdated(event.sessionId, event.todos)
                is SseEvent.VcsBranchUpdated -> onVcsBranchUpdated(event.branch)
                is SseEvent.LspUpdated -> { /* Client-side, ignore */ }
                is SseEvent.ProjectUpdated -> onProjectUpdated(event.project)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event ${event::class.simpleName}", e)
        }

        // Forward event to cache for Room invalidation
        cacheRepository.onSseEvent(serverId, event)
    }

    // === Bulk Init Methods ===

    /** Bulk init sessions from REST API */
    fun setSessions(serverId: String, sessions: List<Session>) {
        val sessionMap = _sessions.value.toMutableMap()
        val sessionIdSet = mutableSetOf<String>()
        sessions.forEach { session ->
            sessionMap[session.id] = session
            sessionIdSet.add(session.id)
        }
        _sessions.value = sessionMap
        _serverSessions.value = _serverSessions.value.toMutableMap().apply {
            put(serverId, sessionIdSet)
        }
        _activeServers.value = _activeServers.value + serverId
        // Recompute unread based on persisted view logs
        scope.launch {
            computeUnreadSessions(serverId)
        }
    }

    /**
     * Bulk init messages from REST API.
     * Uses merge semantics instead of full overwrite to avoid race condition:
     * SSE may have already appended new messages between the REST request and response.
     * REST data is authoritative for messages it contains (upsert), but SSE-only messages are preserved.
     * After merging, messages are sorted by creation time to ensure correct ordering.
     */
    fun setMessages(sessionId: String, messages: List<Message>) {
        val current = _messages.value[sessionId] ?: emptyList()
        if (current.isEmpty()) {
            // No existing messages — safe to do full write
            _messages.value = _messages.value.toMutableMap().apply {
                put(sessionId, messages)
            }
        } else {
            // Merge: REST messages as base, preserve SSE-only messages not in REST response
            val restById = messages.associateBy { it.id }
            val sseOnly = current.filter { it.id !in restById }
            // Sort by creation time to ensure correct order regardless of source
            val merged = (messages + sseOnly).sortedBy { it.info.time.created }
            Log.d(TAG, "setMessages merge: sessionId=$sessionId, rest=${messages.size}, sseOnly=${sseOnly.size}, merged=${merged.size}")
            _messages.value = _messages.value.toMutableMap().apply {
                put(sessionId, merged)
            }
        }
    }

    /** Prepend older messages to the front of the list (for reverse pagination) */
    fun prependMessages(sessionId: String, olderMessages: List<Message>) {
        val current = _messages.value[sessionId] ?: emptyList()
        // Deduplicate: keep existing messages, only add ones with new IDs
        val existingIds = current.map { it.id }.toSet()
        val newMessages = olderMessages.filter { it.id !in existingIds }
        _messages.value = _messages.value.toMutableMap().apply {
            put(sessionId, newMessages + current)
        }
    }

    /** Bulk init parts for a message, merging with any existing SSE-accumulated parts */
    fun setParts(messageId: String, parts: List<Part>) {
        val existing = _parts.value[messageId] ?: emptyList()
        val merged = if (existing.isEmpty()) parts else mergeParts(existing, parts)
        _parts.value = _parts.value.toMutableMap().apply {
            put(messageId, merged)
        }
    }

    /** Cleanup on server disconnect */
    fun clearForServer(serverId: String) {
        val sessionIds = _serverSessions.value[serverId] ?: emptySet()

        // Remove sessions
        _sessions.value = _sessions.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }

        // Remove messages
        _messages.value = _messages.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }

        // Remove statuses
        _sessionStatuses.value = _sessionStatuses.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }

        // Remove permissions
        _permissions.value = _permissions.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }

        // Remove questions
        _questions.value = _questions.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }

        // Remove todos
        _todos.value = _todos.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }

        // Remove diffs
        _sessionDiffs.value = _sessionDiffs.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }

        // Remove server-sessions mapping
        _serverSessions.value = _serverSessions.value.toMutableMap().apply {
            remove(serverId)
        }

        // Remove unread state
        _unreadSessions.value = _unreadSessions.value.toMutableMap().apply {
            remove(serverId)
        }

        // Remove persisted view logs for this server
        scope.launch {
            cacheRepository.deleteSessionViewLogsForServer(serverId)
        }

        // Remove from active servers
        _activeServers.value = _activeServers.value - serverId
    }

    /** Full state reset */
    fun clearAll() {
        _activeServers.value = emptySet()
        _serverSessions.value = emptyMap()
        _sessions.value = emptyMap()
        _sessionStatuses.value = emptyMap()
        _messages.value = emptyMap()
        _parts.value = emptyMap()
        _sessionDiffs.value = emptyMap()
        _permissions.value = emptyMap()
        _questions.value = emptyMap()
        _todos.value = emptyMap()
        _vcsBranch.value = null
        _projectInfo.value = null
        _unreadSessions.value = emptyMap()
    }

    // === Optimistic Updates ===

    fun updateSessionStatus(sessionId: String, status: SessionStatus) {
        _sessionStatuses.value = _sessionStatuses.value.toMutableMap().apply {
            put(sessionId, status)
        }
    }

    fun removeQuestion(sessionId: String, requestId: String) {
        val current = _questions.value[sessionId] ?: return
        _questions.value = _questions.value.toMutableMap().apply {
            put(sessionId, current.filterNot { it.id == requestId })
        }
    }

    /**
     * Initialize questions for a session from REST API.
     * Only adds questions not already present (avoids duplicates from SSE).
     */
    fun setQuestions(sessionId: String, questions: List<QuestionRequest>) {
        val current = _questions.value[sessionId] ?: emptyList()
        val existingIds = current.map { it.id }.toSet()
        val newQuestions = questions.filterNot { it.id in existingIds }
        Log.d("EventReducer", "setQuestions: sessionId=$sessionId, current=${current.size}, incoming=${questions.size}, new=${newQuestions.size}")
        if (newQuestions.isEmpty()) return
        _questions.value = _questions.value.toMutableMap().apply {
            put(sessionId, current + newQuestions)
        }
    }

    fun removePermission(sessionId: String, requestId: String) {
        val current = _permissions.value[sessionId] ?: return
        _permissions.value = _permissions.value.toMutableMap().apply {
            put(sessionId, current.filterNot { it.id == requestId })
        }
    }

    // === Private Event Handlers: Server ===

    private fun onServerConnected(serverId: String) {
        _activeServers.value = _activeServers.value + serverId
    }

    private fun onServerInstanceDisposed(serverId: String) {
        clearForServer(serverId)
    }

    // === Private Event Handlers: Session ===

    private fun onSessionCreated(serverId: String, session: Session) {
        _sessions.value = _sessions.value.toMutableMap().apply {
            put(session.id, session)
        }
        _serverSessions.value = _serverSessions.value.toMutableMap().apply {
            put(serverId, (get(serverId) ?: emptySet()) + session.id)
        }
        _activeServers.value = _activeServers.value + serverId
        // Recompute unread — new session may have updated > lastViewedAt
        scope.launch {
            computeUnreadSessions(serverId)
        }
    }

    private fun onSessionUpdated(session: Session) {
        _sessions.value = _sessions.value.toMutableMap().apply {
            put(session.id, session)
        }
        // Recompute unread — session.time.updated may have changed
        val serverId = _serverSessions.value.entries.find { session.id in it.value }?.key
        if (serverId != null) {
            scope.launch {
                computeUnreadSessions(serverId)
            }
        }
    }

    private fun onSessionDeleted(serverId: String, session: Session) {
        _sessions.value = _sessions.value.toMutableMap().apply { remove(session.id) }
        _messages.value = _messages.value.toMutableMap().apply { remove(session.id) }
        _sessionStatuses.value = _sessionStatuses.value.toMutableMap().apply { remove(session.id) }
        _permissions.value = _permissions.value.toMutableMap().apply { remove(session.id) }
        _questions.value = _questions.value.toMutableMap().apply { remove(session.id) }
        _todos.value = _todos.value.toMutableMap().apply { remove(session.id) }
        _sessionDiffs.value = _sessionDiffs.value.toMutableMap().apply { remove(session.id) }
        _serverSessions.value = _serverSessions.value.toMutableMap().apply {
            put(serverId, (get(serverId) ?: emptySet()) - session.id)
        }
        _unreadSessions.value = _unreadSessions.value.toMutableMap().apply {
            val current = get(serverId) ?: emptySet()
            put(serverId, current - session.id)
        }
        // Clean up persisted view log
        scope.launch {
            cacheRepository.deleteSessionViewLog(serverId, session.id)
        }
    }

    private fun onSessionStatus(sessionId: String, status: SessionStatus) {
        Log.d(TAG, "onSessionStatus: sessionId=$sessionId, status=$status")
        _sessionStatuses.value = _sessionStatuses.value.toMutableMap().apply {
            put(sessionId, status)
        }
    }

    private fun onSessionIdle(sessionId: String) {
        _sessionStatuses.value = _sessionStatuses.value.toMutableMap().apply {
            put(sessionId, SessionStatus.IDLE)
        }
    }

    private fun onSessionDiff(sessionId: String, diffs: List<FileDiff>) {
        _sessionDiffs.value = _sessionDiffs.value.toMutableMap().apply {
            put(sessionId, diffs)
        }
    }

    private fun onSessionError(sessionId: String?, error: ErrorInfo?) {
        val errorMessage = error?.message ?: "Unknown error"
        Log.e(TAG, "Session error (session=${sessionId ?: "unknown"}): $errorMessage")
        // Store error in sessionErrors StateFlow for notification
        if (sessionId != null && errorMessage.isNotEmpty()) {
            _sessionErrors.value = _sessionErrors.value.toMutableMap().apply {
                put(sessionId, errorMessage)
            }
        }
    }

    // === Private Event Handlers: Message ===

    private fun onMessageUpdated(message: Message) {
        val sessionId = message.info.sessionID
        val current = _messages.value[sessionId] ?: emptyList()
        val index = current.indexOfFirst { it.info.id == message.info.id }
        val updated = if (index >= 0) {
            Log.d(TAG, "onMessageUpdated: UPDATE sessionId=$sessionId, msgId=${message.info.id}, role=${message.info.role}, parts=${message.parts.size}")
            current.toMutableList().apply { set(index, message) }
        } else {
            Log.d(TAG, "onMessageUpdated: APPEND sessionId=$sessionId, msgId=${message.info.id}, role=${message.info.role}, parts=${message.parts.size}")
            (current + message).sortedBy { it.info.time.created }
        }
        _messages.value = _messages.value.toMutableMap().apply {
            put(sessionId, updated)
        }

        // Sync inline parts from message to _parts StateFlow so UI can render them.
        // Merge with existing parts (SSE streaming may have accumulated content already).
        if (message.parts.isNotEmpty()) {
            val messageId = message.info.id
            val existingParts = _parts.value[messageId] ?: emptyList()
            val merged = mergeParts(existingParts, message.parts)
            _parts.value = _parts.value.toMutableMap().apply {
                put(messageId, merged)
            }
        }

        // Track unread: when an assistant message arrives, recompute unread
        // based on persisted lastViewedAt vs session.time.updated
        if (message.isAssistant) {
            val serverId = _serverSessions.value.entries.find { sessionId in it.value }?.key
            if (serverId != null) {
                scope.launch {
                    computeUnreadSessions(serverId)
                }
            }
        }
    }

    /**
     * Merge two part lists by ID. Preserves existing streaming content when possible.
     *
     * Strategy: For each part ID, if both existing and incoming have it:
     * - Text/Reasoning parts: keep whichever has MORE text (streaming accumulates, so longer = newer)
     * - All other parts: prefer incoming (it's the authoritative update from server)
     *
     * Parts only in existing are preserved (preserves streaming content).
     * Parts only in incoming are added.
     */
    private fun mergeParts(existing: List<Part>, incoming: List<Part>): List<Part> {
        val existingById = existing.associateBy { it.id }
        val result = mutableListOf<Part>()
        val seen = mutableSetOf<String>()

        // First pass: process incoming parts, preferring existing streaming content
        for (part in incoming) {
            val existingPart = existingById[part.id]
            if (existingPart != null) {
                // Merge: prefer the one with more content for streaming text
                val merged = when {
                    part is Part.Text && existingPart is Part.Text ->
                        if (existingPart.text.length >= part.text.length) existingPart else part
                    part is Part.Reasoning && existingPart is Part.Reasoning ->
                        if (existingPart.text.length >= part.text.length) existingPart else part
                    else -> part // Non-streaming parts: incoming is authoritative
                }
                result.add(merged)
            } else {
                result.add(part)
            }
            seen.add(part.id)
        }

        // Second pass: add existing parts not in incoming
        for (part in existing) {
            if (part.id !in seen) {
                result.add(part)
            }
        }
        return result
    }

    private fun onMessageRemoved(sessionId: String, messageId: String) {
        val current = _messages.value[sessionId] ?: return
        _messages.value = _messages.value.toMutableMap().apply {
            put(sessionId, current.filterNot { it.info.id == messageId })
        }
        _parts.value = _parts.value.toMutableMap().apply { remove(messageId) }
    }

    private fun onMessagePartUpdated(part: Part) {
        val messageId = part.messageId
        val current = _parts.value[messageId] ?: emptyList()
        val index = current.indexOfFirst { it.id == part.id }
        Log.d(TAG, "onMessagePartUpdated: msgId=$messageId, partId=${part.id}, type=${part::class.simpleName}, isUpdate=${index >= 0}")
        val updated = if (index >= 0) {
            current.toMutableList().apply { set(index, part) }
        } else {
            current + part
        }
        _parts.value = _parts.value.toMutableMap().apply {
            put(messageId, updated)
        }
    }

    private fun onMessagePartDelta(
        sessionId: String,
        messageId: String,
        partId: String,
        field: String,
        delta: String,
    ) {
        var current = _parts.value[messageId]
        if (current == null) {
            // Parts not initialized yet — create a stub part to accumulate deltas
            Log.d(TAG, "onMessagePartDelta: STUB msgId=$messageId, partId=$partId, field=$field, deltaLen=${delta.length}")
            val stub = when (field) {
                "text" -> Part.Text(id = partId, sessionId = sessionId, messageId = messageId, text = delta)
                else -> return // Unknown field, can't create stub
            }
            _parts.value = _parts.value.toMutableMap().apply {
                put(messageId, listOf(stub))
            }
            return
        }
        val updated = current.map { part ->
            if (part.id != partId) return@map part
            when {
                part is Part.Text && field == "text" -> part.copy(text = part.text + delta)
                part is Part.Reasoning && field == "text" -> part.copy(text = part.text + delta)
                else -> part
            }
        }
        _parts.value = _parts.value.toMutableMap().apply {
            put(messageId, updated)
        }
    }

    private fun onMessagePartRemoved(sessionId: String, messageId: String, partId: String) {
        val current = _parts.value[messageId] ?: return
        _parts.value = _parts.value.toMutableMap().apply {
            put(messageId, current.filterNot { it.id == partId })
        }
    }

    // === Private Event Handlers: Interaction ===

    private fun onPermissionAsked(permission: PermissionRequest) {
        val sessionId = permission.sessionID
        val current = _permissions.value[sessionId] ?: emptyList()
        _permissions.value = _permissions.value.toMutableMap().apply {
            put(sessionId, current + permission)
        }
    }

    private fun onPermissionReplied(sessionId: String, requestId: String) {
        removePermission(sessionId, requestId)
    }

    private fun onQuestionAsked(question: QuestionRequest) {
        val sessionId = question.sessionID
        Log.d("EventReducer", "onQuestionAsked: id=${question.id}, sessionID=$sessionId, questions=${question.questions.size}")
        val current = _questions.value[sessionId] ?: emptyList()
        _questions.value = _questions.value.toMutableMap().apply {
            put(sessionId, current + question)
        }
    }

    private fun onQuestionReplied(sessionId: String, requestId: String) {
        removeQuestion(sessionId, requestId)
    }

    private fun onQuestionRejected(sessionId: String, requestId: String) {
        removeQuestion(sessionId, requestId)
    }

    // === Private Event Handlers: Other ===

    private fun onTodoUpdated(sessionId: String, todoList: List<Todo>) {
        _todos.value = _todos.value.toMutableMap().apply {
            put(sessionId, todoList)
        }
    }

    private fun onVcsBranchUpdated(branch: String) {
        _vcsBranch.value = branch
    }

    private fun onProjectUpdated(project: Project) {
        _projectInfo.value = project
    }

    companion object {
        private const val TAG = "EventReducer"
    }
}
