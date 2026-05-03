package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.ui.components.common.formatTokenCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    sessionTitle: String,
    totalTokens: Long,
    isShared: Boolean,
    sessionStatus: SessionStatus,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    callbacks: ChatCallbacks,
    onRenameSession: () -> Unit,
    hasRevert: Boolean,
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = sessionTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    if (totalTokens > 0) {
                        Text(
                            text = formatTokenCount(totalTokens) + " tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isShared) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Shared",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            // Session diff navigation
            IconButton(onClick = callbacks.onNavigateToSessionDiff) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Changes",
                )
            }
            if (sessionStatus !is SessionStatus.Idle) {
                IconButton(onClick = callbacks.onAbort) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { onShowMenuChange(true) },
                    modifier = Modifier.testTag("chat_more"),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            onShowMenuChange(false)
                            onRenameSession()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            onShowMenuChange(false)
                            callbacks.onExportSession()
                        },
                    )
                    if (hasRevert) {
                        DropdownMenuItem(
                            text = { Text("Unrevert") },
                            onClick = {
                                onShowMenuChange(false)
                                callbacks.onUnrevertSession()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            onShowMenuChange(false)
                            callbacks.onDeleteSession()
                        },
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
