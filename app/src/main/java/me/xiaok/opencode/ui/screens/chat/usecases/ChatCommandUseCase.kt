package me.xiaok.opencode.ui.screens.chat.usecases

import kotlinx.coroutines.flow.first
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.BuiltInCommand
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

class ChatCommandUseCase @Inject constructor(
    private val eventReducer: EventReducer,
    private val settingsRepository: SettingsRepository,
    private val sessionOpsUseCase: SessionOpsUseCase,
    private val modelSelectionUseCase: ModelSelectionUseCase,
    private val errorCollector: ErrorCollector,
) {
    sealed class CommandResult {
        data object Handled : CommandResult()
        data object NotHandled : CommandResult()
        data class Error(val message: String) : CommandResult()
    }

    /**
     * Execute a built-in command.
     * @return CommandResult.Handled if command was processed locally,
     *         CommandResult.NotHandled if it's a navigation command (caller should handle),
     *         CommandResult.Error if execution failed.
     */
    suspend fun execute(command: BuiltInCommand, serverId: String, sessionId: String): CommandResult {
        return when (command.id) {
            "undo" -> undoLastTurn(serverId, sessionId)
            "redo" -> redoLastTurn(serverId, sessionId)
            "compact" -> { summarizeSession(serverId, sessionId); CommandResult.Handled }
            "share" -> { sessionOpsUseCase.shareSession(serverId, sessionId, getDirectory(sessionId)); CommandResult.Handled }
            "unshare" -> { sessionOpsUseCase.unshareSession(serverId, sessionId, getDirectory(sessionId)); CommandResult.Handled }
            "fork" -> forkFromLatestMessage(serverId, sessionId)
            "archive" -> archiveCurrentSession(serverId, sessionId)
            "variant" -> { cycleVariant(serverId); CommandResult.Handled }
            "theme" -> { cycleTheme(); CommandResult.Handled }
            "new", "sessions", "terminal", "files", "settings", "mcp",
            "model", "agent" -> CommandResult.NotHandled
            else -> CommandResult.NotHandled
        }
    }

    private fun getDirectory(sessionId: String): String? {
        return eventReducer.sessions.value[sessionId]?.directory
    }

    private suspend fun undoLastTurn(serverId: String, sessionId: String): CommandResult {
        val session = eventReducer.sessions.value[sessionId] ?: return CommandResult.Error("Session not found")
        val messages = eventReducer.messages.value[sessionId] ?: emptyList()
        val directory = session.directory

        if (eventReducer.sessionStatuses.value[sessionId] != SessionStatus.IDLE) {
            try {
                sessionOpsUseCase.abortSession(serverId, sessionId, directory)
            } catch (_: Exception) {}
        }

        val lastUserMessage = messages.lastOrNull { it.isUser } ?: return CommandResult.Error("No user message to undo")

        return try {
            sessionOpsUseCase.revertSession(serverId, sessionId, lastUserMessage.id, directory)
            CommandResult.Handled
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            CommandResult.Error(e.message ?: "Failed to undo")
        }
    }

    private suspend fun redoLastTurn(serverId: String, sessionId: String): CommandResult {
        val session = eventReducer.sessions.value[sessionId] ?: return CommandResult.Error("Session not found")
        val revertInfo = session.revert ?: return CommandResult.Error("Nothing to redo")
        val messages = eventReducer.messages.value[sessionId] ?: emptyList()
        val directory = session.directory

        return try {
            val reverting = revertInfo.messageID
            val afterRevert = messages.dropWhile { it.id != reverting }.drop(1)
            val nextUserMsg = afterRevert.firstOrNull { it.isUser }

            if (nextUserMsg != null) {
                sessionOpsUseCase.revertSession(serverId, sessionId, nextUserMsg.id, directory)
            } else {
                sessionOpsUseCase.unrevertSession(serverId, sessionId, directory)
            }
            CommandResult.Handled
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            CommandResult.Error(e.message ?: "Failed to redo")
        }
    }

    private suspend fun forkFromLatestMessage(serverId: String, sessionId: String): CommandResult {
        val messages = eventReducer.messages.value[sessionId] ?: emptyList()
        val lastMsgId = messages.lastOrNull()?.id ?: return CommandResult.Error("No messages to fork")
        return try {
            sessionOpsUseCase.forkSession(serverId, sessionId, lastMsgId, getDirectory(sessionId))
            CommandResult.Handled
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            CommandResult.Error(e.message ?: "Failed to fork")
        }
    }

    private suspend fun archiveCurrentSession(serverId: String, sessionId: String): CommandResult {
        return try {
            sessionOpsUseCase.updateSession(serverId, sessionId, getDirectory(sessionId), archived = System.currentTimeMillis())
            CommandResult.Handled
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            CommandResult.Error(e.message ?: "Failed to archive")
        }
    }

    private suspend fun summarizeSession(serverId: String, sessionId: String) {
        try {
            val directory = getDirectory(sessionId)
            sessionOpsUseCase.summarizeSession(serverId, sessionId, modelSelectionUseCase.selectedModel.value, directory)
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
        }
    }

    private suspend fun cycleVariant(serverId: String) {
        val variants = listOf("fast", "think", "agentic")
        val current = modelSelectionUseCase.selectedVariant.value
        val next = if (current == null) {
            variants.first()
        } else {
            val idx = variants.indexOf(current)
            if (idx >= 0 && idx < variants.lastIndex) variants[idx + 1] else null
        }
        modelSelectionUseCase.selectVariant(serverId, next)
    }

    private suspend fun cycleTheme() {
        val current = settingsRepository.theme.first()
        val next = when (current) {
            "system" -> "light"
            "light" -> "dark"
            else -> "dark"
        }
        settingsRepository.setTheme(next)
    }

    companion object {
        private const val TAG = "ChatCommandUseCase"
    }
}
