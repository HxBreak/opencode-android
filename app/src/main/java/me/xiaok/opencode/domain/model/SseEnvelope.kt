package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * SSE envelope from /global/event.
 * Each SSE data line is JSON with this structure.
 */
@Serializable
data class SseEnvelope(
    val directory: String = "",
    val payload: SsePayload = SsePayload(),
)

@Serializable
data class SsePayload(
    val type: String = "",
    val properties: kotlinx.serialization.json.JsonObject? = null,
)

/**
 * Instance-level SSE envelope from /event (no directory field).
 */
@Serializable
data class InstanceSseEnvelope(
    val type: String = "",
    val properties: kotlinx.serialization.json.JsonObject? = null,
)
