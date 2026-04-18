package me.xiaok.opencode.ui.screens.projects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
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
        onDirectoryBrowserDismiss = {
            showDirectoryBrowser = false
            viewModel.resetBrowserState()
        },
        onBrowseDirectory = { path -> viewModel.browseDirectory(path) },
        onNavigateUp = { viewModel.navigateUp() },
        onSelectDirectory = { viewModel.selectDirectory() },
        onRefresh = { viewModel.loadProjects() },
        onProjectClick = { item ->
            onNavigateToSessions(serverId, item.project.worktree)
        },
        onRemoveLocalProject = { directory ->
            viewModel.removeLocalProject(directory)
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
    onProjectClick: (ProjectListItem) -> Unit,
    onRemoveLocalProject: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenDirectoryBrowser: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showRemoveDialog by remember { mutableStateOf<ProjectListItem?>(null) }

    // Remove confirmation dialog
    showRemoveDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text("Remove local project") },
            text = {
                Text(
                    "Remove \"${item.project.name ?: item.project.worktree}\" from the project list? " +
                    "This only removes the local reference — the directory itself is not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveLocalProject(item.project.worktree)
                        showRemoveDialog = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }

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
                actions = {
                    AnimatedVisibility(
                        visible = !uiState.isConnected,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Connecting",
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                modifier = Modifier.testTag("fab_open_project"),
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
                            key = { it.project.id },
                        ) { item ->
                            ProjectRow(
                                item = item,
                                onClick = { onProjectClick(item) },
                                onLongClick = {
                                    if (item.isLocal) {
                                        showRemoveDialog = item
                                    } else {
                                        clipboardManager.setText(AnnotatedString(item.project.worktree))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Path copied to clipboard")
                                        }
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
// Project Row
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    item: ProjectListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = item.project
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("project_card")
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
                    color = if (item.isLocal) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (item.isLocal) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.name ?: project.worktree.split("/").lastOrNull().orEmpty().ifEmpty { project.id },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.isLocal) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Local",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
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
