package me.xiaok.opencode.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.xiaok.opencode.domain.model.*

// ---------------------------------------------------------------------------
// File - image or file card
// ---------------------------------------------------------------------------

@Composable
internal fun FilePart(
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
internal fun SubtaskPart(
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
// Snapshot - badge
// ---------------------------------------------------------------------------

@Composable
internal fun SnapshotPart(
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
internal fun PatchPart(
    part: Part.Patch,
    modifier: Modifier = Modifier,
) {
    if (part.diffs.isEmpty()) return

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
