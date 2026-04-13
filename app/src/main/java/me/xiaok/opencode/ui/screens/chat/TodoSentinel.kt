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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.xiaok.opencode.domain.model.Todo
import me.xiaok.opencode.ui.components.common.PulsingDot
import me.xiaok.opencode.ui.theme.StatusConnected

@Composable
fun TodoSentinel(
    modifier: Modifier = Modifier,
    todos: List<Todo>,
    onDismiss: () -> Unit,
) {
    val allDone = todos.isNotEmpty() && todos.all { it.status == "completed" || it.status == "cancelled" }
    var isExpanded by remember { mutableStateOf(false) }
    var pendingDismiss by remember { mutableStateOf(false) }

    // Auto-dismiss when all todos are done
    LaunchedEffect(allDone) {
        if (!allDone) {
            pendingDismiss = false
            return@LaunchedEffect
        }
        delay(2000)
        if (isExpanded) {
            pendingDismiss = true
        } else {
            onDismiss()
        }
    }

    // Fire pending dismiss when user collapses
    LaunchedEffect(isExpanded, pendingDismiss) {
        if (!isExpanded && pendingDismiss) {
            onDismiss()
        }
    }

    Column(modifier = modifier) {
        TodoCollapsed(
            todos = todos,
            isExpanded = isExpanded,
            onClick = { isExpanded = !isExpanded },
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            TodoExpanded(todos = todos)
        }
    }
}

@Composable
private fun TodoCollapsed(
    todos: List<Todo>,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val completedCount = todos.count { it.status == "completed" || it.status == "cancelled" }
    val inProgressCount = todos.count { it.status == "in_progress" }
    val pendingCount = todos.count { it.status == "pending" }
    val activeTodo = todos.find { it.status == "in_progress" }
        ?: todos.find { it.status == "pending" }
        ?: todos.lastOrNull { it.status == "completed" }
        ?: todos.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                RoundedCornerShape(24.dp),
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dots — up to 5
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val visibleTodos = todos.take(5)
            for (todo in visibleTodos) {
                when (todo.status) {
                    "in_progress" -> PulsingDot(
                        MaterialTheme.colorScheme.tertiary,
                        size = 7.dp,
                    )
                    "completed", "cancelled" -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StatusConnected),
                        )
                    }
                    "pending" -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        )
                    }
                }
            }
            if (todos.size > 5) {
                Text(
                    text = "+${todos.size - 5}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Progress text
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append("$completedCount/${todos.size}")
                }
                append(" 完成")
                if (inProgressCount > 0) {
                    append(" · ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                        append("$inProgressCount")
                    }
                    append(" 进行中")
                }
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        )

        // Active task preview
        if (activeTodo != null && activeTodo.content.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = activeTodo.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Expand/collapse icon
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "收起" else "展开",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodoExpanded(
    todos: List<Todo>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            )
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        for (todo in todos) {
            TodoRow(todo = todo)
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
) {
    val isCompleted = todo.status == "completed" || todo.status == "cancelled"
    val isInProgress = todo.status == "in_progress"
    val isPending = todo.status == "pending"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isInProgress) {
                    Modifier.background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left colored indicator bar for in_progress
        if (isInProgress) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(20.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiary,
                        RoundedCornerShape(1.dp),
                    ),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }

        // Status icon
        when {
            isCompleted -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "完成",
                    modifier = Modifier.size(18.dp),
                    tint = StatusConnected,
                )
            }
            isInProgress -> {
                PulsingDot(
                    color = MaterialTheme.colorScheme.tertiary,
                    size = 7.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
            isPending -> {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Content
        Text(
            text = todo.content,
            style = MaterialTheme.typography.bodySmall.copy(
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = when {
                isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                isPending -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            modifier = Modifier.weight(1f),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
