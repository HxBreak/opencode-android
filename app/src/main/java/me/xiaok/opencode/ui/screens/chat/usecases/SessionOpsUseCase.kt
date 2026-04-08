package me.xiaok.opencode.ui.screens.chat.usecases

import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

class SessionOpsUseCase @Inject constructor(
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val errorCollector: ErrorCollector,
) {
    suspend fun forkSession(
        serverId: String,
        sessionId: String,
        messageId: String,
        sessionDirectory: String?,
    ): String {
        val server = serverRepository.getServer(serverId) ?: throw IllegalStateException("Server not found")
        val forked = api.forkSession(server, sessionId, messageId, directory = sessionDirectory)
        return forked.id
    }

    suspend fun shareSession(
        serverId: String,
        sessionId: String,
        sessionDirectory: String?,
    ): String {
        val server = serverRepository.getServer(serverId) ?: throw IllegalStateException("Server not found")
        val share = api.shareSession(server, sessionId, directory = sessionDirectory)
        return share.url
    }

    suspend fun unshareSession(
        serverId: String,
        sessionId: String,
        sessionDirectory: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        api.unshareSession(server, sessionId, directory = sessionDirectory)
        val updated = api.getSession(server, sessionId, directory = sessionDirectory)
        eventReducer.processEvent(
            serverId,
            SseEvent.SessionUpdated(updated),
        )
    }

    suspend fun revertSession(
        serverId: String,
        sessionId: String,
        messageId: String,
        sessionDirectory: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        api.revertSession(server, sessionId, messageId, directory = sessionDirectory)
    }

    suspend fun unrevertSession(
        serverId: String,
        sessionId: String,
        sessionDirectory: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        api.unrevertSession(server, sessionId, directory = sessionDirectory)
    }

    suspend fun summarizeSession(
        serverId: String,
        sessionId: String,
        selectedModel: ModelRef?,
        sessionDirectory: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        api.summarizeSession(
            conn = server,
            sessionId = sessionId,
            providerId = selectedModel?.providerID?.ifBlank { null },
            modelId = selectedModel?.modelID?.ifBlank { null },
            directory = sessionDirectory,
        )
    }

    suspend fun renameSession(
        serverId: String,
        sessionId: String,
        newTitle: String,
        sessionDirectory: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        api.updateSession(server, sessionId, title = newTitle, directory = sessionDirectory)
    }

    suspend fun deleteSession(
        serverId: String,
        sessionId: String,
        sessionDirectory: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        val deleted = api.deleteSession(server, sessionId, directory = sessionDirectory)
        if (deleted) {
            val session = eventReducer.sessions.value[sessionId] ?: return
            eventReducer.processEvent(serverId, SseEvent.SessionDeleted(session))
        }
    }

    suspend fun exportSession(
        serverId: String,
        sessionId: String,
    ): String {
        val server = serverRepository.getServer(serverId) ?: throw IllegalStateException("Server not found")
        val session = eventReducer.sessions.value[sessionId] ?: throw IllegalStateException("Session not found")
        val messages = eventReducer.messages.value[sessionId] ?: emptyList()

        val sb = StringBuilder()
        sb.appendLine("# ${session.title.ifEmpty { "Chat" }}")
        sb.appendLine()

        for (message in messages) {
            val role = if (message.isUser) "## User" else "## Assistant"
            sb.appendLine(role)
            sb.appendLine()
            val textParts = message.parts.filterIsInstance<Part.Text>()
            for (part in textParts) {
                if (part.text.isNotEmpty()) {
                    sb.appendLine(part.text)
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }

    suspend fun abortSession(
        serverId: String,
        sessionId: String,
        sessionDirectory: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        api.abortSession(server, sessionId, directory = sessionDirectory)
    }

    suspend fun updateSession(
        serverId: String,
        sessionId: String,
        sessionDirectory: String?,
        title: String? = null,
        archived: Long? = null,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        api.updateSession(server, sessionId, title = title, archived = archived, directory = sessionDirectory)
    }

    suspend fun refreshSessionDiffs(
        serverId: String,
        sessionId: String,
        sessionDirectory: String?,
        sessionWorkspace: String?,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        val diffs = api.getSessionDiff(server, sessionId, directory = sessionDirectory, workspace = sessionWorkspace)
        eventReducer.processEvent(serverId, SseEvent.SessionDiff(sessionId, diffs))
    }

    fun dismissDiffs(
        serverId: String,
        sessionId: String,
    ) {
        eventReducer.processEvent(serverId, SseEvent.SessionDiff(sessionId, emptyList()))
    }

    companion object {
        private const val TAG = "SessionOpsUseCase"
    }
}
