package me.xiaok.opencode.ui.screens.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ---------------------------------------------------------------------------
// Diff line types
// ---------------------------------------------------------------------------

private enum class DiffLineType {
    CONTEXT,
    ADDITION,
    DELETION,
    HUNK_HEADER,
    FILE_HEADER,
}

private data class DiffLine(
    val content: String,
    val type: DiffLineType,
    val oldLineNum: Int?,
    val newLineNum: Int?,
)

// ---------------------------------------------------------------------------
// Diff parsing
// ---------------------------------------------------------------------------

private val HUNK_HEADER_REGEX = Regex("^@@\\s+-(\\d+)(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@")

private fun parseDiff(diffText: String): List<DiffLine> {
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
                result.add(DiffLine(line, DiffLineType.HUNK_HEADER, null, null))
            }
            line.startsWith("---") -> {
                result.add(DiffLine(line, DiffLineType.FILE_HEADER, null, null))
            }
            line.startsWith("+++") -> {
                result.add(DiffLine(line, DiffLineType.FILE_HEADER, null, null))
            }
            line.startsWith("+") -> {
                result.add(DiffLine(line, DiffLineType.ADDITION, null, newLine))
                newLine++
            }
            line.startsWith("-") -> {
                result.add(DiffLine(line, DiffLineType.DELETION, oldLine, null))
                oldLine++
            }
            else -> {
                result.add(DiffLine(line, DiffLineType.CONTEXT, oldLine, newLine))
                if (oldLine > 0) oldLine++
                if (newLine > 0) newLine++
            }
        }
    }
    return result
}

// ---------------------------------------------------------------------------
// Colors
// ---------------------------------------------------------------------------

private val AdditionTextColor = Color(0xFF2E7D32)
private val AdditionBgColor = Color(0xFF4CAF50).copy(alpha = 0.08f)
private val DeletionTextColor = Color(0xFFC62828)
private val DeletionBgColor = Color(0xFFE53935).copy(alpha = 0.08f)

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless DiffViewerScreen
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
// Stateless DiffViewerScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    diffText: String,
    title: String?,
    onNavigateBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val diffLines = remember(diffText) { parseDiff(diffText) }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

// ---------------------------------------------------------------------------
// Content renderer
// ---------------------------------------------------------------------------

@Composable
private fun DiffViewerContent(
    diffLines: List<DiffLine>,
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
    val lineNumberWidth = 48.dp
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    LazyColumn(
        modifier = modifier,
    ) {
        items(
            items = diffLines,
            key = { index -> index },
        ) { diffLine ->
            DiffLineRow(
                diffLine = diffLine,
                monospaceStyle = monospaceStyle,
                lineNumberStyle = lineNumberStyle,
                lineNumberWidth = lineNumberWidth,
                dividerColor = dividerColor,
            )
        }
    }
}

@Composable
private fun DiffLineRow(
    diffLine: DiffLine,
    monospaceStyle: androidx.compose.ui.text.TextStyle,
    lineNumberStyle: androidx.compose.ui.text.TextStyle,
    lineNumberWidth: androidx.compose.ui.unit.Dp,
    dividerColor: Color,
) {
    val bgColor = when (diffLine.type) {
        DiffLineType.ADDITION -> AdditionBgColor
        DiffLineType.DELETION -> DeletionBgColor
        DiffLineType.HUNK_HEADER -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        DiffLineType.FILE_HEADER -> MaterialTheme.colorScheme.surfaceContainerHigh
        DiffLineType.CONTEXT -> Color.Transparent
    }

    val textColor = when (diffLine.type) {
        DiffLineType.ADDITION -> AdditionTextColor
        DiffLineType.DELETION -> DeletionTextColor
        DiffLineType.HUNK_HEADER -> MaterialTheme.colorScheme.onSurfaceVariant
        DiffLineType.FILE_HEADER -> MaterialTheme.colorScheme.onSurface
        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface
    }

    val lineContentStyle = when (diffLine.type) {
        DiffLineType.HUNK_HEADER -> monospaceStyle.copy(
            fontWeight = FontWeight.Medium,
        )
        DiffLineType.FILE_HEADER -> monospaceStyle.copy(
            fontWeight = FontWeight.Bold,
        )
        else -> monospaceStyle
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .background(dividerColor),
        )

        // Content with horizontal scroll
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = diffLine.content,
                style = lineContentStyle,
                color = textColor,
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 2.dp,
                ),
            )
        }
    }
}
