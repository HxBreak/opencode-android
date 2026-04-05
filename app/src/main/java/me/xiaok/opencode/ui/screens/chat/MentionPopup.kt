package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.AgentConfig
import me.xiaok.opencode.domain.model.MentionItem

/**
 * Data class representing a selectable option in the @ mention popup.
 */
data class MentionOption(
    val type: String,            // "agent" or "file"
    val display: String,         // Display text for the option
    val subtitle: String = "",   // Optional second line (agent description or file dir)
    val agentName: String? = null,
    val filePath: String? = null,
)

/**
 * Popup content for @ mention suggestions.
 * Displays agents first, then file results, matching the web frontend's grouping order.
 *
 * @param agents Available agents (already filtered by query if needed)
 * @param files File search results (paths)
 * @param query Current search query (for highlighting matches)
 * @param onSelect Callback when user selects an option, receives the corresponding MentionItem
 */
@Composable
fun MentionPopupContent(
    agents: List<AgentConfig>,
    files: List<String>,
    query: String,
    onSelect: (MentionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
    ) {
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
            // --- Agent group ---
            if (agents.isNotEmpty()) {
                item {
                    GroupHeader("Agents")
                }
                items(count = agents.size, key = { "agent-${agents[it].name}" }) { index ->
                    val agent = agents[index]
                    AgentOptionRow(
                        agent = agent,
                        query = query,
                        onClick = {
                            onSelect(
                                MentionItem.AgentMention(
                                    name = agent.name,
                                    displayText = "@${agent.name}",
                                    start = 0,
                                    end = 0,
                                )
                            )
                        },
                    )
                }
            }

            // Divider between groups
            if (agents.isNotEmpty() && files.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // --- File group ---
            if (files.isNotEmpty()) {
                item {
                    GroupHeader("Files")
                }
                items(count = files.size, key = { "file-${files[it]}" }) { index ->
                    val filePath = files[index]
                    FileOptionRow(
                        filePath = filePath,
                        query = query,
                        onClick = {
                            onSelect(
                                MentionItem.FileMention(
                                    path = filePath,
                                    displayText = "@$filePath",
                                    start = 0,
                                    end = 0,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun AgentOptionRow(
    agent: AgentConfig,
    query: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    append("@")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(highlightMatch(agent.name, query))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (agent.description.isNotBlank()) {
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FileOptionRow(
    filePath: String,
    query: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // File icon using first letter of extension
        val ext = filePath.substringAfterLast('.', "").take(3).uppercase().ifEmpty { "F" }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = ext,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.7f,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = highlightMatch(filePath, query),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Returns the text with the query portion highlighted.
 * For now, returns the original text — highlighting is visual-only
 * and will be handled via AnnotatedString in a follow-up if needed.
 */
private fun highlightMatch(text: String, query: String): String = text
