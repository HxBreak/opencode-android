package me.xiaok.opencode.ui.screens.chat.usecases

import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.MentionItem
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

class MentionManagementUseCase @Inject constructor(
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val errorCollector: ErrorCollector,
) {
    fun addMention(
        currentMentions: List<MentionItem>,
        mention: MentionItem,
        start: Int,
        end: Int,
    ): List<MentionItem> {
        val positioned = when (mention) {
            is MentionItem.FileMention -> mention.copy(start = start, end = end)
            is MentionItem.AgentMention -> mention.copy(start = start, end = end)
        }
        return currentMentions + positioned
    }

    fun removeMention(
        currentMentions: List<MentionItem>,
        displayText: String,
    ): List<MentionItem> {
        return currentMentions.filter { it.displayText != displayText }
    }

    fun clearMentions(): List<MentionItem> = emptyList()

    fun reconcileMentions(
        currentMentions: List<MentionItem>,
        text: String,
    ): List<MentionItem> {
        val updated = mutableListOf<MentionItem>()
        for (mention in currentMentions) {
            val index = text.indexOf(mention.displayText)
            if (index >= 0) {
                val newMention = when (mention) {
                    is MentionItem.FileMention -> mention.copy(
                        start = index,
                        end = index + mention.displayText.length,
                    )
                    is MentionItem.AgentMention -> mention.copy(
                        start = index,
                        end = index + mention.displayText.length,
                    )
                }
                updated.add(newMention)
            }
        }
        return updated
    }

    fun getMentionDisplayTexts(currentMentions: List<MentionItem>): Set<String> {
        return currentMentions.map { it.displayText }.toSet()
    }

    suspend fun searchFiles(
        serverId: String,
        sessionId: String,
        query: String,
    ): List<String> {
        return try {
            val server = serverRepository.getServer(serverId) ?: return emptyList()
            val directory = eventReducer.sessions.value[sessionId]?.directory
            api.fileSearch(server, query, limit = 20, directory = directory)
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            emptyList()
        }
    }
}
