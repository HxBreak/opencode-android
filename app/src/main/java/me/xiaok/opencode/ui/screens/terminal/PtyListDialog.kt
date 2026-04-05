package me.xiaok.opencode.ui.screens.terminal

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.PtyInfo

@Composable
fun PtyListDialog(
    ptys: List<PtyInfo>,
    currentPtyId: String?,
    onPtyClick: (PtyInfo) -> Unit,
    onPtyDelete: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Open Terminals",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            if (ptys.isEmpty()) {
                Text(
                    text = "No running terminals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(ptys, key = { it.id }) { pty ->
                        PtyListItem(
                            pty = pty,
                            isCurrent = pty.id == currentPtyId,
                            onClick = { onPtyClick(pty) },
                            onDelete = { onPtyDelete(pty.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onCreateNew) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Terminal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PtyListItem(
    pty: PtyInfo,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val bgColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Terminal icon with status dot
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pty.title.ifBlank {
                        pty.command.substringAfterLast('/')
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                if (pty.cwd.isNotBlank()) {
                    Text(
                        text = pty.cwd.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            if (isCurrent) {
                // "Current" indicator
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {}
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Delete button (not for current PTY)
            if (!isCurrent) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close terminal",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
