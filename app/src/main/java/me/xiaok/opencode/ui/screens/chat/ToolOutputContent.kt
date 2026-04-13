package me.xiaok.opencode.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import me.xiaok.opencode.domain.model.ToolState
import me.xiaok.opencode.ui.components.common.PulsingDot
import me.xiaok.opencode.ui.components.common.StatusDot

@Composable
internal fun BashOutputContent(output: String) {
    val context = LocalContext.current
    val lines = output.lines()

    val commandLine = if (lines.isNotEmpty() && lines[0].startsWith("$")) lines[0] else null
    val outputLines = if (commandLine != null) lines.drop(1) else lines

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        if (commandLine != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = commandLine.removePrefix("$ "),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = { copyToClipboard(context, "command", commandLine.removePrefix("$ ")) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy command",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        if (outputLines.isNotEmpty()) {
            val previewLines = outputLines.take(15)
            val remaining = outputLines.size - previewLines.size

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(
                    topStart = if (commandLine != null) 0.dp else 4.dp,
                    topEnd = if (commandLine != null) 0.dp else 4.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 4.dp,
                ),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    SelectionContainer {
                        Text(
                            text = previewLines.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (remaining > 0) {
                        Text(
                            text = "... $remaining more lines",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiffOutputContent(output: String) {
    val lines = output.lines()

    var additions = 0
    var deletions = 0
    for (line in lines) {
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions++
            line.startsWith("-") && !line.startsWith("---") -> deletions++
        }
    }

    val diffLines = lines.filter { line ->
        line.startsWith("+") || line.startsWith("-") || line.startsWith("@@")
    }
    val previewLines = diffLines.take(20)
    val remaining = diffLines.size - previewLines.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildAnnotatedString {
                        if (additions > 0) {
                            withStyle(SpanStyle(color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)) {
                                append("+$additions")
                            }
                        }
                        if (additions > 0 && deletions > 0) append(" ")
                        if (deletions > 0) {
                            withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.Medium)) {
                                append("-$deletions")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (previewLines.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                previewLines.forEach { line ->
                    val bgColor = when {
                        line.startsWith("+") -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                        line.startsWith("-") -> Color(0xFFE53935).copy(alpha = 0.08f)
                        line.startsWith("@@") -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        line.startsWith("+") -> Color(0xFF4CAF50)
                        line.startsWith("-") -> Color(0xFFE53935)
                        line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        if (remaining > 0) {
            Text(
                text = "... $remaining more lines",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp),
            )
        }
    }
}

@Composable
internal fun LinksOutputContent(output: String) {
    val lines = output.lines().filter { it.isNotBlank() }
    val previewLines = lines.take(5)
    val remaining = lines.size - previewLines.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        previewLines.forEach { line ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = line.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (remaining > 0) {
            Text(
                text = "... $remaining more results",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun GenericOutputContent(output: String) {
    val lines = output.lines()
    val previewLines = lines.take(10)
    val remaining = lines.size - previewLines.size

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SelectionContainer {
                Text(
                    text = previewLines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (remaining > 0) {
                Text(
                    text = "... $remaining more lines",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun QuestionToolCard(
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
                    if (state.isPending || state.isRunning) {
                        PulsingDot(color = statusColor, size = 8.dp)
                    } else {
                        StatusDot(color = statusColor, size = 8.dp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "\u2753",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Question",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
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
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

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
                                Text(
                                    text = qa.question,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.size(6.dp),
                                        shape = CircleShape,
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

private data class QuestionAnswerItem(
    val header: String,
    val question: String,
    val answers: List<String>,
)

private fun parseQuestionAnswers(
    input: JsonElement?,
    metadata: JsonElement?,
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
