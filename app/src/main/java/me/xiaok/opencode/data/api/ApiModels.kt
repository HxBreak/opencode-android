package me.xiaok.opencode.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.domain.model.SessionStatus

/**
 * Request/response models and parsing helpers for OpenCodeApi.
 * Extracted from OpenCodeApi to keep the core class minimal.
 * Marked internal where appropriate so only this module can use them.
 */

@Serializable
data class MessagesPage(
    val messages: List<Message>,
    val nextCursor: String?,
)

@Serializable
data class SendMessageRequest(
    val agent: String? = null,
    val model: ModelRef? = null,
    val variant: String? = null,
    val parts: List<Map<String, String>>? = null,
)

@Serializable
data class HealthResponse(
    val healthy: Boolean = false,
    val version: String = "",
)

internal fun parseSessionStatus(element: JsonElement): SessionStatus {
    val obj = element.jsonObject
    val typeStr = obj["type"]?.jsonPrimitive?.content ?: return SessionStatus.Idle
    return when (typeStr.lowercase()) {
        "idle" -> SessionStatus.Idle
        "busy" -> SessionStatus.Busy
        "retry" -> SessionStatus.Retry(
            attempt = obj["attempt"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            message = obj["message"]?.jsonPrimitive?.content ?: "",
            next = obj["next"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        )
        else -> SessionStatus.Idle
    }
}
