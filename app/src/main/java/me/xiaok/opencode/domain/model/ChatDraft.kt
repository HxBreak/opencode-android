package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a saved draft state for a chat session.
 * Includes text content, selected agent/model/variant, and attached image URIs.
 */
@Serializable
data class ChatDraft(
    val text: String = "",
    val selectedAgent: String? = null,
    val selectedModel: ModelRef? = null,
    val selectedVariant: String? = null,
    val imageUris: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)
