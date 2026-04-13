package me.xiaok.opencode.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.ui.screens.tooldetail.CachedToolData
import me.xiaok.opencode.ui.screens.tooldetail.ToolDetailCache

// ---------------------------------------------------------------------------
// ContextToolGroup — collapsed card for consecutive read/glob/grep/list/find
// Mimics web's ContextToolGroup: "Exploring / Explored" + "3 reads · 2 searches · 1 list"
// ---------------------------------------------------------------------------

/** Classify tools for the summary counter, matching web's contextToolSummary(). */
private data class ContextSummary(val read: Int, val search: Int, val list: Int)

private fun contextToolSummary(tools: List<Part.Tool>): ContextSummary {
    var read = 0
    var search = 0
    var list = 0
    for (tool in tools) {
        when (tool.tool) {
            "read" -> read++
            "glob", "grep", "find" -> search++
            "list" -> list++
        }
    }
    return ContextSummary(read, search, list)
}

/** Build the summary text like "3 reads · 2 searches · 1 list", matching web's AnimatedCountList. */
private fun buildSummaryText(summary: ContextSummary): String {
    val parts = mutableListOf<String>()
    if (summary.read > 0) {
        parts += if (summary.read == 1) "1 read" else "${summary.read} reads"
    }
    if (summary.search > 0) {
        parts += if (summary.search == 1) "1 search" else "${summary.search} searches"
    }
    if (summary.list > 0) {
        parts += if (summary.list == 1) "1 list" else "${summary.list} lists"
    }
    return parts.joinToString(" \u00B7 ")  // middle dot separator
}

@Composable
internal fun ContextToolGroup(
    tools: List<Part.Tool>,
    modifier: Modifier = Modifier,
    onNavigateToToolDetail: (String) -> Unit = {},
) {
    if (tools.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val isAnyRunning = tools.any { it.state.isRunning || it.state.isPending }
    val summary = remember(tools.map { it.tool }) { contextToolSummary(tools) }
    val summaryText = remember(summary) { buildSummaryText(summary) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            // Header: "Exploring…" / "Explored" + summary counts
            Surface(
                onClick = {
                    if (!isAnyRunning) expanded = !expanded
                },
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Status dot (pulsing when running)
                    val dotColor = if (isAnyRunning) {
                        val infiniteTransition = rememberInfiniteTransition(label = "ctxGroupDot")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "ctxGroupDotAlpha",
                        )
                        Color(0xFF42A5F5).copy(alpha = alpha)
                    } else {
                        Color(0xFF66BB6A)
                    }
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = dotColor,
                    ) {}

                    Spacer(modifier = Modifier.width(8.dp))

                    // Title: "Exploring…" or "Explored" — matching web's ToolStatusTitle
                    Text(
                        text = if (isAnyRunning) "Exploring\u2026" else "Explored",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Summary: "3 reads · 2 searches · 1 list"
                    if (summaryText.isNotEmpty()) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Running pill
                    if (isAnyRunning) {
                        Surface(
                            color = Color(0xFF42A5F5).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "Running",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = FontStyle.Italic,
                                ),
                                color = Color(0xFF42A5F5),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }

                    // Expand/collapse arrow
                    if (!isAnyRunning) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            // Expanded: lightweight rows (not full ToolCard), matching web's contextToolTrigger
            if (!isAnyRunning) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        tools.forEach { part ->
                            val typeInfo = getToolTypeInfo(part.tool)
                            val subtitle = remember(part.state.input) {
                                extractSubtitle(part.tool, part.state.input)
                            }
                            val argTags = remember(part.state.input) {
                                extractArgTags(part.tool, part.state.input)
                            }
                            val (statusColor, _) = toolStatusInfo(part.state)

                            // Cache for detail navigation
                            ToolDetailCache.put(part.id, CachedToolData(
                                toolName = part.tool,
                                state = part.state,
                                childSessionId = part.state.childSessionId,
                            ))

                            Surface(
                                onClick = { onNavigateToToolDetail(part.id) },
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Mini status dot
                                    Surface(
                                        modifier = Modifier.size(6.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = statusColor,
                                    ) {}

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Emoji
                                    Text(
                                        text = typeInfo.emoji,
                                        style = MaterialTheme.typography.labelSmall,
                                    )

                                    Spacer(modifier = Modifier.width(3.dp))

                                    // Tool name
                                    Text(
                                        text = typeInfo.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    // Subtitle (directory or filename)
                                    if (subtitle.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false),
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    // Arg tags
                                    argTags.forEach { (key, value) ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(2.dp),
                                        ) {
                                            Text(
                                                text = "$key=$value",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                    }

                                    // Chevron
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
