package me.xiaok.opencode.ui.screens.diff

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xiaok.opencode.domain.model.FileDiff

// ---------------------------------------------------------------------------
// Colors
// ---------------------------------------------------------------------------

private val AdditionTextColor = Color(0xFF2E7D32)
private val AdditionBgColor = Color(0xFF4CAF50).copy(alpha = 0.08f)
private val AdditionInlineBgColor = Color(0xFF4CAF50).copy(alpha = 0.20f)
private val DeletionTextColor = Color(0xFFC62828)
private val DeletionBgColor = Color(0xFFE53935).copy(alpha = 0.08f)
private val DeletionInlineBgColor = Color(0xFFE53935).copy(alpha = 0.20f)
private val HunkHeaderBgColor = Color(0xFF2196F3).copy(alpha = 0.06f)
private val HunkHeaderTextColor = Color(0xFF1565C0)

// ---------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerRoute(
    onNavigateBack: () -> Unit,
    viewModel: DiffViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DiffViewerScreen(
        diffText = uiState.diffText,
        title = uiState.title,
        onNavigateBack = onNavigateBack,
    )
}

// ---------------------------------------------------------------------------
// Standalone DiffViewerScreen (backward compat — takes raw diff text)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    diffText: String,
    title: String?,
    onNavigateBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val diffLines = remember(diffText) { parseDiffLegacy(diffText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title ?: "Diff",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        DiffViewerContent(
            diffLines = diffLines,
            inlineDiffs = emptyMap(),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

// ---------------------------------------------------------------------------
// FileDiffViewer — renders diff from FileDiff (before/after content)
// Used by SessionReviewTab
// ---------------------------------------------------------------------------

@Composable
fun FileDiffViewer(
    fileDiff: FileDiff,
    modifier: Modifier = Modifier,
) {
    var diffResult by remember {
        mutableStateOf<Pair<List<DiffLine>, Map<Int, Pair<List<InlineSpan>, List<InlineSpan>>>>?>(null)
    }

    LaunchedEffect(fileDiff.before, fileDiff.after) {
        withContext(Dispatchers.Default) {
            val lines = DiffEngine.computeDiff(fileDiff.before, fileDiff.after, fileDiff.path)
            val inline = DiffEngine.computeInlineDiffs(lines)
            diffResult = lines to inline
        }
    }

    val result = diffResult
    if (result == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        return
    }

    val (diffLines, inlineDiffs) = result

    if (diffLines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No changes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    DiffViewerContent(
        diffLines = diffLines,
        inlineDiffs = inlineDiffs,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Shared content renderer
// ---------------------------------------------------------------------------

@Composable
private fun DiffViewerContent(
    diffLines: List<DiffLine>,
    inlineDiffs: Map<Int, Pair<List<InlineSpan>, List<InlineSpan>>>,
    modifier: Modifier = Modifier,
) {
    if (diffLines.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No diff content",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val monospaceStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
    )
    val lineNumberStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.End,
    )
    val lineNumberWidth = 44.dp
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Column(modifier = modifier) {
        diffLines.forEachIndexed { index, diffLine ->
            val inlineSpans = inlineDiffs[index]
            DiffLineRow(
                diffLine = diffLine,
                deletionInlineSpans = inlineSpans?.first ?: emptyList(),
                additionInlineSpans = inlineSpans?.second ?: emptyList(),
                monospaceStyle = monospaceStyle,
                lineNumberStyle = lineNumberStyle,
                lineNumberWidth = lineNumberWidth,
                dividerColor = dividerColor,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Single diff line row
// ---------------------------------------------------------------------------

@Composable
private fun DiffLineRow(
    diffLine: DiffLine,
    deletionInlineSpans: List<InlineSpan>,
    additionInlineSpans: List<InlineSpan>,
    monospaceStyle: androidx.compose.ui.text.TextStyle,
    lineNumberStyle: androidx.compose.ui.text.TextStyle,
    lineNumberWidth: androidx.compose.ui.unit.Dp,
    dividerColor: Color,
) {
    val bgColor = when (diffLine.type) {
        DiffLine.LineType.ADDITION -> AdditionBgColor
        DiffLine.LineType.DELETION -> DeletionBgColor
        DiffLine.LineType.HUNK_HEADER -> HunkHeaderBgColor
        DiffLine.LineType.CONTEXT -> Color.Transparent
    }

    val textColor = when (diffLine.type) {
        DiffLine.LineType.ADDITION -> AdditionTextColor
        DiffLine.LineType.DELETION -> DeletionTextColor
        DiffLine.LineType.HUNK_HEADER -> HunkHeaderTextColor
        DiffLine.LineType.CONTEXT -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Old line number column
        Box(
            modifier = Modifier.width(lineNumberWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (diffLine.oldLineNum != null) {
                Text(
                    text = diffLine.oldLineNum.toString(),
                    style = lineNumberStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(dividerColor),
        )

        // New line number column
        Box(
            modifier = Modifier.width(lineNumberWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (diffLine.newLineNum != null) {
                Text(
                    text = diffLine.newLineNum.toString(),
                    style = lineNumberStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(dividerColor),
        )

        // Content with horizontal scroll and optional inline highlighting
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            val contentText = buildContentWithInlineHighlight(
                diffLine = diffLine,
                textColor = textColor,
                deletionInlineSpans = deletionInlineSpans,
                additionInlineSpans = additionInlineSpans,
            )
            Text(
                text = contentText,
                style = monospaceStyle,
                color = textColor,
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 2.dp,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Inline highlighting builder
// ---------------------------------------------------------------------------

private fun buildContentWithInlineHighlight(
    diffLine: DiffLine,
    textColor: Color,
    deletionInlineSpans: List<InlineSpan>,
    additionInlineSpans: List<InlineSpan>,
): AnnotatedString {
    val spans = when (diffLine.type) {
        DiffLine.LineType.DELETION -> deletionInlineSpans
        DiffLine.LineType.ADDITION -> additionInlineSpans
        else -> emptyList()
    }

    if (spans.isEmpty()) {
        return AnnotatedString(diffLine.content)
    }

    val inlineBgColor = when (diffLine.type) {
        DiffLine.LineType.DELETION -> DeletionInlineBgColor
        DiffLine.LineType.ADDITION -> AdditionInlineBgColor
        else -> Color.Transparent
    }

    return buildAnnotatedString {
        var lastEnd = 0
        for (span in spans.sortedBy { it.start }) {
            // Text before this span
            if (span.start > lastEnd) {
                append(diffLine.content.substring(lastEnd, minOf(span.start, diffLine.content.length)))
            }
            // Highlighted span
            val highlightEnd = minOf(span.end, diffLine.content.length)
            if (highlightEnd > span.start) {
                withStyle(
                    SpanStyle(
                        background = inlineBgColor,
                        fontWeight = FontWeight.Medium,
                    )
                ) {
                    append(diffLine.content.substring(span.start, highlightEnd))
                }
            }
            lastEnd = highlightEnd
        }
        // Remaining text after last span
        if (lastEnd < diffLine.content.length) {
            append(diffLine.content.substring(lastEnd))
        }
    }
}

// ---------------------------------------------------------------------------
// Legacy diff parser (for backward compat with raw diff text input)
// ---------------------------------------------------------------------------

private data class LegacyDiffLine(
    val content: String,
    val type: DiffLine.LineType,
    val oldLineNum: Int?,
    val newLineNum: Int?,
)

private val HUNK_HEADER_REGEX = Regex("^@@\\s+-(\\d+)(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@")

private fun parseDiffLegacy(diffText: String): List<DiffLine> {
    val lines = diffText.lines()
    val result = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0

    for (line in lines) {
        when {
            line.startsWith("@@") -> {
                val match = HUNK_HEADER_REGEX.find(line)
                if (match != null) {
                    oldLine = match.groupValues[1].toIntOrNull() ?: 0
                    newLine = match.groupValues[2].toIntOrNull() ?: 0
                }
                result.add(
                    DiffLine(
                        content = line,
                        type = DiffLine.LineType.HUNK_HEADER,
                        oldLineNum = null,
                        newLineNum = null,
                    )
                )
            }
            line.startsWith("---") -> continue
            line.startsWith("+++") -> continue
            line.startsWith("+") -> {
                result.add(
                    DiffLine(
                        content = line.removePrefix("+"),
                        type = DiffLine.LineType.ADDITION,
                        oldLineNum = null,
                        newLineNum = newLine,
                    )
                )
                newLine++
            }
            line.startsWith("-") -> {
                result.add(
                    DiffLine(
                        content = line.removePrefix("-"),
                        type = DiffLine.LineType.DELETION,
                        oldLineNum = oldLine,
                        newLineNum = null,
                    )
                )
                oldLine++
            }
            else -> {
                result.add(
                    DiffLine(
                        content = line.removePrefix(" "),
                        type = DiffLine.LineType.CONTEXT,
                        oldLineNum = oldLine,
                        newLineNum = newLine,
                    )
                )
                if (oldLine > 0) oldLine++
                if (newLine > 0) newLine++
            }
        }
    }
    return result
}

// ---------------------------------------------------------------------------
// DiffChanges indicator — shows +N/-N for a file
// ---------------------------------------------------------------------------

@Composable
fun DiffChangesIndicator(
    additions: Int,
    deletions: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (additions > 0) {
            Text(
                text = "+$additions",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                ),
                color = AdditionTextColor,
            )
        }
        if (deletions > 0) {
            Text(
                text = "-$deletions",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                ),
                color = DeletionTextColor,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DiffChanges block bars — 5-block visual like GitHub
// ---------------------------------------------------------------------------

@Composable
fun DiffChangesBars(
    additions: Int,
    deletions: Int,
    modifier: Modifier = Modifier,
) {
    val total = additions + deletions
    if (total == 0) return

    val totalBlocks = 5
    val (addBlocks, delBlocks) = remember(additions, deletions) {
        if (total == 0) return@remember 0 to 0

        var added = if (additions > 0) {
            val raw = (additions.toFloat() / total * totalBlocks)
            maxOf(1, raw.toInt())
        } else 0

        var deleted = if (deletions > 0) {
            val raw = (deletions.toFloat() / total * totalBlocks)
            maxOf(1, raw.toInt())
        } else 0

        // Cap to total blocks
        val allocated = added + deleted
        if (allocated > totalBlocks) {
            if (added >= deleted) added = totalBlocks - deleted
            else deleted = totalBlocks - added
        }

        added to deleted
    }
    val neutralBlocks = totalBlocks - addBlocks - delBlocks

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(addBlocks) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AdditionTextColor),
            )
        }
        repeat(delBlocks) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(DeletionTextColor),
            )
        }
        repeat(neutralBlocks) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// File status badge
// ---------------------------------------------------------------------------

@Composable
fun FileStatusBadge(
    status: String?,
    modifier: Modifier = Modifier,
) {
    val (label, bgColor, contentColor) = when (status) {
        "added" -> Triple("A", Color(0xFF4CAF50), Color.White)
        "deleted" -> Triple("D", Color(0xFFE53935), Color.White)
        "modified" -> Triple("M", Color(0xFFFF9800), Color.White)
        else -> return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
            ),
            color = contentColor,
        )
    }
}
