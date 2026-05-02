package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.Part
import java.text.SimpleDateFormat
import java.util.Locale

// ---------------------------------------------------------------------------
// Turn Bubble — renders a full ChatTurn (user message + grouped assistant parts)
// ---------------------------------------------------------------------------

@Composable
internal fun TurnBubble(
    turn: ChatTurn,
    onCopyMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onForkSession: (String) -> Unit = {},
    onRevertSession: (String) -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    onNavigateToToolDetail: (String) -> Unit = {},
    fontSize: String = "medium",
    isLastTurn: Boolean = false,
    isActiveSession: Boolean = false,
) {
    val grouped = turn.groupedParts
    val partLookup = turn.partLookup
    val isCompactionOnly = turn.isCompactionOnly
    val userParts = turn.userParts
    val isSyntheticUser = turn.isSyntheticUser
    val childSessionIds = turn.childSessionIdLookup

    val isLatestActiveReasoning = if (isLastTurn && isActiveSession) {
        turn.isActivelyReasoning
    } else false

    // Dropdown state
    var showMenu by remember { mutableStateOf(false) }

    // Check if turn has a real user message id for menu actions
    val hasRealUserMessage = turn.userMessage.id.isNotEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        // 1. User message (or compaction divider)
        if (isCompactionOnly) {
            userParts.filterIsInstance<Part.Compaction>().forEach { part ->
                PartRenderer(part = part)
            }
        } else if (!isSyntheticUser) {
            UserMessageBubble(
                message = turn.userMessage,
                parts = userParts,
            )
            val createdMs = turn.userMessage.time.created
            val userModel = turn.userMessage.info.model
            val hasTime = createdMs > 0
            val hasModel = userModel != null && userModel.modelID.isNotBlank()
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasTime) {
                    Text(
                        text = remember(createdMs) { formatMessageTime(createdMs) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                if (hasTime && hasModel) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                }
                if (hasModel) {
                    Text(
                        text = buildString {
                            append(userModel.modelID)
                            if (userModel.providerID.isNotBlank()) append(" · ${userModel.providerID}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Message options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // 2. Error indicator from last assistant message
        val lastAssistant = turn.assistantMessages.lastOrNull()
        val errorInfo = lastAssistant?.info?.error
        if (errorInfo != null && errorInfo.message.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorInfo.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 3. Assistant parts (cross-message grouped)
        for (group in grouped) {
            when (group) {
                is TurnPartGroup.Single -> {
                    val part = partLookup[group.ref] ?: continue
                    PartRenderer(
                        part = part,
                        onNavigateToSession = onNavigateToSession,
                        childSessionIds = childSessionIds,
                        fontSize = fontSize,
                        onNavigateToToolDetail = onNavigateToToolDetail,
                        isLatestActiveReasoning = isLatestActiveReasoning,
                    )
                }
                is TurnPartGroup.ContextGroup -> {
                    val tools = group.refs.mapNotNull { ref ->
                        partLookup[ref] as? Part.Tool
                    }
                    if (tools.isNotEmpty()) {
                        ContextToolGroup(
                            tools = tools,
                            onNavigateToToolDetail = onNavigateToToolDetail,
                        )
                    }
                }
            }
        }

        val completedAt = lastAssistant?.time?.completed
        val createdAt = lastAssistant?.time?.created
        if (isLastTurn && completedAt != null && completedAt > 0L && createdAt != null && createdAt > 0L) {
            val durationSec = (completedAt - createdAt) / 1000
            if (durationSec > 0) {
                Text(
                    text = remember(durationSec) { "Completed in ${formatDuration(durationSec)}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }

        }

        // Dropdown menu — anchored to the ⋮ button
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            // Copy: merge all Text parts (user + assistant)
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = {
                    showMenu = false
                    onCopyMessage(extractTurnCopyText(turn))
                },
            )
            // Delete: only if real user message
            if (hasRealUserMessage) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDeleteMessage(turn.userMessage.id)
                    },
                )
            }
            // Fork: only if real user message
            if (hasRealUserMessage) {
                DropdownMenuItem(
                    text = { Text("Fork from here") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onForkSession(turn.userMessage.id)
                    },
                )
            }
            // Revert: only if real user message
            if (hasRealUserMessage) {
                DropdownMenuItem(
                    text = { Text("Revert to here") },
                    onClick = {
                        showMenu = false
                        onRevertSession(turn.userMessage.id)
                    },
                )
            }
        }
    }
}

private fun formatMessageTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(epochMs)
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60
    val s = seconds % 60
    return if (s == 0L) "${m}m" else "${m}m ${s}s"
}
