package me.xiaok.opencode.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.domain.model.ToolState

// ---------------------------------------------------------------------------
// Tool status colors
// ---------------------------------------------------------------------------

private val ColorToolPending = Color(0xFFFFA000)
private val ColorToolRunning = Color(0xFF42A5F5)
private val ColorToolCompleted = Color(0xFF66BB6A)
private val ColorToolError = Color(0xFFE53935)

// ---------------------------------------------------------------------------
// Main Tool Card — compact inline version for chat stream
// ---------------------------------------------------------------------------

/**
 * Compact inline tool card for the chat message stream.
 * Shows a single line with: status dot + tool name (italic) + title + status label.
 * Clicking navigates to the full ToolDetailScreen.
 *
 * Special case: "question" tool is handled with a dedicated card that opens a dialog.
 */
@Composable
fun ToolCard(
    toolName: String,
    state: ToolState,
    modifier: Modifier = Modifier,
    childSessionId: String? = null,
    onNavigateToSession: (String) -> Unit = {},
    onClick: () -> Unit = {},
    onQuestionClick: (() -> Unit)? = null,
) {
    // Question tool: special inline card that opens dialog on click
    if (toolName == "question") {
        QuestionToolCard(
            state = state,
            onQuestionClick = onQuestionClick,
            modifier = modifier,
        )
        return
    }

    val (statusColor, statusLabel) = toolStatusInfo(state)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = statusColor.copy(alpha = 0.04f),
        border = BorderStroke(
            width = 0.5.dp,
            color = statusColor.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot (8dp circle)
            Surface(
                modifier = Modifier.size(8.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = statusColor,
            ) {}

            Spacer(modifier = Modifier.width(8.dp))

            // Tool name — italic monospace
            Text(
                text = toolName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Title preview (if available)
            if (state.title.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Navigation arrow for task tool with child session
            if (childSessionId != null) {
                IconButton(
                    onClick = { onNavigateToSession(childSessionId) },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View sub-agent session",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Status label pill — only show for running/error states
            if (state.isRunning || state.isError) {
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = FontStyle.Italic,
                        ),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            // Chevron indicating tappable
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View details",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Question tool card — special handling
// ---------------------------------------------------------------------------

@Composable
private fun QuestionToolCard(
    state: ToolState,
    onQuestionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (statusColor, _) = toolStatusInfo(state)
    var expanded by remember { mutableStateOf(state.isCompleted) }

    val clickable = if (onQuestionClick != null && !state.isCompleted) {
        Modifier.clickable { onQuestionClick() }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(clickable),
        color = statusColor.copy(alpha = 0.04f),
        border = BorderStroke(
            width = 0.5.dp,
            color = statusColor.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row — always visible
            Surface(
                onClick = {
                    if (state.isCompleted) expanded = !expanded
                },
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Status dot
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = statusColor,
                    ) {}

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "question",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = when {
                            state.isCompleted -> state.title.ifEmpty { "Answered" }
                            onQuestionClick != null -> "Tap to answer"
                            else -> state.title.ifEmpty { "Waiting for answer..." }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = if (!state.isCompleted) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = when {
                            state.isCompleted -> ColorToolCompleted
                            !state.isCompleted && onQuestionClick != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )

                    if (!state.isCompleted && onQuestionClick != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (state.isCompleted) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ChevronRight
                            else Icons.Default.ChevronRight,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            // Expanded Q&A detail — only when completed
            if (state.isCompleted) {
                val qaItems = remember(state.input, state.metadata) {
                    parseQuestionAnswers(state.input, state.metadata)
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    if (qaItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            qaItems.forEachIndexed { index, qa ->
                                if (qa.header.isNotEmpty()) {
                                    Text(
                                        text = qa.header,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Q
                                Text(
                                    text = qa.question,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // A
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.size(6.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = ColorToolCompleted,
                                    ) {}
                                    Text(
                                        text = qa.answers.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = ColorToolCompleted,
                                        ),
                                    )
                                }
                                if (index < qaItems.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Parses question input + answer metadata from ToolState into displayable Q&A items.
 */
private data class QuestionAnswerItem(
    val header: String,
    val question: String,
    val answers: List<String>,
)

private fun parseQuestionAnswers(
    input: kotlinx.serialization.json.JsonElement?,
    metadata: kotlinx.serialization.json.JsonElement?,
): List<QuestionAnswerItem> {
    val inputObj = (input as? JsonObject) ?: return emptyList()
    val questionsArr = inputObj["questions"] as? JsonArray ?: return emptyList()
    val answersArr = (metadata as? JsonObject)?.get("answers") as? JsonArray

    return questionsArr.mapIndexed { index, questionEl ->
        val qObj = questionEl as? JsonObject ?: return@mapIndexed null
        val header = (qObj["header"] as? JsonPrimitive)?.content ?: ""
        val question = (qObj["question"] as? JsonPrimitive)?.content ?: ""
        val answers = answersArr
            ?.getOrNull(index)
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?: emptyList()
        QuestionAnswerItem(header, question, answers)
    }.filterNotNull()
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun toolStatusInfo(state: ToolState): Pair<Color, String> = when {
    state.isPending -> ColorToolPending to "Pending"
    state.isRunning -> ColorToolRunning to "Running"
    state.isCompleted -> ColorToolCompleted to "Completed"
    state.isError -> ColorToolError to "Error"
    else -> Color(0xFF9E9E9E) to state.status.replaceFirstChar { it.uppercase() }
}
