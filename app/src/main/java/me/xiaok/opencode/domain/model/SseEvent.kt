package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * 24 SSE event types processed by EventReducer.
 * Events come from GET /global/event SSE stream.
 * Each event has a `type` field and a `properties` field with event-specific data.
 */
@Serializable
sealed class SseEvent {

    // === Server Events ===

    @Serializable
    data object ServerConnected : SseEvent()

    @Serializable
    data object ServerHeartbeat : SseEvent()

    @Serializable
    data object ServerInstanceDisposed : SseEvent()

    // === Session Events ===

    @Serializable
    data class SessionCreated(
        val session: Session,
    ) : SseEvent()

    @Serializable
    data class SessionUpdated(
        val session: Session,
    ) : SseEvent()

    @Serializable
    data class SessionDeleted(
        val session: Session,
    ) : SseEvent()

    @Serializable
    data class SessionStatusChanged(
        val sessionId: String,
        val status: SessionStatus,
    ) : SseEvent()

    @Serializable
    data class SessionIdle(
        val sessionId: String,
    ) : SseEvent()

    @Serializable
    data class SessionDiff(
        val sessionId: String,
        val diffs: List<FileDiff>,
    ) : SseEvent()

    @Serializable
    data class SessionError(
        val sessionId: String? = null,
        val error: ErrorInfo? = null,
    ) : SseEvent()

    // === Message Events ===

    @Serializable
    data class MessageUpdated(
        val message: Message,
    ) : SseEvent()

    @Serializable
    data class MessageRemoved(
        val sessionId: String,
        val messageId: String,
    ) : SseEvent()

    @Serializable
    data class MessagePartUpdated(
        val part: Part,
    ) : SseEvent()

    @Serializable
    data class MessagePartDelta(
        val sessionId: String,
        val messageId: String,
        val partId: String,
        val field: String = "",
        val delta: String = "",
    ) : SseEvent()

    @Serializable
    data class MessagePartRemoved(
        val sessionId: String,
        val messageId: String,
        val partId: String,
    ) : SseEvent()

    // === Interaction Events ===

    @Serializable
    data class PermissionAsked(
        val permission: PermissionRequest,
    ) : SseEvent()

    @Serializable
    data class PermissionReplied(
        val sessionId: String,
        val requestId: String,
    ) : SseEvent()

    @Serializable
    data class QuestionAsked(
        val question: QuestionRequest,
    ) : SseEvent()

    @Serializable
    data class QuestionReplied(
        val sessionId: String,
        val requestId: String,
    ) : SseEvent()

    @Serializable
    data class QuestionRejected(
        val sessionId: String,
        val requestId: String,
    ) : SseEvent()

    // === Other Events ===

    @Serializable
    data class TodoUpdated(
        val sessionId: String,
        val todos: List<Todo>,
    ) : SseEvent()

    @Serializable
    data class VcsBranchUpdated(
        val branch: String,
    ) : SseEvent()

    @Serializable
    data object LspUpdated : SseEvent()

    @Serializable
    data class ProjectUpdated(
        val project: Project,
    ) : SseEvent()

    // === PTY Events ===

    @Serializable
    data class PtyCreated(
        val info: PtyInfo,
    ) : SseEvent()

    @Serializable
    data class PtyUpdated(
        val info: PtyInfo,
    ) : SseEvent()

    @Serializable
    data class PtyExited(
        val id: String,
        val exitCode: Int,
    ) : SseEvent()

    @Serializable
    data class PtyDeleted(
        val id: String,
    ) : SseEvent()

    // === MCP Events ===

    @Serializable
    data class McpBrowserOpenFailed(
        val mcpName: String,
        val url: String,
    ) : SseEvent()

    @Serializable
    data class McpToolsChanged(
        val server: String,
    ) : SseEvent()

    // === File Events ===

    @Serializable
    data class FileEdited(
        val file: String,
    ) : SseEvent()

    // === Installation Events ===

    @Serializable
    data class InstallationUpdated(
        val version: String,
    ) : SseEvent()

    @Serializable
    data class InstallationUpdateAvailable(
        val version: String,
    ) : SseEvent()
}
