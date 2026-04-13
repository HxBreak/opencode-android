package me.xiaok.opencode.ui.screens.chat

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.ui.components.common.PulsingDot
import me.xiaok.opencode.ui.theme.StatusConnected
import me.xiaok.opencode.ui.theme.StatusError

@Composable
fun HoverSentinel(
    modifier: Modifier = Modifier,
    childSessions: List<ChildSessionInfo>,
    onNavigateToSession: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val allDone = childSessions.isNotEmpty() && childSessions.none {
        it.status is SessionStatus.Busy || it.status is SessionStatus.Retry
    }
    var isExpanded by remember { mutableStateOf(false) }
    var pendingDismiss by remember { mutableStateOf(false) }

    // Auto-dismiss: only keyed on allDone, not isExpanded.
    // This prevents user clicks (isExpanded changes) from restarting the effect.
    LaunchedEffect(allDone) {
        if (!allDone) {
            pendingDismiss = false
            return@LaunchedEffect
        }
        delay(1500)
        if (isExpanded) {
            // User is viewing — mark for dismiss when they collapse
            pendingDismiss = true
        } else {
            onDismiss()
        }
    }

    // When user collapses and there's a pending dismiss, fire it
    LaunchedEffect(isExpanded, pendingDismiss) {
        if (!isExpanded && pendingDismiss) {
            onDismiss()
        }
    }

    Column(modifier = modifier) {
        SentinelCollapsed(
            childSessions = childSessions,
            isExpanded = isExpanded,
            onClick = { isExpanded = !isExpanded },
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            SentinelExpanded(
                childSessions = childSessions,
                onNavigateToSession = onNavigateToSession,
            )
        }
    }
}

@Composable
private fun SentinelCollapsed(
    childSessions: List<ChildSessionInfo>,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val busyCount = childSessions.count { it.status is SessionStatus.Busy }
    val retryCount = childSessions.count { it.status is SessionStatus.Retry }
    val idleCount = childSessions.count { it.status is SessionStatus.Idle }
    val runningCount = busyCount + retryCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                RoundedCornerShape(24.dp),
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val visibleChildren = childSessions.take(5)
            for (info in visibleChildren) {
                when (info.status) {
                    is SessionStatus.Busy -> PulsingDot(MaterialTheme.colorScheme.tertiary, size = 7.dp)
                    is SessionStatus.Retry -> PulsingDot(MaterialTheme.colorScheme.tertiary, size = 7.dp)
                    is SessionStatus.Idle -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StatusConnected),
                        )
                    }
                }
            }
            if (childSessions.size > 5) {
                Text(
                    text = "+${childSessions.size - 5}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append("$idleCount/${childSessions.size}")
                }
                append(" 完成")
                if (runningCount > 0) {
                    append(" · ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("$runningCount")
                    }
                    append(" 个")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                        append("运行中")
                    }
                }
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        )

        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "收起" else "展开",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SentinelExpanded(
    childSessions: List<ChildSessionInfo>,
    onNavigateToSession: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            )
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(childSessions, key = { it.session.id }) { info ->
            ChildSessionRow(
                info = info,
                onClick = { onNavigateToSession(info.session.id) },
            )
        }
    }
}

@Composable
private fun ChildSessionRow(
    info: ChildSessionInfo,
    onClick: () -> Unit,
) {
    val isRunning = info.status is SessionStatus.Busy || info.status is SessionStatus.Retry

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .then(
                if (isRunning) {
                    Modifier.background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRunning) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiary,
                        RoundedCornerShape(1.dp),
                    ),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }

        when (info.status) {
            is SessionStatus.Idle -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "完成",
                    modifier = Modifier.size(20.dp),
                    tint = StatusConnected,
                )
            }
            is SessionStatus.Busy -> {
                PulsingDot(
                    color = MaterialTheme.colorScheme.tertiary,
                    size = 7.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
            is SessionStatus.Retry -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "重试中",
                    modifier = Modifier.size(20.dp),
                    tint = StatusError,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.session.title.ifEmpty { "Session" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            Text(
                text = when (info.status) {
                    is SessionStatus.Busy -> "处理中…"
                    is SessionStatus.Retry -> "重试中 (第${info.status.attempt}次)"
                    is SessionStatus.Idle -> "已完成"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
