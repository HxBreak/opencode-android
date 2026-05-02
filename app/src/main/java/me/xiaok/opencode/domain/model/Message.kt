package me.xiaok.opencode.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

@Serializable
data class ModelRef(
    val providerID: String = "",
    val modelID: String = "",
)

@Serializable
data class MessageModel(
    val providerID: String = "",
    val modelID: String = "",
    val variant: String? = null,
)

@Serializable
data class TokenUsage(
    val total: Long = 0L,
    val input: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val cache: CacheInfo = CacheInfo(),
)

@Serializable
data class CacheInfo(
    val read: Long = 0L,
    val write: Long = 0L,
)

/**
 * Error structure matching the backend wire format:
 * ```json
 * {
 *   "name": "APIError",
 *   "data": {
 *     "message": "This model is not available in your region.",
 *     "statusCode": 403,
 *     "isRetryable": false,
 *     "responseHeaders": { ... },
 *     "responseBody": "..."
 *   }
 * }
 * ```
 */
@Serializable
data class ErrorInfo(
    val name: String = "",
    val data: ErrorData? = null,
) {
    /**
     * Convenience accessor for the human-readable error message.
     * Used by UI to display error text.
     */
    val message: String get() = data?.message ?: ""
}

@Serializable
data class ErrorData(
    val message: String = "",
    val statusCode: Int? = null,
    val isRetryable: Boolean? = null,
)

/**
 * Tolerant serializer for UserSummary? — handles server sending "summary": true (boolean)
 * instead of the expected object. Returns null for non-object values.
 */
object UserSummarySerializer : KSerializer<UserSummary?> {
    private val delegate = UserSummary.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: UserSummary?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            delegate.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): UserSummary? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = jsonDecoder.decodeJsonElement()
        return if (element is JsonObject) {
            jsonDecoder.json.decodeFromJsonElement(delegate, element)
        } else {
            null // "summary": true or other non-object → treat as null
        }
    }
}

@Serializable
data class UserSummary(
    val diffs: List<FileDiff> = emptyList(),
)

@Serializable
data class MessageTime(
    val created: Long = 0L,
    val updated: Long = 0L,
    val completed: Long? = null,
)

@Serializable
data class MessagePath(
    val cwd: String = "",
    val root: String = "",
)

/**
 * Message model matching the API wire format:
 * ```json
 * {
 *   "info": {
 *     "role": "user" | "assistant",
 *     "id": "msg_...",
 *     "sessionID": "ses_...",
 *     "time": {"created": ..., "completed": ...},
 *     ...
 *   },
 *   "parts": [{ "type": "text", ... }, ...]
 * }
 * ```
 */
@Serializable
data class Message(
    val info: MessageInfo = MessageInfo(),
    val parts: List<Part> = emptyList(),
) {
    val id: String get() = info.id
    val sessionId: String get() = info.sessionID
    val role: String get() = info.role
    val time: MessageTime get() = info.time
    val isUser: Boolean get() = info.role == "user"
    val isAssistant: Boolean get() = info.role == "assistant"
}

/**
 * Message metadata from the "info" field.
 * Fields common to both user and assistant messages.
 * Extra fields for user/assistant are present depending on role.
 */
@Serializable
data class MessageInfo(
    val role: String = "",
    val id: String = "",
    val sessionID: String = "",
    val time: MessageTime = MessageTime(),
    // User-specific fields
    @Serializable(with = UserSummarySerializer::class)
    val summary: UserSummary? = null,
    val model: MessageModel? = null,
    // Assistant-specific fields
    val parentID: String? = null,
    val modelID: String? = null,
    val providerID: String? = null,
    val mode: String? = null,
    val agent: String? = null,
    val path: MessagePath? = null,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
    val error: ErrorInfo? = null,
    val finish: String? = null,
)
