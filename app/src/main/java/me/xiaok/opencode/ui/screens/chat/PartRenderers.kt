package me.xiaok.opencode.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.ui.screens.tooldetail.CachedToolData
import me.xiaok.opencode.ui.screens.tooldetail.ToolDetailCache

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
) {
    when (part) {
        is Part.Text -> TextPart(part = part, modifier = modifier, fontSize = fontSize)
        is Part.Reasoning -> ReasoningPart(part = part, modifier = modifier)
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
        is Part.StepStart -> { /* hidden */ }
        is Part.StepFinish -> { /* hidden */ }
        is Part.Snapshot -> SnapshotPart(part = part, modifier = modifier)
        is Part.Patch -> PatchPart(part = part, modifier = modifier)
        is Part.Agent -> AgentPart(part = part, modifier = modifier)
        is Part.Retry -> RetryPart(part = part, modifier = modifier)
        is Part.Compaction -> CompactionPart(part = part, modifier = modifier)
    }
}

// ---------------------------------------------------------------------------
// Text / Markdown — code blocks extracted and rendered with copy buttons
// ---------------------------------------------------------------------------

/** Segment produced by splitting raw markdown around fenced code blocks. */
private sealed class MarkdownSegment {
    data class Plain(val text: String) : MarkdownSegment()
    data class Code(val language: String, val code: String) : MarkdownSegment()
}

/**
 * Identifies "protected" line ranges in the markdown text where single newlines
 * must be preserved as-is. This includes:
 * - Fenced code blocks (``` ... ```)
 * - GFM tables (lines starting/continuing with `|`, including separator `|---|`)
 *
 * Newlines within these blocks must NOT be doubled, or the structure breaks.
 */
private fun findProtectedRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()

    // 1. Fenced code blocks
    val codeBlockRegex = Regex("""```[\s\S]*?```""")
    codeBlockRegex.findAll(text).forEach { match ->
        ranges += match.range
    }

    // 2. GFM table blocks — consecutive non-empty lines containing '|'
    val lines = text.linesWithOffsets()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].text.trim()
        if (line.isNotEmpty() && line.contains('|')) {
            // Start of a potential table block
            val startOffset = lines[i].offset
            var endIdx = i
            while (endIdx + 1 < lines.size) {
                val nextLine = lines[endIdx + 1].text.trim()
                if (nextLine.isNotEmpty() && nextLine.contains('|')) {
                    endIdx++
                } else {
                    break
                }
            }
            val endOffset = lines[endIdx].offset + lines[endIdx].text.length
            ranges += IntRange(startOffset, endOffset)
            i = endIdx + 1
        } else {
            i++
        }
    }

    return ranges
}

/** Holds a line of text and its character offset within the original string. */
private data class LineWithOffset(val text: String, val offset: Int)

/** Splits text into lines, each annotated with its starting character offset. */
private fun String.linesWithOffsets(): List<LineWithOffset> {
    val result = mutableListOf<LineWithOffset>()
    var offset = 0
    for (line in this.lines()) {
        result += LineWithOffset(line, offset)
        offset += line.length + 1 // +1 for the \n character
    }
    return result
}

/**
 * Preprocesses text to ensure single newlines are rendered as hard breaks
 * in Markdown. CommonMark treats single `\n` as a soft break (space), so we
 * convert lone `\n` to `\n\n` (paragraph break) for proper display.
 *
 * Preserves:
 * - Existing `\n\n` (already paragraph breaks)
 * - Content inside code blocks (handled separately by parseMarkdownSegments)
 * - GFM table blocks (newlines within tables must stay as-is)
 */
private fun ensureMarkdownLineBreaks(text: String): String {
    val protectedRanges = findProtectedRanges(text)
    val sb = StringBuilder(text.length * 2)
    var i = 0
    while (i < text.length) {
        // Check if current position is inside a protected range
        val inProtected = protectedRanges.any { i in it }
        if (text[i] == '\n' && !inProtected) {
            // Check if next char is also \n (already a paragraph break)
            val nextIsNewline = i + 1 < text.length && text[i + 1] == '\n'
            // Check if prev char is also \n (part of existing \n\n)
            val prevIsNewline = i > 0 && text[i - 1] == '\n'
            if (!nextIsNewline && !prevIsNewline) {
                sb.append("\n\n")
            } else {
                sb.append('\n')
            }
        } else {
            sb.append(text[i])
        }
        i++
    }
    return sb.toString()
}

/**
 * Splits raw markdown into alternating [MarkdownSegment.Plain] and
 * [MarkdownSegment.Code] pieces. Code blocks are detected via the
 * ``` lang\n…``` pattern.
 */
private fun parseMarkdownSegments(text: String): List<MarkdownSegment> {
    val codeBlockRegex = Regex("""```(\w*)\n([\s\S]*?)```""")
    val segments = mutableListOf<MarkdownSegment>()
    var lastEnd = 0

    for (match in codeBlockRegex.findAll(text)) {
        if (match.range.first > lastEnd) {
            val plain = text.substring(lastEnd, match.range.first).trim('\n')
            if (plain.isNotEmpty()) {
                segments += MarkdownSegment.Plain(plain)
            }
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2]
        if (code.isNotEmpty()) {
            segments += MarkdownSegment.Code(lang, code)
        }
        lastEnd = match.range.last + 1
    }

    // Remaining text after the last code block
    if (lastEnd < text.length) {
        val plain = text.substring(lastEnd).trim('\n')
        if (plain.isNotEmpty()) {
            segments += MarkdownSegment.Plain(plain)
        }
    }

    return segments
}

// ---------------------------------------------------------------------------
// Code block with header (language label + copy button)
// ---------------------------------------------------------------------------

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            // Header row: language label + copy icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (language.isNotEmpty()) {
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", code.trimEnd()))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = code.trimEnd(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reasoning (Thinking) - collapsed with left accent border
// ---------------------------------------------------------------------------

@Composable
private fun ReasoningPart(
    part: Part.Reasoning,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row {
            // Left accent border
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Surface(
                    onClick = { expanded = !expanded },
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Thinking",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = FontStyle.Italic,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                // Content
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Text(
                        text = part.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tool - delegates to ToolCard
// ---------------------------------------------------------------------------

@Composable
private fun TextPart(
    part: Part.Text,
    modifier: Modifier = Modifier,
    fontSize: String = "medium",
) {
    if (part.text.isBlank()) return

    // Font size scaling factor based on user preference
    val fontScale = when (fontSize) {
        "small" -> 0.85f
        "large" -> 1.15f
        else -> 1.0f // "medium"
    }

    // Preprocess to ensure single \n renders as line break in Markdown
    val processedText = remember(part.text) { ensureMarkdownLineBreaks(part.text) }
    val segments = remember(processedText) { parseMarkdownSegments(processedText) }

    // Custom typography: use smaller, chat-appropriate sizes instead of library defaults
    // (which use displayLarge/displayMedium/displaySmall for h1/h2/h3 — way too big)
    val baseTypography = MaterialTheme.typography
    val chatTypography = markdownTypography(
        h1 = baseTypography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = baseTypography.headlineSmall.fontSize * fontScale,
            lineHeight = baseTypography.headlineSmall.lineHeight * fontScale,
        ),
        h2 = baseTypography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = baseTypography.titleLarge.fontSize * fontScale,
            lineHeight = baseTypography.titleLarge.lineHeight * fontScale,
        ),
        h3 = baseTypography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = baseTypography.titleMedium.fontSize * fontScale,
            lineHeight = baseTypography.titleMedium.lineHeight * fontScale,
        ),
        h4 = baseTypography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = baseTypography.titleSmall.fontSize * fontScale,
            lineHeight = baseTypography.titleSmall.lineHeight * fontScale,
        ),
        h5 = baseTypography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = baseTypography.bodyLarge.fontSize * fontScale,
            lineHeight = baseTypography.bodyLarge.lineHeight * fontScale,
        ),
        h6 = baseTypography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = baseTypography.bodyMedium.fontSize * fontScale,
            lineHeight = baseTypography.bodyMedium.lineHeight * fontScale,
        ),
        text = baseTypography.bodyMedium.copy(
            fontSize = baseTypography.bodyMedium.fontSize * fontScale,
            lineHeight = baseTypography.bodyMedium.lineHeight * fontScale,
        ),
        code = baseTypography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = baseTypography.bodySmall.fontSize * fontScale,
            lineHeight = baseTypography.bodySmall.lineHeight * fontScale,
        ),
        inlineCode = baseTypography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = baseTypography.bodyMedium.fontSize * fontScale,
            lineHeight = baseTypography.bodyMedium.lineHeight * fontScale,
        ),
        quote = baseTypography.bodySmall.copy(
            fontStyle = FontStyle.Italic,
            fontSize = baseTypography.bodySmall.fontSize * fontScale,
            lineHeight = baseTypography.bodySmall.lineHeight * fontScale,
        ),
        paragraph = baseTypography.bodyMedium.copy(
            fontSize = baseTypography.bodyMedium.fontSize * fontScale,
            lineHeight = baseTypography.bodyMedium.lineHeight * fontScale,
        ),
        ordered = baseTypography.bodyMedium.copy(
            fontSize = baseTypography.bodyMedium.fontSize * fontScale,
            lineHeight = baseTypography.bodyMedium.lineHeight * fontScale,
        ),
        bullet = baseTypography.bodyMedium.copy(
            fontSize = baseTypography.bodyMedium.fontSize * fontScale,
            lineHeight = baseTypography.bodyMedium.lineHeight * fontScale,
        ),
        list = baseTypography.bodyMedium.copy(
            fontSize = baseTypography.bodyMedium.fontSize * fontScale,
            lineHeight = baseTypography.bodyMedium.lineHeight * fontScale,
        ),
    )

    // Custom colors: ensure table borders and dividers are visible
    val chatColors = markdownColor(
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
    )

    // If parsing produced no segments (shouldn't happen with non-blank text),
    // fall back to plain Markdown rendering
    if (segments.isEmpty()) {
        Markdown(
            content = part.text,
            modifier = modifier.fillMaxWidth(),
            typography = chatTypography,
            colors = chatColors,
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Plain -> Markdown(
                    content = segment.text,
                    modifier = Modifier.fillMaxWidth(),
                    typography = chatTypography,
                    colors = chatColors,
                )
                is MarkdownSegment.Code -> CodeBlock(
                    language = segment.language,
                    code = segment.code,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// File - image or file card
// ---------------------------------------------------------------------------

@Composable
private fun FilePart(
    part: Part.File,
    modifier: Modifier = Modifier,
) {
    val isImage = part.mimeType?.startsWith("image/") == true

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        if (isImage && part.url.isNotEmpty()) {
            AsyncImage(
                model = part.url,
                contentDescription = part.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = part.name.ifEmpty { "File" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Subtask - sub-agent card
// ---------------------------------------------------------------------------

@Composable
private fun SubtaskPart(
    part: Part.Subtask,
    modifier: Modifier = Modifier,
    onNavigateToSession: (String) -> Unit = {},
    childSessionId: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Subtask",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = part.agent,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                // Navigation arrow: indicates clickable for sub-agent session
                if (childSessionId != null) {
                    IconButton(
                        onClick = { onNavigateToSession(childSessionId) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View sub-agent session",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (part.output.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (part.prompt.isNotEmpty()) {
                Text(
                    text = part.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                if (part.output.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            text = part.output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// StepStart / StepFinish - step group markers
// ---------------------------------------------------------------------------

@Composable
private fun StepStartPart(
    part: Part.StepStart,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = if (part.name.isNotEmpty()) "Step: ${part.name}" else "Step started",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StepFinishPart(
    part: Part.StepFinish,
    modifier: Modifier = Modifier,
) {
    val totalTokens = part.tokens.total

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Step completed",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (totalTokens > 0) {
                Text(
                    text = "${totalTokens} tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (part.cost > 0.0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$${String.format("%.4f", part.cost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Snapshot - badge
// ---------------------------------------------------------------------------

@Composable
private fun SnapshotPart(
    part: Part.Snapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Snapshot",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (part.label.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = part.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Patch - file change list with diff
// ---------------------------------------------------------------------------

@Composable
private fun PatchPart(
    part: Part.Patch,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${part.diffs.size} file${if (part.diffs.size != 1) "s" else ""} changed",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                val totalAdd = part.diffs.sumOf { it.additions }
                val totalDel = part.diffs.sumOf { it.deletions }
                Text(
                    text = buildAnnotatedString {
                        if (totalAdd > 0) {
                            withStyle(SpanStyle(color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)) {
                                append("+$totalAdd")
                            }
                        }
                        if (totalAdd > 0 && totalDel > 0) append(" ")
                        if (totalDel > 0) {
                            withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.Medium)) {
                                append("-$totalDel")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    part.diffs.forEach { diff ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = diff.path,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                if (diff.additions > 0) {
                                    Text(
                                        text = "+${diff.additions}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50),
                                    )
                                }
                                if (diff.deletions > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "-${diff.deletions}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFE53935),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Agent switch marker
// ---------------------------------------------------------------------------

@Composable
private fun AgentPart(
    part: Part.Agent,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Agent",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = part.agent,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )
            if (part.model.modelID.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = part.model.modelID,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Retry - error display
// ---------------------------------------------------------------------------

@Composable
private fun RetryPart(
    part: Part.Retry,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "Retry",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = part.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Compaction - context compression marker
// ---------------------------------------------------------------------------

@Composable
private fun CompactionPart(
    part: Part.Compaction,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Context compressed",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (part.summary.isNotEmpty()) {
                Text(
                    text = part.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
