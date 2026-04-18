package me.xiaok.opencode.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.FileNode
import me.xiaok.opencode.domain.model.FileStatus

// ---------------------------------------------------------------------------
// Directory Browser
// ---------------------------------------------------------------------------

@Composable
internal fun DirectoryBrowserView(
    uiState: FileBrowserUiState,
    onNavigateUp: () -> Unit,
    onDirectoryClick: (path: String) -> Unit,
    onFileClick: (path: String) -> Unit,
) {
    val statusMap = remember(uiState.fileStatuses) {
        uiState.fileStatuses.associateBy { it.path }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        // "Up" navigation item when not at root
        if (uiState.currentPath != "." && uiState.currentPath != "/") {
            item {
                NavigateUpItem(onClick = onNavigateUp)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }

        items(
            items = uiState.fileTree,
            key = { it.path },
        ) { node ->
            val status = statusMap[node.path]
            FileNodeItem(
                node = node,
                fileStatus = status,
                onClick = {
                    if (node.type == "directory") {
                        onDirectoryClick(node.path)
                    } else {
                        onFileClick(node.path)
                    }
                },
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 56.dp),
            )
        }

        // Empty state
        if (uiState.fileTree.isEmpty() && !uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Empty directory",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NavigateUpItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Navigate up",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "..",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileNodeItem(
    node: FileNode,
    fileStatus: FileStatus?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(
            fileName = node.name,
            type = node.type,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (node.type == "directory") FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.type == "file" && node.path != node.name) {
                Text(
                    text = node.path,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Git status badge
        if (fileStatus != null) {
            GitStatusBadge(status = fileStatus.status, added = fileStatus.added, removed = fileStatus.removed)
        }
    }
}

@Composable
private fun FileIcon(
    fileName: String,
    type: String,
    modifier: Modifier = Modifier,
) {
    if (type == "directory") {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = Color(0xFFE8A838),
            modifier = modifier,
        )
        return
    }

    val ext = fileName.substringAfterLast('.', "").lowercase()
    val (icon, tint) = when (ext) {
        "kt", "kts" -> Icons.Default.Description to Color(0xFFA97BFF)
        "java" -> Icons.Default.Description to Color(0xFFFF7043)
        "py" -> Icons.Default.Description to Color(0xFF4B8BBE)
        "js", "jsx" -> Icons.Default.Description to Color(0xFFF7DF1E)
        "ts", "tsx" -> Icons.Default.Description to Color(0xFF3178C6)
        "json", "yaml", "yml", "toml" -> Icons.Default.Settings to Color(0xFF8BC34A)
        "xml", "html", "svg" -> Icons.Default.Description to Color(0xFFE44D26)
        "css", "scss" -> Icons.Default.Description to Color(0xFF264DE4)
        "md", "markdown" -> Icons.Default.Description to Color(0xFF2196F3)
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp" -> Icons.Default.Image to Color(0xFF4CAF50)
        "gradle", "properties" -> Icons.Default.Settings to Color(0xFF607D8B)
        "sh", "bash", "zsh" -> Icons.Default.Description to Color(0xFF4EAA25)
        "sql" -> Icons.Default.Description to Color(0xFFFF9800)
        "gitignore", "dockerignore", "editorconfig" -> Icons.Default.Settings to Color(0xFF9E9E9E)
        else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

@Composable
private fun GitStatusBadge(status: String, added: Int, removed: Int) {
    val (backgroundColor, contentColor, label) = when (status) {
        "added" -> Triple(Color(0xFF4CAF50), Color.White, "+$added")
        "deleted" -> Triple(Color(0xFFF44336), Color.White, "-$removed")
        "modified" -> Triple(Color(0xFFFF9800), Color.White, "+$added/-$removed")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, status)
    }

    Box(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
            color = contentColor,
        )
    }
}
