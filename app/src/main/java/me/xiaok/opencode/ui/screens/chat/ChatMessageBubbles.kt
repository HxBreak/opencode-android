package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.Part

// ---------------------------------------------------------------------------
// Message Bubble
// ---------------------------------------------------------------------------

@Composable
internal fun MessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    when {
        message.isUser -> {
            val allParts = parts.ifEmpty { message.parts }
            val hasCompactionOnly = allParts.isNotEmpty() && allParts.all { it is Part.Compaction }
            if (hasCompactionOnly) {
                // Compaction-only user message → render as divider (matches Web UI behavior)
                allParts.filterIsInstance<Part.Compaction>().forEach { part ->
                    PartRenderer(part = part)
                }
            } else {
                UserMessageBubble(message = message, parts = parts, onMenuClick = onMenuClick)
            }
        }
        message.isAssistant -> AssistantMessageBubble(
            message = message,
            parts = parts,
            onMenuClick = onMenuClick,
            onNavigateToSession = onNavigateToSession,
            childSessionIds = childSessionIds,
            fontSize = fontSize,
            onQuestionClick = onQuestionClick,
            onNavigateToToolDetail = onNavigateToToolDetail,
            isLatestActiveReasoning = isLatestActiveReasoning,
        )
    }
}

@Composable
internal fun UserMessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp,
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onMenuClick() }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Prefer SSE-streamed parts (uiState.parts), fallback to message.parts
                val allParts = parts.ifEmpty { message.parts }
                allParts.forEach { part ->
                    when (part) {
                        is Part.Text -> {
                            if (part.text.isNotEmpty()) {
                                Text(
                                    text = part.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        is Part.File -> {
                            // Render file mention as a compact chip in user message
                            Surface(
                                color = Color(0xFF2196F3).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "📄",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = part.name.ifEmpty { part.url.removePrefix("file://") },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        is Part.Agent -> {
                            // Render agent mention as a compact chip in user message
                            Surface(
                                color = Color(0xFF9C27B0).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "🤖",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = part.agent,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        else -> { /* Skip other part types in user bubble */ }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssistantMessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onMenuClick() }
                )
            }
    ) {
        // Error indicator
        val errorInfo = message.info.error
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

        // Parts — group consecutive context tools
        val groupedParts = remember(parts) { groupParts(parts) }
        groupedParts.forEach { grouped ->
            GroupedPartRenderer(
                grouped = grouped,
                onNavigateToSession = onNavigateToSession,
                childSessionIds = childSessionIds,
                fontSize = fontSize,
                onQuestionClick = onQuestionClick,
                onNavigateToToolDetail = onNavigateToToolDetail,
                isLatestActiveReasoning = isLatestActiveReasoning,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
internal fun ChatEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Send a message to begin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
