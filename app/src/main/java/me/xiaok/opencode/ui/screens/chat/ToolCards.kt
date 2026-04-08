package me.xiaok.opencode.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.domain.model.Part
import me.xiaok.opencode.domain.model.ToolState

// ---------------------------------------------------------------------------
// Tool status colors
// ---------------------------------------------------------------------------

private val ColorToolPending = Color(0xFFFFA000)
private val ColorToolRunning = Color(0xFF42A5F5)
private val ColorToolCompleted = Color(0xFF66BB6A)
private val ColorToolError = Color(0xFFE53935)

// ---------------------------------------------------------------------------
// Tool type registry
// ---------------------------------------------------------------------------

private data class ToolTypeInfo(
    val emoji: String,
    val displayName: String,
    val hasDetails: Boolean,
    val deferContent: Boolean = false,
)

private fun getToolTypeInfo(toolName: String): ToolTypeInfo = when (toolName) {
    "bash" -> ToolTypeInfo("\uD83D\uDCBB", "Shell", hasDetails = true)
    "grep" -> ToolTypeInfo("\uD83D\uDD0D", "Grep", hasDetails = true)
    "glob" -> ToolTypeInfo("\uD83D\uDD0D", "Glob", hasDetails = true)
    "list" -> ToolTypeInfo("\uD83D\uDCC2", "List", hasDetails = true)
    "find" -> ToolTypeInfo("\uD83D\uDD0D", "Find", hasDetails = true)
    "read" -> ToolTypeInfo("\uD83D\uDC53", "Read", hasDetails = false)
    "edit" -> ToolTypeInfo("\u270F\uFE0F", "Edit", hasDetails = true, deferContent = true)
    "write" -> ToolTypeInfo("\uD83D\uDCDD", "Write", hasDetails = true, deferContent = true)
    "apply_patch" -> ToolTypeInfo("\uD83E\uDDF1", "Patch", hasDetails = true, deferContent = true)
    "webfetch" -> ToolTypeInfo("\uD83C\uDF10", "Fetch", hasDetails = false)
    "websearch" -> ToolTypeInfo("\uD83D\uDD0E", "Search", hasDetails = true)
    "codesearch" -> ToolTypeInfo("\uD83D\uDD0E", "CodeSearch", hasDetails = true)
    "task" -> ToolTypeInfo("\uD83D\uDCCB", "Task", hasDetails = false)
    "skill" -> ToolTypeInfo("\u2699\uFE0F", "Skill", hasDetails = false)
    "question" -> ToolTypeInfo("\u2753", "Question", hasDetails = true)
    else -> ToolTypeInfo("\uD83D\uDD27", toolName, hasDetails = true)
}

// ---------------------------------------------------------------------------
// Argument extraction helpers
// ---------------------------------------------------------------------------

private fun extractSubtitle(toolName: String, input: JsonElement?): String {
    val obj = input as? JsonObject ?: return ""
    return when (toolName) {
        "bash" -> obj.stringField("command").take(60)
        "grep" -> buildString {
            append(obj.stringField("pattern"))
            val include = obj.stringField("include")
            if (include.isNotEmpty()) append(" in " + include)
        }
        "glob" -> obj.stringField("pattern")
        "list" -> obj.stringField("path")
        "find" -> buildString {
            val pattern = obj.stringField("pattern")
            if (pattern.isNotEmpty()) append(pattern)
            val path = obj.stringField("path")
            if (path.isNotEmpty()) {
                if (isNotEmpty()) append(" in ")
                append(path)
            }
        }
        "read" -> buildString {
            val filePath = obj.stringField("filePath")
            if (filePath.isNotEmpty()) {
                append(filePath.substringAfterLast('/'))
            }
            val offset = obj.intField("offset")
            val limit = obj.intField("limit")
            if (offset != null || limit != null) {
                append(" [")
                if (offset != null) append("offset=" + offset)
                if (offset != null && limit != null) append(", ")
                if (limit != null) append("limit=" + limit)
                append("]")
            }
        }
        "edit", "write" -> obj.stringField("filePath")
            .ifEmpty { obj.stringField("file_path") }
            .substringAfterLast('/')
        "apply_patch" -> {
            val p = obj.stringField("path")
                .ifEmpty { obj.stringField("file_path") }
            if (p.isNotEmpty()) p.substringAfterLast('/') else ""
        }
        "webfetch" -> obj.stringField("url")
        "websearch", "codesearch" -> obj.stringField("query")
        else -> buildString {
            val desc = obj.stringField("description")
            if (desc.isNotEmpty()) { append(desc); return@buildString }
            val q = obj.stringField("query")
            if (q.isNotEmpty()) { append(q); return@buildString }
            val url = obj.stringField("url")
            if (url.isNotEmpty()) { append(url); return@buildString }
            val fp = obj.stringField("filePath")
                .ifEmpty { obj.stringField("file_path") }
                .ifEmpty { obj.stringField("path") }
            if (fp.isNotEmpty()) append(fp)
        }
    }
}

private fun extractArgTags(toolName: String, input: JsonElement?): List<Pair<String, String>> {
    val obj = input as? JsonObject ?: return emptyList()
    val tags = mutableListOf<Pair<String, String>>()
    for ((key, value) in obj) {
        if (tags.size >= 3) break
        val strValue = value.stringValue()
        if (strValue != null && strValue.length <= 40) {
            tags += key to strValue
        }
    }
    return tags
}

private fun JsonObject.stringField(key: String): String {
    val el = this[key] ?: return ""
    return el.stringValue() ?: ""
}

private fun JsonObject.intField(key: String): Int? {
    val el = this[key] ?: return null
    return try { el.jsonPrimitive.int } catch (_: Exception) { null }
}

private fun JsonElement.stringValue(): String? = try {
    jsonPrimitive.content
} catch (_: Exception) { null }

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

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

// ---------------------------------------------------------------------------
// Pulsing status dot
// ---------------------------------------------------------------------------

@Composable
private fun PulsingStatusDot(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "toolPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Surface(
        modifier = modifier.size(8.dp),
        shape = CircleShape,
        color = color.copy(alpha = pulseAlpha),
    ) {}
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(8.dp),
        shape = CircleShape,
        color = color,
    ) {}
}

// ---------------------------------------------------------------------------
// Main Tool Card — collapsible with type-specific rendering
// ---------------------------------------------------------------------------

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
    // Question tool: special inline card
    if (toolName == "question") {
        QuestionToolCard(
            state = state,
            onQuestionClick = onQuestionClick,
            modifier = modifier,
        )
        return
    }

    val typeInfo = getToolTypeInfo(toolName)
    val (statusColor, statusLabel) = toolStatusInfo(state)
    val isRunning = state.isRunning || state.isPending
    val canExpand = typeInfo.hasDetails && state.output.isNotEmpty()
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = statusColor.copy(alpha = 0.04f),
        border = BorderStroke(
            width = 0.5.dp,
            color = statusColor.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Collapsed header row — always visible
            Surface(
                onClick = {
                    if (canExpand && !isRunning) {
                        expanded = !expanded
                    } else if (!canExpand) {
                        onClick()
                    }
                },
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Status dot — pulsing when running
                    if (isRunning) {
                        PulsingStatusDot(color = statusColor)
                    } else {
                        StatusDot(color = statusColor)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Emoji
                    Text(
                        text = typeInfo.emoji,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Display name
                    Text(
                        text = typeInfo.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Subtitle or arg tags
                    val subtitle = extractSubtitle(toolName, state.input)
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (state.title.isNotEmpty()) {
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
                        // Show arg tags
                        val tags = extractArgTags(toolName, state.input)
                        if (tags.isNotEmpty()) {
                            tags.forEach { (key, value) ->
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(3.dp),
                                ) {
                                    Text(
                                        text = "$key=$value",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    // Child session arrow
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

                    // Status pill — running/error
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

                    // Expand/collapse arrow
                    if (canExpand) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View details",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            // Expanded content — type-specific renderers
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Error display at top
                    if (state.error.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }

                    // Type-specific content
                    val output = state.output
                    if (output.isNotEmpty()) {
                        when (toolName) {
                            "bash" -> BashOutputContent(output)
                            "edit", "write", "apply_patch" -> DiffOutputContent(output)
                            "websearch", "codesearch" -> LinksOutputContent(output)
                            else -> GenericOutputContent(output)
                        }
                    }

                    // View full details button
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        Text("View full details")
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Type-specific output renderers
// ---------------------------------------------------------------------------

@Composable
private fun BashOutputContent(output: String) {
    val context = LocalContext.current
    val lines = output.lines()

    // Separate command from output
    val commandLine = if (lines.isNotEmpty() && lines[0].startsWith("$")) lines[0] else null
    val outputLines = if (commandLine != null) lines.drop(1) else lines

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        // Command header with copy button
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

        // Output preview — max 15 lines
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
private fun DiffOutputContent(output: String) {
    val lines = output.lines()

    // Count additions and deletions
    var additions = 0
    var deletions = 0
    for (line in lines) {
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions++
            line.startsWith("-") && !line.startsWith("---") -> deletions++
        }
    }

    // Diff lines preview — max 20 lines
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
        // Summary bar
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

        // Diff lines
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
private fun LinksOutputContent(output: String) {
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
private fun GenericOutputContent(output: String) {
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

// ---------------------------------------------------------------------------
// Question tool card — special handling (preserved existing Q&A logic)
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
                    // Status dot — pulsing when pending/running
                    if (state.isPending || state.isRunning) {
                        PulsingStatusDot(color = statusColor)
                    } else {
                        StatusDot(color = statusColor)
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
