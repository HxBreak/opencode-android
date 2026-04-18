package me.xiaok.opencode.ui.screens.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Directory Browser Dialog
// ---------------------------------------------------------------------------

@Composable
internal fun DirectoryBrowserDialog(
    state: DirectoryBrowserState,
    onBrowse: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val hasSuggestions = state.suggestions.isNotEmpty() || state.isSearching

    // Initial load when dialog opens
    LaunchedEffect(Unit) {
        if (state.entries.isEmpty() && !state.isLoading) {
            onBrowse(".")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Open Project Directory",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Current path display with up navigation
                val isRoot = state.currentPath == "." ||
                        state.currentPath == "/" ||
                        state.currentPath.isEmpty()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Up navigation button (disabled at root)
                        IconButton(
                            onClick = onNavigateUp,
                            enabled = !isRoot && !hasSuggestions,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Parent directory",
                                modifier = Modifier.size(20.dp),
                                tint = if (isRoot || hasSuggestions) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        Text(
                            text = state.currentAbsolutePath.ifEmpty { state.currentPath },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        )
                    }
                }

                // Search / path input with autocomplete
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        onSearchQueryChanged(newQuery)
                    },
                    label = { Text("Search or enter path") },
                    placeholder = { Text("~  /path/to  or name", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )

                // Error state
                if (state.error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                // Content area: suggestions OR browse list
                if (state.isLoading && !hasSuggestions) {
                    // Full-screen loading (initial browse, no query)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        // ---- Autocomplete suggestions (when user is typing) ----
                        if (hasSuggestions) {
                            if (state.isSearching) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }

                            items(
                                items = state.suggestions,
                                key = { it.absolute },
                            ) { suggestion ->
                                SuggestionEntry(
                                    suggestion = suggestion,
                                    onClick = {
                                        searchQuery = ""
                                        onSearchQueryChanged("")
                                        onBrowse(suggestion.path)
                                    },
                                )
                            }
                        } else {
                            // ---- Directory browsing (no search query) ----
                            val isRoot = state.currentPath == "." ||
                                    state.currentPath == "/" ||
                                    state.currentPath.isEmpty()
                            if (!isRoot) {
                                item {
                                    DirectoryEntry(
                                        name = "..",
                                        isParent = true,
                                        onClick = onNavigateUp,
                                    )
                                }
                            }

                            if (state.isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }

                            items(
                                items = state.entries,
                                key = { it.path },
                            ) { entry ->
                                DirectoryEntry(
                                    name = entry.name,
                                    isParent = false,
                                    onClick = { onBrowse(entry.path) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Go to typed path
                if (searchQuery.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onBrowse(searchQuery.trim())
                            searchQuery = ""
                            onSearchQueryChanged("")
                        },
                    ) {
                        Text("Go")
                    }
                }
                FilledTonalButton(
                    onClick = onSelect,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DirectoryEntry(
    name: String,
    isParent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isParent) Icons.AutoMirrored.Default.ArrowBack else Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isParent) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isParent) "Parent directory" else name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isParent) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SuggestionEntry(
    suggestion: DirectorySuggestion,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestion.absolute,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
