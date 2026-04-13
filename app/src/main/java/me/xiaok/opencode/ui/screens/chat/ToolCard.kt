package me.xiaok.opencode.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.ToolState
import me.xiaok.opencode.ui.components.common.PulsingDot
import me.xiaok.opencode.ui.components.common.StatusDot

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
                    if (isRunning) {
                        PulsingDot(color = statusColor, size = 8.dp)
                    } else {
                        StatusDot(color = statusColor, size = 8.dp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = typeInfo.emoji,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = typeInfo.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

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

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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

                    val output = state.output
                    if (output.isNotEmpty()) {
                        when (toolName) {
                            "bash" -> BashOutputContent(output)
                            "edit", "write", "apply_patch" -> DiffOutputContent(output)
                            "websearch", "codesearch" -> LinksOutputContent(output)
                            else -> GenericOutputContent(output)
                        }
                    }

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
