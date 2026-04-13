package me.xiaok.opencode.ui.screens.sessions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.Session
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.ui.components.common.PulsingDot
import me.xiaok.opencode.ui.components.common.StatusDot
import me.xiaok.opencode.ui.theme.StatusConnected
import me.xiaok.opencode.ui.theme.StatusError

// ---------------------------------------------------------------------------
// Session Row with Swipe-to-Dismiss
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SessionRow(
    session: Session,
    status: SessionStatus,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isUnread: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeRename: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onCopyUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val isArchived = session.time.archived != null
    val contentAlpha = if (isArchived) 0.5f else 1f

    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isArchived) {
                        onSwipeDelete()
                    } else {
                        onArchive()
                    }
                    isArchived // dismiss only for delete, not archive
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeRename()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isSelectionMode,
        enableDismissFromEndToStart = !isSelectionMode,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isArchived) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.tertiary
                }
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF1565C0)
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val alignment = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.Settled -> Alignment.Center
            }
            val icon = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isArchived) Icons.Default.Delete
                    else Icons.Default.Archive
                }
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                SwipeToDismissBoxValue.Settled -> null
            }
            val tint = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isArchived) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onTertiary
                }
                SwipeToDismissBoxValue.StartToEnd -> Color.White
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                    )
                }
            }
        },
        content = {
            val backgroundColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }

            Box {
                Surface(
                    modifier = modifier
                        .fillMaxWidth()
                        .testTag("session_card")
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = {
                                if (!isSelectionMode) {
                                    showContextMenu = true
                                } else {
                                    onLongClick()
                                }
                            },
                        ),
                    color = backgroundColor,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSelectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onClick() },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UnifiedStatusIndicator(status = status, isUnread = isUnread)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = session.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatTimestamp(session.time.updated),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                                )
                                session.summary?.let { summary ->
                                    if (summary.additions > 0 || summary.deletions > 0) {
                                        Text(
                                            text = "  \u00B7  ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                                        )
                                        Text(
                                            text = buildAnnotatedString {
                                                if (summary.additions > 0) {
                                                    withStyle(
                                                        SpanStyle(
                                                            color = StatusConnected,
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                    ) {
                                                        append("+${summary.additions}")
                                                    }
                                                }
                                                if (summary.additions > 0 && summary.deletions > 0) {
                                                    append(" ")
                                                }
                                                if (summary.deletions > 0) {
                                                    withStyle(
                                                        SpanStyle(
                                                            color = StatusError,
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                    ) {
                                                        append("-${summary.deletions}")
                                                    }
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }

                        // Overflow menu icon
                        if (!isSelectionMode) {
                            IconButton(
                                onClick = { showContextMenu = true },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                ) {
                    // TODO: Unarchive disabled — server Zod schema doesn't accept null yet
                    // if (session.time.archived != null) {
                    //     DropdownMenuItem(
                    //         text = { Text("Unarchive") },
                    //         onClick = {
                    //             showContextMenu = false
                    //             onUnarchive()
                    //         },
                    //         leadingIcon = {
                    //             Icon(
                    //                 imageVector = Icons.Default.Unarchive,
                    //                 contentDescription = null,
                    //             )
                    //         },
                    //     )
                    // } else
                    if (session.time.archived == null) {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                showContextMenu = false
                                onArchive()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Copy URL") },
                        onClick = {
                            showContextMenu = false
                            onCopyUrl()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showContextMenu = false
                            onSwipeRename()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showContextMenu = false
                            onSwipeDelete()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Status Indicators (private to this file)
// ---------------------------------------------------------------------------

@Composable
private fun UnifiedStatusIndicator(
    status: SessionStatus,
    isUnread: Boolean,
    modifier: Modifier = Modifier,
) {
    when (status) {
        is SessionStatus.Busy -> PulsingDot(
            color = StatusConnected,
            size = 10.dp,
            modifier = modifier,
        )
        is SessionStatus.Retry -> PulsingDot(
            color = Color(0xFFFFA000),
            size = 10.dp,
            modifier = modifier,
        )
        is SessionStatus.Idle -> {
            if (isUnread) {
                StatusDot(
                    color = MaterialTheme.colorScheme.primary,
                    size = 10.dp,
                    modifier = modifier,
                )
            }
        }
    }
}
