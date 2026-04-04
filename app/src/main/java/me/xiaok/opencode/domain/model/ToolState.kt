package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Tool state from API. The API uses a flat structure with a "status" discriminator:
 * ```json
 * {"status": "completed", "input": {...}, "output": "...", "title": "...", "metadata": {...}}
 * ```
 *
 * We keep it as a single data class since the status field determines behavior.
 * Use `status` to branch in UI code.
 */
@Serializable
data class ToolState(
    val status: String = "pending",
    val input: JsonElement? = null,
    val output: String = "",
    val title: String = "",
    val error: String = "",
    val metadata: JsonElement? = null,
    val raw: JsonElement? = null,
) {
    val isPending: Boolean get() = status == "pending"
    val isRunning: Boolean get() = status == "running"
    val isCompleted: Boolean get() = status == "completed"
    val isError: Boolean get() = status == "error"

    /**
     * Extracts `sessionId` from `state.metadata.sessionId`.
     * This is set by the "task" tool when it spawns a child session:
     * ```json
     * "state": { "metadata": { "sessionId": "ses_child_xxx" } }
     * ```
     */
    val childSessionId: String?
        get() = (metadata as? JsonObject)
            ?.get("sessionId")
            ?.jsonPrimitive
            ?.content
}
