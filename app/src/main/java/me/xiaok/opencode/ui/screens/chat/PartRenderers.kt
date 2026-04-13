package me.xiaok.opencode.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.ui.screens.tooldetail.CachedToolData
import me.xiaok.opencode.ui.screens.tooldetail.ToolDetailCache

// ---------------------------------------------------------------------------
// Context tool grouping constants
// ---------------------------------------------------------------------------

/** Tools that should be hidden from the chat stream entirely. */
internal val HIDDEN_TOOLS = setOf("todowrite")

/** Context-gathering tools eligible for grouping when consecutive. */
internal val CONTEXT_GROUP_TOOLS = setOf("read", "glob", "grep", "list", "find")

// ---------------------------------------------------------------------------
// Part grouping — consecutive context tools merged into groups
// ---------------------------------------------------------------------------

/**
 * Wrapper that allows grouping multiple context tools into a single renderable unit.
 */
sealed class GroupedPart {
    data class Single(val part: Part) : GroupedPart()
    data class ContextGroup(val tools: List<Part.Tool>) : GroupedPart()
}

/**
 * Groups consecutive context-gathering tools (read/glob/grep/list/find) into
 * [GroupedPart.ContextGroup] wrappers. Non-context tools and other part types
 * pass through unchanged as [GroupedPart.Single]. Hidden tools (todowrite) are
 * filtered out entirely.
 *
 * Mimics the web frontend's `groupParts()` in message-part.tsx.
 */
fun groupParts(parts: List<Part>): List<GroupedPart> {
    val result = mutableListOf<GroupedPart>()
    var currentGroup = mutableListOf<Part.Tool>()

    fun flushGroup() {
        if (currentGroup.size >= 2) {
            result += GroupedPart.ContextGroup(currentGroup.toList())
        } else {
            // Single tool — render individually
            currentGroup.forEach { result += GroupedPart.Single(it) }
        }
        currentGroup = mutableListOf()
    }

    for (part in parts) {
        if (part is Part.Tool && part.tool in HIDDEN_TOOLS) continue // skip hidden

        if (part is Part.Tool && part.tool in CONTEXT_GROUP_TOOLS) {
            currentGroup += part
        } else {
            flushGroup()
            result += GroupedPart.Single(part)
        }
    }
    flushGroup()

    return result
}

// ---------------------------------------------------------------------------
// Turn-based rendering helpers (Wave 2)
// ---------------------------------------------------------------------------

/**
 * Lightweight reference to a [Part] identified by its message and part IDs.
 * Used for cross-message part lists where the full [Part] is looked up separately.
 */
data class PartRef(
    val messageId: String,
    val partId: String,
)

/**
 * Grouping wrapper for turn-based rendering — mirrors [GroupedPart] but works
 * with [PartRef] instead of the full [Part] object.
 */
sealed class TurnPartGroup {
    data class Single(val ref: PartRef) : TurnPartGroup()
    data class ContextGroup(val refs: List<PartRef>) : TurnPartGroup()
}

/**
 * Determines whether a [Part] should produce visible UI in the chat stream.
 * Parts that return `false` are silently skipped and do NOT break up consecutive
 * context-tool groups.
 *
 * Mirrors the web frontend's `renderable()` in message-part.tsx.
 */
fun renderable(part: Part): Boolean = when (part) {
    is Part.Text       -> part.text.isNotBlank()
    is Part.Reasoning  -> part.text.isNotBlank()
    is Part.Tool       -> part.tool !in HIDDEN_TOOLS &&
                          !(part.tool == "question" &&
                            (part.state.isPending || part.state.isRunning))
    is Part.Compaction -> true
    is Part.File       -> true
    is Part.Subtask    -> true
    // Double-insurance: these should already be filtered at the data layer.
    is Part.StepStart  -> false
    is Part.StepFinish -> false
    is Part.Agent      -> false
    is Part.Retry      -> false
    is Part.Snapshot   -> false
    is Part.Patch      -> false
}

/**
 * Groups consecutive context-gathering tools into [TurnPartGroup.ContextGroup]
 * wrappers — turn-based counterpart of [groupParts].
 *
 * @param parts List of (PartRef, Part) pairs that have **already** passed
 *              through [renderable]. The [Part] is needed to inspect the tool
 *              name; the [PartRef] is what ends up in the output groups.
 */
fun groupTurnParts(parts: List<Pair<PartRef, Part>>): List<TurnPartGroup> {
    val result = mutableListOf<TurnPartGroup>()
    var currentGroup = mutableListOf<PartRef>()

    fun flushGroup() {
        if (currentGroup.size >= 2) {
            result += TurnPartGroup.ContextGroup(currentGroup.toList())
        } else {
            currentGroup.forEach { result += TurnPartGroup.Single(it) }
        }
        currentGroup = mutableListOf()
    }

    for ((ref, part) in parts) {
        if (part is Part.Tool && part.tool in CONTEXT_GROUP_TOOLS) {
            currentGroup += ref
        } else {
            flushGroup()
            result += TurnPartGroup.Single(ref)
        }
    }
    flushGroup()

    return result
}

// ---------------------------------------------------------------------------
// Part renderer dispatcher
// ---------------------------------------------------------------------------

@Composable
fun PartRenderer(
    part: Part,
    modifier: Modifier = Modifier,
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    when (part) {
        is Part.Text -> TextPart(part = part, modifier = modifier, fontSize = fontSize)
        is Part.Reasoning -> ReasoningPart(part = part, modifier = modifier, isShimmerActive = isLatestActiveReasoning)
        is Part.Tool -> {
            // Cache the tool data before navigating to detail screen
            ToolDetailCache.put(part.id, CachedToolData(
                toolName = part.tool,
                state = part.state,
                childSessionId = part.state.childSessionId,
            ))
            ToolCard(
                toolName = part.tool,
                state = part.state,
                modifier = modifier,
                childSessionId = part.state.childSessionId,
                onNavigateToSession = onNavigateToSession,
                onClick = { onNavigateToToolDetail(part.id) },
                onQuestionClick = if (part.tool == "question") onQuestionClick else null,
            )
        }
        is Part.File -> FilePart(part = part, modifier = modifier)
        is Part.Subtask -> SubtaskPart(
            part = part,
            modifier = modifier,
            onNavigateToSession = onNavigateToSession,
            childSessionId = childSessionIds[part.id],
        )
        is Part.Compaction -> CompactionPart(part = part, modifier = modifier)
        is Part.Snapshot -> SnapshotPart(part = part, modifier = modifier)
        is Part.Patch -> PatchPart(part = part, modifier = modifier)
        is Part.Agent -> AgentPart(part = part, modifier = modifier)
        is Part.Retry -> RetryPart(part = part, modifier = modifier)
        is Part.StepStart -> {}
        is Part.StepFinish -> {}
    }
}

@Composable
fun GroupedPartRenderer(
    grouped: GroupedPart,
    modifier: Modifier = Modifier,
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    when (grouped) {
        is GroupedPart.Single -> PartRenderer(
            part = grouped.part,
            modifier = modifier,
            onNavigateToSession = onNavigateToSession,
            childSessionIds = childSessionIds,
            fontSize = fontSize,
            onQuestionClick = onQuestionClick,
            onNavigateToToolDetail = onNavigateToToolDetail,
            isLatestActiveReasoning = isLatestActiveReasoning,
        )
        is GroupedPart.ContextGroup -> ContextToolGroup(
            tools = grouped.tools,
            modifier = modifier,
            onNavigateToToolDetail = onNavigateToToolDetail,
        )
    }
}
