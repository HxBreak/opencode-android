package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the content response from GET /file/content.
 *
 * Text files: type="text", content=raw text, optional diff/patch.
 * Binary files: type="binary", content=base64 encoded, encoding="base64", mimeType set.
 */
@Serializable
data class FileContent(
    val type: String = "text",       // "text" | "binary"
    val content: String = "",
    val diff: String? = null,
    val patch: kotlinx.serialization.json.JsonElement? = null,
    val encoding: String? = null,    // "base64" for binary
    val mimeType: String? = null,    // e.g. "image/png"
) {
    val isBinary: Boolean get() = type == "binary"
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
    val isText: Boolean get() = type == "text" || type.isBlank()
}
