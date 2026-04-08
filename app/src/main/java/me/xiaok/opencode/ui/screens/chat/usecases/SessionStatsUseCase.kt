package me.xiaok.opencode.ui.screens.chat.usecases

import me.xiaok.opencode.domain.model.*
import javax.inject.Inject

class SessionStatsUseCase @Inject constructor() {

    data class SessionStats(
        val contextUsagePercent: Int = 0,
        val totalTokens: Long = 0L,
        val totalCost: Double = 0.0,
        val conversationTurns: Int = 0,
    )

    fun computeSessionStats(
        messages: List<Message>,
        providers: List<Provider>,
        selectedModel: ModelRef?,
    ): SessionStats {
        val userCount = messages.count { it.isUser }

        // Cost: cumulative across all assistant messages (same as Web)
        var totalCost = 0.0
        for (message in messages) {
            val cost = message.info.cost
            if (cost != null) {
                totalCost += cost
            }
        }

        // Total tokens & context usage: from the last assistant message with tokens (same as Web)
        // Web picks the last assistant message and computes total = input + output + reasoning + cache.read + cache.write
        // Context usage % = total / model.contextLimit × 100
        val lastAssistantWithTokens = messages.lastOrNull {
            it.isAssistant && it.info.tokens != null && it.info.tokens!!.total > 0
        }
        val tokens = lastAssistantWithTokens?.info?.tokens
        // total = input + output + reasoning + cache.read + cache.write
        // Backend normalizes: input = inputTokens - cacheRead - cacheWrite (can be negative),
        // so the sum correctly reconstructs the total. Do NOT coerce individual fields to 0.
        val totalTokens = if (tokens != null) {
            tokens.input +
                tokens.output +
                tokens.reasoning +
                tokens.cache.read +
                tokens.cache.write
        } else {
            0L
        }

        val contextLimit = findContextLimit(providers, selectedModel, messages)

        val contextUsagePercent = if (contextLimit > 0 && totalTokens > 0) {
            ((totalTokens.toDouble() / contextLimit) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        return SessionStats(
            contextUsagePercent = contextUsagePercent,
            totalTokens = totalTokens,
            totalCost = totalCost,
            conversationTurns = userCount,
        )
    }

    /**
     * Find the context window limit for the current model.
     * Priority: selectedModel > last assistant message's model > first provider model
     */
    private fun findContextLimit(
        providers: List<Provider>,
        selectedModel: ModelRef?,
        messages: List<Message>,
    ): Long {
        // Try selected model first
        if (selectedModel != null) {
            val limit = getModelContextLimit(providers, selectedModel.providerID, selectedModel.modelID)
            if (limit > 0) return limit
        }

        // Try last assistant message's model
        val lastAssistant = messages.lastOrNull { it.isAssistant }
        if (lastAssistant != null) {
            val providerID = lastAssistant.info.providerID
            val modelID = lastAssistant.info.modelID
            if (providerID != null && modelID != null) {
                val limit = getModelContextLimit(providers, providerID, modelID)
                if (limit > 0) return limit
            }
        }

        return 0L
    }

    private fun getModelContextLimit(providers: List<Provider>, providerID: String, modelID: String): Long {
        return providers
            .find { it.id == providerID }
            ?.models?.get(modelID)
            ?.limit?.context
            ?: 0L
    }
}
