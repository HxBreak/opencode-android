package me.xiaok.opencode.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.xiaok.opencode.data.local.db.dao.MessageDao
import me.xiaok.opencode.data.local.db.dao.SessionDao
import me.xiaok.opencode.data.local.db.dao.SessionViewLogDao
import me.xiaok.opencode.data.local.db.entity.MessageEntity
import me.xiaok.opencode.data.local.db.entity.SessionEntity
import me.xiaok.opencode.data.local.db.entity.SessionViewLog
import me.xiaok.opencode.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Room DAOs for cache coordination.
 *
 * Supports two patterns:
 *
 * **API-first (existing):**
 * 1. GET from API → display
 * 2. Write to Room cache
 *
 * **Stale-while-revalidate (new):**
 * 1. Read Room cache → instant display
 * 2. GET from API in background → refresh cache
 * 3. UI collects Room Flow → auto-updates when cache refreshes
 *
 * **SSE cache invalidation:**
 * SSE events (session.updated, message.updated, etc.) are forwarded here
 * to keep Room cache in sync with real-time changes.
 */
@Singleton
class CacheRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val sessionViewLogDao: SessionViewLogDao,
) {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // === Session Cache ===

    fun getCachedSessions(serverId: String): Flow<List<SessionEntity>> {
        return sessionDao.getSessionsForServer(serverId)
    }

    /** Cross-server recent sessions for home screen quick access. */
    fun getRecentSessions(limit: Int = 7): Flow<List<SessionEntity>> {
        return sessionDao.getRecentSessions(limit)
    }

    suspend fun syncSessions(serverId: String, sessions: List<Session>) = withContext(Dispatchers.IO) {
        val entities = sessions.map { it.toEntity(serverId) }
        sessionDao.upsertAll(entities)
    }

    suspend fun upsertSession(serverId: String, session: Session) = withContext(Dispatchers.IO) {
        sessionDao.upsert(session.toEntity(serverId))
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.delete(sessionId)
    }

    suspend fun deleteSessionsForServer(serverId: String) = withContext(Dispatchers.IO) {
        sessionDao.deleteForServer(serverId)
    }

    // === Message Cache ===

    fun getCachedMessages(sessionId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForSession(sessionId)
    }

    suspend fun syncMessages(serverId: String, sessionId: String, messages: List<Message>) =
        withContext(Dispatchers.IO) {
            val entities = messages.map { it.toEntity(serverId) }
            messageDao.upsertAll(entities)
        }

    suspend fun upsertMessage(serverId: String, message: Message) = withContext(Dispatchers.IO) {
        messageDao.upsert(message.toEntity(serverId))
    }

    suspend fun deleteMessagesForSession(sessionId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteForSession(sessionId)
    }

    suspend fun deleteMessagesForServer(serverId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteForServer(serverId)
    }

    // === Stale-While-Revalidate ===

    /**
     * Get cached sessions as domain models for immediate display.
     * Returns empty list if no cache exists.
     */
    suspend fun getCachedSessionsAsModels(serverId: String): List<Session> = withContext(Dispatchers.IO) {
        try {
            sessionDao.getSessionsForServer(serverId).first().mapNotNull { it.toSession() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached sessions for server $serverId", e)
            emptyList()
        }
    }

    /**
     * Get cached messages as domain models for immediate display.
     * Returns empty list if no cache exists.
     */
    suspend fun getCachedMessagesAsModels(sessionId: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            messageDao.getMessagesForSession(sessionId).first().mapNotNull { it.toMessage() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached messages for session $sessionId", e)
            emptyList()
        }
    }

    /**
     * Stale-while-revalidate for sessions: return cached data immediately,
     * then fetch fresh data from API and update cache in background.
     *
     * @param serverId The server whose sessions to fetch
     * @param api The API client
     * @param server The server connection
     * @return Cached sessions for immediate display (empty if no cache)
     */
    suspend fun getSessionsWithRefresh(
        serverId: String,
        api: me.xiaok.opencode.data.api.OpenCodeApi,
        server: ServerConnection,
        directory: String? = null,
    ): List<Session> {
        // 1. Return cached data immediately
        val cached = getCachedSessionsAsModels(serverId)

        // 2. Fire background refresh
        scope.launch {
            try {
                val fresh = api.listSessions(server, directory = directory, roots = true)
                syncSessions(serverId, fresh)
            } catch (e: Exception) {
                Log.w(TAG, "Background session refresh failed for server $serverId", e)
            }
        }

        return cached
    }

    /**
     * Stale-while-revalidate for messages: return cached data immediately,
     * then fetch fresh data from API and update cache in background.
     *
     * @param serverId The server ID
     * @param sessionId The session whose messages to fetch
     * @param api The API client
     * @param server The server connection
     * @param limit Max messages to fetch
     * @return Cached messages for immediate display (empty if no cache)
     */
    suspend fun getMessagesWithRefresh(
        serverId: String,
        sessionId: String,
        api: me.xiaok.opencode.data.api.OpenCodeApi,
        server: ServerConnection,
        limit: Int? = null,
        directory: String? = null,
    ): List<Message> {
        // 1. Return cached data immediately
        val cached = getCachedMessagesAsModels(sessionId)

        // 2. Fire background refresh
        scope.launch {
            try {
                val fresh = api.listMessages(server, sessionId, limit = limit, directory = directory)
                syncMessages(serverId, sessionId, fresh.messages)
            } catch (e: Exception) {
                Log.w(TAG, "Background message refresh failed for session $sessionId", e)
            }
        }

        return cached
    }

    // === SSE Cache Invalidation ===

    /**
     * Handle SSE event to invalidate/update Room cache.
     * Called from EventReducer or SSE processing pipeline.
     */
    fun onSseEvent(serverId: String, event: SseEvent) {
        scope.launch {
            try {
                when (event) {
                    is SseEvent.SessionCreated -> {
                        upsertSession(serverId, event.session)
                    }
                    is SseEvent.SessionUpdated -> {
                        // Find which server owns this session and update
                        upsertSession(serverId, event.session)
                    }
                    is SseEvent.SessionDeleted -> {
                        deleteSession(event.session.id)
                        deleteMessagesForSession(event.session.id)
                    }
                    is SseEvent.MessageUpdated -> {
                        upsertMessage(serverId, event.message)
                    }
                    is SseEvent.MessageRemoved -> {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                messageDao.delete(event.messageId)
                            }
                        }
                    }
                    is SseEvent.ServerInstanceDisposed -> {
                        deleteSessionsForServer(serverId)
                        deleteMessagesForServer(serverId)
                    }
                    else -> {
                        // Other events don't directly affect cache
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to invalidate cache for event ${event::class.simpleName}", e)
            }
        }
    }

    // === Session View Log (Unread Tracking) ===

    /**
     * Mark a session as viewed at the current time.
     * Used to determine unread state: session.time.updated > lastViewedAt → unread.
     */
    suspend fun markSessionViewed(serverId: String, sessionId: String) = withContext(Dispatchers.IO) {
        sessionViewLogDao.upsert(
            SessionViewLog(
                serverId = serverId,
                sessionId = sessionId,
                lastViewedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Get all view logs for a server, returned as a map of sessionId → lastViewedAt.
     */
    suspend fun getSessionViewLogs(serverId: String): Map<String, Long> = withContext(Dispatchers.IO) {
        val logs = sessionViewLogDao.getAllForServer(serverId)
        logs.associate { it.sessionId to it.lastViewedAt }
    }

    /**
     * Remove view log for a deleted session.
     */
    suspend fun deleteSessionViewLog(serverId: String, sessionId: String) = withContext(Dispatchers.IO) {
        sessionViewLogDao.delete(serverId, sessionId)
    }

    /**
     * Remove all view logs for a disconnected server.
     */
    suspend fun deleteSessionViewLogsForServer(serverId: String) = withContext(Dispatchers.IO) {
        sessionViewLogDao.deleteForServer(serverId)
    }

    // === Mappers ===

    // === Bulk Clear ===

    /**
     * Delete all cached sessions, messages, and view logs from Room.
     * Used by the "Clear cache" setting to free up storage.
     */
    suspend fun clearAllCacheData() = withContext(Dispatchers.IO) {
        sessionDao.deleteAll()
        messageDao.deleteAll()
        sessionViewLogDao.deleteAll()
    }

    private fun Session.toEntity(serverId: String): SessionEntity {
        return SessionEntity(
            id = id,
            serverId = serverId,
            slug = slug,
            projectID = projectID,
            workspaceID = workspaceID,
            directory = directory,
            parentID = parentID,
            title = title,
            version = version,
            summaryJson = summary?.let { json.encodeToString(SessionSummary.serializer(), it) },
            shareJson = share?.let { json.encodeToString(SessionShare.serializer(), it) },
            permissionJson = if (permission.isNotEmpty()) json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(PermissionRule.serializer()),
                permission
            ) else null,
            revertJson = revert?.let { json.encodeToString(RevertInfo.serializer(), it) },
            timeJson = json.encodeToString(SessionTime.serializer(), time),
            updatedAt = time.updated,
        )
    }

    private fun Message.toEntity(serverId: String): MessageEntity {
        return MessageEntity(
            id = id,
            sessionId = sessionId,
            serverId = serverId,
            role = role,
            messageJson = json.encodeToString(Message.serializer(), this),
            createdAt = time.created,
            updatedAt = time.updated,
        )
    }

    private fun SessionEntity.toSession(): Session? {
        return try {
            val time = json.decodeFromString(SessionTime.serializer(), timeJson)
            val summary = summaryJson?.let { json.decodeFromString(SessionSummary.serializer(), it) }
            val share = shareJson?.let { json.decodeFromString(SessionShare.serializer(), it) }
            val permission = permissionJson?.let {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(PermissionRule.serializer()),
                    it
                )
            } ?: emptyList()
            val revert = revertJson?.let { json.decodeFromString(RevertInfo.serializer(), it) }

            Session(
                id = id,
                slug = slug,
                projectID = projectID,
                workspaceID = workspaceID,
                directory = directory,
                parentID = parentID,
                title = title,
                version = version,
                summary = summary,
                share = share,
                permission = permission,
                revert = revert,
                time = time,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize session $id", e)
            null
        }
    }

    private fun MessageEntity.toMessage(): Message? {
        return try {
            json.decodeFromString(Message.serializer(), messageJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize message $id", e)
            null
        }
    }

    companion object {
        private const val TAG = "CacheRepository"
    }
}
