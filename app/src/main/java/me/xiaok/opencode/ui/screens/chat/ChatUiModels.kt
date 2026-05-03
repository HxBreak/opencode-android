package me.xiaok.opencode.ui.screens.chat

import me.xiaok.opencode.domain.model.AgentConfig
import me.xiaok.opencode.domain.model.CommandInfo
import me.xiaok.opencode.domain.model.FileDiff
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.MentionItem
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.domain.model.Part
import me.xiaok.opencode.domain.model.PermissionRequest
import me.xiaok.opencode.domain.model.Provider
import me.xiaok.opencode.domain.model.QuestionRequest
import me.xiaok.opencode.domain.model.Session
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.domain.model.Todo

data class ChildSessionInfo(
    val session: Session,
    val status: SessionStatus = SessionStatus.Idle,
)

data class ChatTurn(
    val userMessage: Message,
    val assistantMessages: List<Message> = emptyList(),
    val turnId: String = userMessage.id,
    val groupedParts: List<TurnPartGroup> = emptyList(),
    val partLookup: Map<PartRef, Part> = emptyMap(),
    val isCompactionOnly: Boolean = false,
    val userParts: List<Part> = emptyList(),
    val isSyntheticUser: Boolean = false,
    val isActivelyReasoning: Boolean = false,
    val childSessionIdLookup: Map<String, String> = emptyMap(),
)

/** Core session content driven by SSE events through EventReducer */
data class ChatContentState(
    val session: Session? = null,
    val messages: List<Message> = emptyList(),
    val parts: Map<String, List<Part>> = emptyMap(),
    val turns: List<ChatTurn> = emptyList(),
    val permissions: List<PermissionRequest> = emptyList(),
    val questions: List<QuestionRequest> = emptyList(),
    val sessionDiffs: List<FileDiff> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val childSessionIds: Map<String, String> = emptyMap(),
    val childSessions: List<ChildSessionInfo> = emptyList(),
)

/** Transient loading/error flags managed by ViewModel */
data class ChatLoadingState(
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasOlderMessages: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    val submittingQuestionIds: Set<String> = emptySet(),
)

/** Model/agent/variant selection state */
data class ChatSelectionState(
    val providers: List<Provider> = emptyList(),
    val agents: List<AgentConfig> = emptyList(),
    val commands: List<CommandInfo> = emptyList(),
    val selectedAgent: String? = null,
    val selectedModel: ModelRef? = null,
    val selectedVariant: String? = null,
    val shareDisabled: Boolean = false,
)

/** Draft, images, mentions — high-frequency input state */
data class ChatInputState(
    val draftText: String = "",
    val draftImageUris: List<String> = emptyList(),
    val attachedImages: List<AttachedImage> = emptyList(),
    val mentions: List<MentionItem> = emptyList(),
)

/** Token/cost stats and PTY count */
data class ChatStatsState(
    val contextUsagePercent: Int = 0,
    val totalTokens: Long = 0L,
    val totalCost: Double = 0.0,
    val conversationTurns: Int = 0,
    val activePtyCount: Int = 0,
)
