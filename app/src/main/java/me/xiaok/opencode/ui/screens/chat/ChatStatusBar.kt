package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.ui.components.common.PulsingDot
import me.xiaok.opencode.ui.components.common.formatTokenCount

// ---------------------------------------------------------------------------
// Status row — working state pulse + context usage + turns + tokens + cost
// ---------------------------------------------------------------------------

@Composable
internal fun StatusRow(
    status: SessionStatus,
    contextUsagePercent: Int,
    totalTokens: Long,
    totalCost: Double,
    conversationTurns: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status indicator (only show when busy/retrying)
        when (status) {
            is SessionStatus.Busy -> {
                PulsingDot(color = Color(0xFF4CAF50), size = 6.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Working",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color(0xFF4CAF50),
                )
            }
            is SessionStatus.Retry -> {
                PulsingDot(color = Color(0xFFFFA000), size = 6.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (status.attempt > 0) "Retrying (${status.attempt})" else "Retrying",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color(0xFFFFA000),
                )
            }
            is SessionStatus.Idle -> { /* Don't show idle status */ }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Conversation turns
        if (conversationTurns > 0) {
            Text(
                text = "$conversationTurns turns",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Token count
        if (totalTokens > 0) {
            Text(
                text = formatTokenCount(totalTokens),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totalCost > 0.0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$${String.format("%.2f", totalCost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Context usage — battery indicator (remaining = 100 - used)
        if (contextUsagePercent > 0) {
            val remaining = 100 - contextUsagePercent
            val batteryColor = when {
                remaining <= 10 -> MaterialTheme.colorScheme.error
                remaining <= 30 -> Color(0xFFFFA000)
                else -> Color(0xFF4CAF50)
            }
            ContextBattery(
                remaining = remaining,
                color = batteryColor,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Context battery indicator — battery icon showing remaining context
// ---------------------------------------------------------------------------

@Composable
private fun ContextBattery(
    remaining: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // Battery body
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                .padding(1.5.dp),
        ) {
            // Fill bar — width proportional to remaining
            Box(
                modifier = Modifier
                    .fillMaxWidth(remaining / 100f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        // Battery tip (positive terminal nub)
        Box(
            modifier = Modifier
                .size(width = 2.dp, height = 6.dp)
                .clip(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
        )
        Spacer(modifier = Modifier.width(4.dp))
    }
}

// ---------------------------------------------------------------------------
// @ Mention detection — matches cursor-relative @ pattern anywhere in text
// ---------------------------------------------------------------------------

/**
 * Result of detecting an @ mention trigger in the text.
 * @param startIndex The index of the '@' character in the text
 * @param query The search query after the '@' (may be empty)
 */
data class AtDetection(
    val startIndex: Int,
    val query: String,
)

/**
 * Detects an @ mention trigger at the start of the text.
 * Only matches when the text starts with '@' (optionally preceded by whitespace-trimmed input).
 * Returns null if '@' is not the first character or if a space follows the '@query'.
 */
fun detectAtMention(text: String, cursorPosition: Int): AtDetection? {
    if (cursorPosition <= 0 || cursorPosition > text.length) return null
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("@")) return null
    // Compute the offset caused by trimStart
    val textBeforeCursor = text.substring(0, cursorPosition)
    val match = Regex("""@(\S*)$""").find(textBeforeCursor) ?: return null
    return AtDetection(
        startIndex = match.range.first,
        query = match.groupValues[1],
    )
}
