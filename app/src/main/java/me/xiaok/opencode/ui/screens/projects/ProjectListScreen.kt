package me.xiaok.opencode.ui.screens.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.xiaok.opencode.domain.model.FileNode
import me.xiaok.opencode.domain.model.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless ProjectListScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListRoute(
    serverId: String,
    onNavigateToSessions: (serverId: String, directory: String?) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProjectListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    var showDirectoryBrowser by remember { mutableStateOf(false) }

    // Collect directory selection events
    LaunchedEffect(Unit) {
        viewModel.selectedDirectory.collect { directory ->
            showDirectoryBrowser = false
            onNavigateToSessions(serverId, directory)
        }
    }

    ProjectListScreen(
        uiState = uiState,
        browserState = browserState,
        showDirectoryBrowser = showDirectoryBrowser,
        onDirectoryBrowserDismiss = { showDirectoryBrowser = false },
        onBrowseDirectory = { path -> viewModel.browseDirectory(path) },
        onNavigateUp = { viewModel.navigateUp() },
        onSelectDirectory = { viewModel.selectDirectory() },
        onRefresh = { viewModel.loadProjects() },
        onProjectClick = { project ->
            onNavigateToSessions(serverId, project.worktree)
        },
        onNavigateBack = onNavigateBack,
        onOpenDirectoryBrowser = {
            showDirectoryBrowser = true
            viewModel.ensurePathInfo()
        },
        onSearchQueryChanged = { query -> viewModel.onSearchQueryChanged(query) },
    )
}

// ---------------------------------------------------------------------------
// Stateless ProjectListScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    uiState: ProjectListUiState,
    browserState: DirectoryBrowserState,
    showDirectoryBrowser: Boolean,
    onDirectoryBrowserDismiss: () -> Unit,
    onBrowseDirectory: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelectDirectory: () -> Unit,
    onRefresh: () -> Unit,
    onProjectClick: (Project) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenDirectoryBrowser: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.serverName.ifEmpty { "Projects" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDirectoryBrowser,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Open Project",
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.error != null) {
                ErrorBanner(
                    message = uiState.error!!,
                    onRetry = onRefresh,
                )
            }

            if (uiState.projects.isEmpty() && !uiState.isLoading) {
                ProjectEmptyState(
                    onOpenProject = onOpenDirectoryBrowser,
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(
                            items = uiState.projects,
                            key = { it.id },
                        ) { project ->
                            ProjectRow(
                                project = project,
                                onClick = { onProjectClick(project) },
                                onLongClick = {
                                    clipboardManager.setText(AnnotatedString(project.worktree))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Path copied to clipboard")
                                    }
                                },
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 6.dp,
                                ),
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDirectoryBrowser) {
        DirectoryBrowserDialog(
            state = browserState,
            onBrowse = onBrowseDirectory,
            onNavigateUp = onNavigateUp,
            onSelect = onSelectDirectory,
            onDismiss = onDirectoryBrowserDismiss,
            onSearchQueryChanged = onSearchQueryChanged,
        )
    }
}

// ---------------------------------------------------------------------------
// Directory Browser Dialog
// ---------------------------------------------------------------------------

@Composable
private fun DirectoryBrowserDialog(
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

// ---------------------------------------------------------------------------
// Project Row
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    project: Project,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Project icon — colored square or default folder
            val iconColor = project.icon?.color
            val parsedColor = iconColor?.let { parseHexColor(it) }
            if (parsedColor != null) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = parsedColor.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(parsedColor),
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name ?: project.worktree.split("/").lastOrNull().orEmpty().ifEmpty { project.id },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.worktree,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (project.vcs != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = project.vcs,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (project.time.updated > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(project.time.updated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun ProjectEmptyState(
    onOpenProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No projects yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Open a project directory to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onOpenProject,
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Project")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Error Banner
// ---------------------------------------------------------------------------

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

/** Parse a hex color string (e.g. "#FF5722" or "FF5722") into a Compose Color.
 *  Returns null if the string is not a valid hex color. */
private fun parseHexColor(hex: String): Color? {
    val cleaned = hex.removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16).toInt())
            8 -> Color(cleaned.toLong(16).toInt())
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}
