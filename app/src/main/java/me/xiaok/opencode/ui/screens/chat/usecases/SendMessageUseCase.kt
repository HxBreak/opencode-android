package me.xiaok.opencode.ui.screens.chat.usecases

import android.util.Log
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.DraftRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.domain.model.MentionItem
import me.xiaok.opencode.ui.screens.chat.AttachedImage
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SendMessageUseCase @Inject constructor(
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val draftRepository: DraftRepository,
    private val settingsRepository: SettingsRepository,
    private val errorCollector: ErrorCollector,
) {

    sealed class SendResult {
        data object Success : SendResult()
        data class Error(val message: String) : SendResult()
        data object ShellCommandSent : SendResult()
    }

    data class SendContext(
        val serverId: String,
        val sessionId: String,
        val text: String,
        val mentions: List<MentionItem>,
        val attachedImages: List<AttachedImage>,
        val selectedAgent: String?,
        val selectedModel: ModelRef?,
        val selectedVariant: String?,
        val draftImageUris: List<String>,
        val sessionDirectory: String?,
    )

    suspend fun execute(ctx: SendContext): SendResult {
        if (ctx.text.isBlank() && ctx.attachedImages.isEmpty()) return SendResult.Error("")

        Log.d(TAG, "sendMessage: LAUNCHED, serverId=${ctx.serverId}, allServers=${serverRepository.servers.value.map { it.id }}")

        Log.d(TAG, "sendMessage: before clearDraft")
        draftRepository.clearDraft(ctx.sessionId)
        Log.d(TAG, "sendMessage: after clearDraft")

        val server = serverRepository.getServer(ctx.serverId)
        Log.d(TAG, "sendMessage: server=$server")
        if (server == null) {
            Log.e(TAG, "sendMessage: server not found for serverId=${ctx.serverId}")
            restoreDraft(ctx)
            return SendResult.Error("Server not found. Please reconnect.")
        }

        // Server-side command: /command [args]
        if (ctx.text.trimStart().startsWith("/")) {
            val content = ctx.text.trimStart().removePrefix("/")
            val cmdParts = content.split(" ", limit = 2)
            val commandName = cmdParts[0]
            val commandArgs = cmdParts.getOrNull(1)
            try {
                api.sendCommand(server, ctx.sessionId, commandName, commandArgs, directory = ctx.sessionDirectory)
                Log.d(TAG, "sendMessage: sendCommand returned successfully, command=$commandName")
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage: sendCommand FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                errorCollector.logError(e, "Chat")
                restoreDraft(ctx)
                return SendResult.Error(e.message ?: "Command failed")
            }
            return SendResult.Success
        }

        // Shell command: !command
        if (ctx.text.trimStart().startsWith("!")) {
            val command = ctx.text.trimStart().removePrefix("!").trim()
            if (command.isNotBlank()) {
                val agent = ctx.selectedAgent
                    ?: return SendResult.Error("No agent selected — cannot run shell command")
                try {
                    api.runShell(server, ctx.sessionId, command, agent = agent, directory = ctx.sessionDirectory)
                    Log.d(TAG, "sendMessage: runShell returned successfully, command=$command")
                } catch (e: Exception) {
                    Log.e(TAG, "sendMessage: runShell FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                    errorCollector.logError(e, "Chat")
                    restoreDraft(ctx)
                    return SendResult.Error(e.message ?: "Shell command failed")
                }
            }
            return SendResult.ShellCommandSent
        }

        val parts = buildParts(ctx)

        Log.d(TAG, "sendMessage: BEFORE api.promptAsync, parts=$parts, agent=${ctx.selectedAgent}, model=${ctx.selectedModel}")
        try {
            api.promptAsync(
                conn = server,
                sessionId = ctx.sessionId,
                parts = parts,
                agent = ctx.selectedAgent,
                model = ctx.selectedModel,
                variant = ctx.selectedVariant,
                directory = ctx.sessionDirectory,
            )
            Log.d(TAG, "sendMessage: api.promptAsync returned successfully")
        } catch (inner: Exception) {
            Log.e(TAG, "sendMessage: api.promptAsync FAILED: ${inner.javaClass.simpleName}: ${inner.message}", inner)
            errorCollector.logError(inner, "Chat")
            restoreDraft(ctx)
            return SendResult.Error(inner.message ?: "Failed to send message")
        }

        postSendRefresh(ctx)

        return SendResult.Success
    }

    private suspend fun restoreDraft(ctx: SendContext) {
        val draft = ChatDraft(
            text = ctx.text,
            selectedAgent = ctx.selectedAgent,
            selectedModel = ctx.selectedModel,
            selectedVariant = ctx.selectedVariant,
            imageUris = ctx.draftImageUris,
        )
        draftRepository.saveDraft(ctx.sessionId, draft)
    }

    private suspend fun postSendRefresh(ctx: SendContext) {
        try {
            val refreshServer = serverRepository.getServer(ctx.serverId) ?: return
            val page = api.listMessages(refreshServer, ctx.sessionId, limit = settingsRepository.initialMessages.first(), directory = ctx.sessionDirectory)
            eventReducer.setMessages(ctx.sessionId, page.messages)
            page.messages.forEach { message ->
                if (eventReducer.parts.value[message.id] == null) {
                    eventReducer.setParts(message.id, message.parts)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendMessage: post-send refresh failed (non-critical)", e)
        }
    }

    private fun buildParts(ctx: SendContext): List<Map<String, Any>> {
        val parts = mutableListOf<Map<String, Any>>()
        val directory = ctx.sessionDirectory

        if (ctx.mentions.isNotEmpty()) {
            var cursor = 0
            for (mention in ctx.mentions) {
                val beforeText = ctx.text.substring(cursor, mention.start.coerceIn(cursor, ctx.text.length)).trim()
                if (beforeText.isNotBlank()) {
                    parts.add(mapOf("type" to "text", "text" to beforeText))
                }

                when (mention) {
                    is MentionItem.FileMention -> {
                        val absolutePath = if (mention.path.startsWith("/")) {
                            mention.path
                        } else {
                            val dir = directory?.trimEnd('/') ?: ""
                            "$dir/${mention.path}"
                        }
                        parts.add(mapOf(
                            "type" to "file",
                            "url" to "file://$absolutePath",
                            "mime" to "text/plain",
                            "filename" to mention.path.substringAfterLast('/'),
                            "source" to mapOf(
                                "type" to "file",
                                "text" to mapOf(
                                    "value" to mention.displayText,
                                    "start" to mention.start,
                                    "end" to mention.end,
                                ),
                                "path" to absolutePath,
                            ),
                        ))
                    }
                    is MentionItem.AgentMention -> {
                        parts.add(mapOf(
                            "type" to "agent",
                            "name" to mention.name,
                            "source" to mapOf(
                                "value" to mention.displayText,
                                "start" to mention.start,
                                "end" to mention.end,
                            ),
                        ))
                    }
                }

                cursor = mention.end.coerceAtMost(ctx.text.length)
            }

            val afterText = ctx.text.substring(cursor).trim()
            if (afterText.isNotBlank()) {
                parts.add(mapOf("type" to "text", "text" to afterText))
            }
        } else if (ctx.text.isNotBlank()) {
            parts.add(mapOf("type" to "text", "text" to ctx.text.trim()))
        }

        ctx.attachedImages.forEach { image ->
            parts.add(mapOf(
                "type" to "file",
                "url" to "data:${image.mimeType};base64,${image.base64}",
                "mime" to image.mimeType,
            ))
        }

        return parts
    }

    companion object {
        private const val TAG = "SendMessageUseCase"
    }
}
