package me.xiaok.opencode.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class Part {
    abstract val id: String
    @SerialName("sessionID")
    abstract val sessionId: String
    @SerialName("messageID")
    abstract val messageId: String

    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val text: String = "",
        val time: PartTime? = null,
    ) : Part()

    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val text: String = "",
    ) : Part()

    @Serializable
    @SerialName("tool")
    data class Tool(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val tool: String = "",
        val state: ToolState = ToolState(),
        @SerialName("callID") val callId: String = "",
    ) : Part()

    @Serializable
    @SerialName("file")
    data class File(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val name: String = "",
        val url: String = "",
        val mimeType: String? = null,
    ) : Part()

    @Serializable
    @SerialName("subtask")
    data class Subtask(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val agent: String = "",
        val prompt: String = "",
        val output: String = "",
    ) : Part()

    @Serializable
    @SerialName("step-start")
    data class StepStart(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val name: String = "",
    ) : Part()

    @Serializable
    @SerialName("step-finish")
    data class StepFinish(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val reason: String? = null,
        val cost: Double = 0.0,
        val tokens: TokenUsage = TokenUsage(),
    ) : Part()

    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        @SerialName("snapshotID") val snapshotId: String = "",
        val label: String = "",
    ) : Part()

    @Serializable
    @SerialName("patch")
    data class Patch(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val diffs: List<FileDiff> = emptyList(),
    ) : Part()

    @Serializable
    @SerialName("agent")
    data class Agent(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val agent: String = "",
        val model: ModelRef = ModelRef(),
    ) : Part()

    @Serializable
    @SerialName("retry")
    data class Retry(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val error: String = "",
    ) : Part()

    @Serializable
    @SerialName("compaction")
    data class Compaction(
        override val id: String = "",
        @SerialName("sessionID") override val sessionId: String = "",
        @SerialName("messageID") override val messageId: String = "",
        val summary: String = "",
    ) : Part()
}

@Serializable
data class PartTime(
    val start: Long? = null,
    val end: Long? = null,
)

@Serializable
data class FileDiff(
    /** API returns "file", we map to [path] for internal use. */
    @SerialName("file")
    val path: String = "",
    /** Complete file content before the change. */
    val before: String = "",
    /** Complete file content after the change. */
    val after: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val status: String? = null,
)
