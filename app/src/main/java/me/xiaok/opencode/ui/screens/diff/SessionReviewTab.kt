package me.xiaok.opencode.ui.screens.diff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.FileDiff

/**
 * Accordion-style diff browser, similar to the OpenCode web frontend's SessionReview.
 *
 * Shows a list of changed files. Each file can be expanded to reveal its inline diff
 * rendered by [FileDiffViewer].
 *
 * @param diffs List of file diffs from the session diff API
 * @param onDismissDiffs Optional callback when user dismisses the diff card
 * @param modifier Modifier for the composable
 */
@Composable
fun SessionReviewTab(
    diffs: List<FileDiff>,
    onDismissDiffs: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (diffs.isEmpty()) {
        EmptyDiffView(modifier = modifier)
        return
    }

    val totalAdd = diffs.sumOf { it.additions }
    val totalDel = diffs.sumOf { it.deletions }
    var expandedFiles by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with summary
        DiffSummaryHeader(
            fileCount = diffs.size,
            totalAdditions = totalAdd,
            totalDeletions = totalDel,
            onExpandAll = {
                expandedFiles = diffs.map { it.path }.toSet()
            },
            onCollapseAll = {
                expandedFiles = emptySet()
            },
            onDismiss = onDismissDiffs,
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )

        // File list with accordion items
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(
                items = diffs,
                key = { it.path },
            ) { diff ->
                FileDiffAccordionItem(
                    fileDiff = diff,
                    isExpanded = diff.path in expandedFiles,
                    onToggle = {
                        expandedFiles = if (diff.path in expandedFiles) {
                            expandedFiles - diff.path
                        } else {
                            expandedFiles + diff.path
                        }
                    },
                )
            }
        }
    }
}

/**
 * Summary header showing "N files changed +X/-Y" with expand/collapse/dismiss actions.
 */
@Composable
private fun DiffSummaryHeader(
    fileCount: Int,
    totalAdditions: Int,
    totalDeletions: Int,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Summary text
            Text(
                text = "$fileCount file${if (fileCount != 1) "s" else ""} changed",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // +/- stats
            DiffChangesIndicator(
                additions = totalAdditions,
                deletions = totalDeletions,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Expand/collapse actions
            TextButton(onClick = onExpandAll) {
                Text("Expand all", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onCollapseAll) {
                Text("Collapse all", style = MaterialTheme.typography.labelSmall)
            }

            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * A single accordion item: file header (clickable) + expandable diff content.
 */
@Composable
private fun FileDiffAccordionItem(
    fileDiff: FileDiff,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        // File header row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            color = if (isExpanded) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                Color.Transparent
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // File icon
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.width(8.dp))

                // File path info
                Column(modifier = Modifier.weight(1f)) {
                    val fileName = fileDiff.path.substringAfterLast('/')
                    val dirPath = fileDiff.path.substringBeforeLast('/', "")

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dirPath.isNotEmpty()) {
                            Text(
                                text = "$dirPath/",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }

                // Status badge
                FileStatusBadge(status = fileDiff.status)

                Spacer(modifier = Modifier.width(8.dp))

                // Changes bars
                DiffChangesBars(
                    additions = fileDiff.additions,
                    deletions = fileDiff.deletions,
                )

                Spacer(modifier = Modifier.width(4.dp))

                // +/- numbers
                DiffChangesIndicator(
                    additions = fileDiff.additions,
                    deletions = fileDiff.deletions,
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Expand chevron
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = if (isExpanded) 90f else 0f
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Expandable diff content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
            ) {
                FileDiffViewer(
                    fileDiff = fileDiff,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        )
    }
}

/**
 * Empty state when no diffs are available.
 */
@Composable
private fun EmptyDiffView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No changes yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Changes will appear here when the AI modifies files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
