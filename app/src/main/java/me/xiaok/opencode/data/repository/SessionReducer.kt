package me.xiaok.opencode.data.repository

import android.util.Log
import kotlinx.coroutines.launch
import me.xiaok.opencode.domain.model.*

class SessionReducer internal constructor(
    private val host: EventReducer,
) {
    private val scope get() = host.scopeAccessor
    private val cacheRepository get() = host.cacheRepositoryAccessor

    fun onSessionCreated(serverId: String, session: Session) {
        host.sessionsFlow.value = host.sessionsFlow.value.toMutableMap().apply {
            put(session.id, session)
        }
        host.serverSessionsFlow.value = host.serverSessionsFlow.value.toMutableMap().apply {
            put(serverId, (get(serverId) ?: emptySet()) + session.id)
        }
        host.activeServersFlow.value = host.activeServersFlow.value + serverId
        scope.launch {
            computeUnreadSessions(serverId)
        }
    }

    fun onSessionUpdated(session: Session) {
        host.sessionsFlow.value = host.sessionsFlow.value.toMutableMap().apply {
            put(session.id, session)
        }
        val serverId = host.serverSessionsFlow.value.entries.find { session.id in it.value }?.key
        if (serverId != null) {
            scope.launch {
                computeUnreadSessions(serverId)
            }
        }
    }

    fun onSessionDeleted(serverId: String, session: Session) {
        host.sessionsFlow.value = host.sessionsFlow.value.toMutableMap().apply { remove(session.id) }
        host.messagesFlow.value = host.messagesFlow.value.toMutableMap().apply { remove(session.id) }
        host.sessionStatusesFlow.value = host.sessionStatusesFlow.value.toMutableMap().apply { remove(session.id) }
        host.permissionsFlow.value = host.permissionsFlow.value.toMutableMap().apply { remove(session.id) }
        host.questionsFlow.value = host.questionsFlow.value.toMutableMap().apply { remove(session.id) }
        host.todosFlow.value = host.todosFlow.value.toMutableMap().apply { remove(session.id) }
        host.sessionDiffsFlow.value = host.sessionDiffsFlow.value.toMutableMap().apply { remove(session.id) }
        host.serverSessionsFlow.value = host.serverSessionsFlow.value.toMutableMap().apply {
            put(serverId, (get(serverId) ?: emptySet()) - session.id)
        }
        host.unreadSessionsFlow.value = host.unreadSessionsFlow.value.toMutableMap().apply {
            val current = get(serverId) ?: emptySet()
            put(serverId, current - session.id)
        }
        scope.launch {
            cacheRepository.deleteSessionViewLog(serverId, session.id)
        }
    }

    fun onSessionStatus(sessionId: String, status: SessionStatus) {
        Log.d(TAG, "onSessionStatus: sessionId=$sessionId, status=$status")
        host.sessionStatusesFlow.value = host.sessionStatusesFlow.value.toMutableMap().apply {
            put(sessionId, status)
        }
    }

    fun onSessionIdle(sessionId: String) {
        host.sessionStatusesFlow.value = host.sessionStatusesFlow.value.toMutableMap().apply {
            put(sessionId, SessionStatus.Idle)
        }
    }

    fun onSessionDiff(sessionId: String, diffs: List<FileDiff>) {
        host.sessionDiffsFlow.value = host.sessionDiffsFlow.value.toMutableMap().apply {
            put(sessionId, diffs)
        }
    }

    fun onSessionError(sessionId: String?, error: ErrorInfo?) {
        val errorMessage = error?.message ?: "Unknown error"
        Log.e(TAG, "Session error (session=${sessionId ?: "unknown"}): $errorMessage")
        if (sessionId != null && errorMessage.isNotEmpty()) {
            host.sessionErrorsFlow.value = host.sessionErrorsFlow.value.toMutableMap().apply {
                put(sessionId, errorMessage)
            }
        }
    }

    fun setSessions(serverId: String, sessions: List<Session>) {
        val sessionMap = host.sessionsFlow.value.toMutableMap()
        val sessionIdSet = mutableSetOf<String>()
        sessions.forEach { session ->
            sessionMap[session.id] = session
            sessionIdSet.add(session.id)
        }
        host.sessionsFlow.value = sessionMap
        host.serverSessionsFlow.value = host.serverSessionsFlow.value.toMutableMap().apply {
            put(serverId, sessionIdSet)
        }
        host.activeServersFlow.value = host.activeServersFlow.value + serverId
        scope.launch {
            computeUnreadSessions(serverId)
        }
    }

    suspend fun computeUnreadSessions(serverId: String) {
        val viewLogs = cacheRepository.getSessionViewLogs(serverId)
        val serverSessionIds = host.serverSessionsFlow.value[serverId] ?: emptySet()
        val unreadIds = serverSessionIds.filterNotNull().filter { sessionId ->
            val session = host.sessionsFlow.value[sessionId] ?: return@filter false
            val updatedAt = session.time.updated
            if (updatedAt <= 0) return@filter false
            val lastViewed = viewLogs[sessionId]
            lastViewed == null || updatedAt > lastViewed
        }.toSet()
        host.unreadSessionsFlow.value = host.unreadSessionsFlow.value.toMutableMap().apply {
            put(serverId, unreadIds)
        }
    }

    suspend fun markSessionViewed(serverId: String, sessionId: String) {
        cacheRepository.markSessionViewed(serverId, sessionId)
        val current = host.unreadSessionsFlow.value[serverId] ?: emptySet()
        if (sessionId in current) {
            host.unreadSessionsFlow.value = host.unreadSessionsFlow.value.toMutableMap().apply {
                put(serverId, current - sessionId)
            }
        }
    }

    fun clearForServer(serverId: String) {
        val sessionIds = host.serverSessionsFlow.value[serverId] ?: emptySet()
        val sessions = host.sessionsFlow.value.toMutableMap()
        val statuses = host.sessionStatusesFlow.value.toMutableMap()
        val diffs = host.sessionDiffsFlow.value.toMutableMap()
        val errs = host.sessionErrorsFlow.value.toMutableMap()
        val unread = host.unreadSessionsFlow.value.toMutableMap()
        sessionIds.forEach { sid ->
            sessions.remove(sid)
            statuses.remove(sid)
            diffs.remove(sid)
            errs.remove(sid)
        }
        host.sessionsFlow.value = sessions
        host.sessionStatusesFlow.value = statuses
        host.sessionDiffsFlow.value = diffs
        host.sessionErrorsFlow.value = errs

        host.serverSessionsFlow.value = host.serverSessionsFlow.value.toMutableMap().apply { remove(serverId) }
        host.activeServersFlow.value = host.activeServersFlow.value - serverId

        unread.remove(serverId)
        host.unreadSessionsFlow.value = unread

        scope.launch {
            cacheRepository.deleteSessionViewLogsForServer(serverId)
        }
    }

    fun clearAll() {
        host.sessionsFlow.value = emptyMap()
        host.serverSessionsFlow.value = emptyMap()
        host.activeServersFlow.value = emptySet()
        host.sessionStatusesFlow.value = emptyMap()
        host.sessionDiffsFlow.value = emptyMap()
        host.sessionErrorsFlow.value = emptyMap()
        host.unreadSessionsFlow.value = emptyMap()
    }

    companion object {
        private const val TAG = "SessionReducer"
    }
}
