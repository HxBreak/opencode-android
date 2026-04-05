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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.xiaok.opencode.domain.model.Session
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.ui.screens.terminal.PtyListDialog
import me.xiaok.opencode.ui.theme.StatusConnected
import me.xiaok.opencode.ui.theme.StatusError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless SessionListScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListRoute(
    serverId: String,
    onNavigateToChat: (serverId: String, sessionId: String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToTerminalWithPty: (ptyId: String) -> Unit,
    onNavigateToFiles: (directory: String) -> Unit,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionListScreen(
        uiState = uiState,
        onRefresh = { viewModel.refreshSessions() },
        onCreateSession = { viewModel.createSession() { sessionId -> onNavigateToChat(serverId, sessionId) } },
        onDeleteSession = { viewModel.deleteSession(it) },
        onDeleteSelectedSessions = { viewModel.deleteSelectedSessions() },
        onUpdateSessionTitle = { id, title -> viewModel.updateSessionTitle(id, title) },
        onArchiveSession = { viewModel.archiveSession(it) },
        onUnarchiveSession = { viewModel.unarchiveSession(it) },
        onArchiveSelectedSessions = { viewModel.archiveSelectedSessions() },
        onSetArchiveFilter = { viewModel.setArchiveFilter(it) },
        onToggleSelection = { viewModel.toggleSelection(it) },
        onEnterSelectionMode = { viewModel.enterSelectionMode(it) },
        onExitSelectionMode = { viewModel.exitSelectionMode() },
        onSelectAll = { viewModel.selectAll() },
        onToggleDirectoryCollapsed = { viewModel.toggleDirectoryCollapsed(it) },
        onLoadSessionChildren = { viewModel.loadSessionChildren(it) },
        onGetSessionChildren = { viewModel.getSessionChildren(it) },
        onNavigateToChat = { sessionId -> onNavigateToChat(serverId, sessionId) },
        onNavigateBack = onNavigateBack,
        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
        onClearSearch = { viewModel.clearSearch() },
        onNavigateToTerminal = onNavigateToTerminal,
        onNavigateToTerminalWithPty = onNavigateToTerminalWithPty,
        onNavigateToFiles = { onNavigateToFiles(it) },
        onPtyDelete = { viewModel.deletePty(it) },
    )
}

// ---------------------------------------------------------------------------
// Stateless SessionListScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    uiState: SessionListUiState,
    onRefresh: () -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onDeleteSelectedSessions: () -> Unit,
    onUpdateSessionTitle: (sessionId: String, title: String) -> Unit,
    onArchiveSession: (sessionId: String) -> Unit,
    onUnarchiveSession: (sessionId: String) -> Unit,
    onArchiveSelectedSessions: () -> Unit,
    onSetArchiveFilter: (SessionArchiveFilter) -> Unit,
    onToggleSelection: (sessionId: String) -> Unit,
    onEnterSelectionMode: (sessionId: String) -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleDirectoryCollapsed: (directory: String) -> Unit,
    onLoadSessionChildren: (sessionId: String) -> Unit,
    onGetSessionChildren: (sessionId: String) -> List<Session>,
    onNavigateToChat: (sessionId: String) -> Unit,
    onNavigateBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToTerminalWithPty: (ptyId: String) -> Unit = {},
    onNavigateToFiles: (directory: String) -> Unit = {},
    onPtyDelete: (ptyId: String) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var renamingSession by remember { mutableStateOf<Session?>(null) }
    var childrenParentSession by remember { mutableStateOf<Session?>(null) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var showPtyList by remember { mutableStateOf(false) }

    renamingSession?.let { session ->
        RenameSessionDialog(
            currentTitle = session.title,
            onConfirm = { newTitle ->
                onUpdateSessionTitle(session.id, newTitle)
                renamingSession = null
            },
            onDismiss = { renamingSession = null },
        )
    }

    if (showPtyList) {
        val runningPtys = uiState.ptyList.filter { it.status != "exited" }
        PtyListDialog(
            ptys = runningPtys,
            currentPtyId = null,
            onPtyClick = { pty ->
                showPtyList = false
                onNavigateToTerminalWithPty(pty.id)
            },
            onPtyDelete = { ptyId -> onPtyDelete(ptyId) },
            onCreateNew = {
                showPtyList = false
                onNavigateToTerminal()
            },
            onDismiss = { showPtyList = false },
        )
    }

    childrenParentSession?.let { parent ->
        val children = onGetSessionChildren(parent.id)
        ChildrenSessionsDialog(
            parentTitle = parent.title.ifBlank { "Untitled" },
            children = children,
            onChildClick = { childSession ->
                childrenParentSession = null
                onNavigateToChat(childSession.id)
            },
            onDismiss = { childrenParentSession = null },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (uiState.isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = uiState.selectedSessions.size,
                    onExitSelection = onExitSelectionMode,
                    onSelectAll = onSelectAll,
                    onDeleteSelected = onDeleteSelectedSessions,
                    onArchiveSelected = onArchiveSelectedSessions,
                    scrollBehavior = scrollBehavior,
                )
            } else if (isSearchMode) {
                SessionSearchTopBar(
                    query = searchInput,
                    onQueryChange = { searchInput = it; onSearchQueryChanged(it) },
                    onClose = {
                        isSearchMode = false
                        searchInput = ""
                        onClearSearch()
                    },
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.projectName,
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
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search sessions",
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onCreateSession,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                        )
                    },
                    text = { Text("New Session") },
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

            // Archive filter tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple(SessionArchiveFilter.All, Icons.AutoMirrored.Default.List, "All"),
                    Triple(SessionArchiveFilter.Active, Icons.Default.CheckCircle, "Active"),
                    Triple(SessionArchiveFilter.Archived, Icons.Default.Archive, "Archived"),
                ).forEach { (filter, icon, label) ->
                    FilterChip(
                        selected = uiState.archiveFilter == filter,
                        onClick = { onSetArchiveFilter(filter) },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            if (uiState.sessions.isEmpty() && !uiState.isLoading) {
                SessionEmptyState(
                    onCreateSession = onCreateSession,
                )
            } else {
                val grouped = uiState.sessions.groupBy { it.directory }
                    .toList()
                    .sortedByDescending { group ->
                        group.second.maxOfOrNull { it.time.updated } ?: 0L
                    }

                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        grouped.forEach { (directory, sessions) ->
                            val isCollapsed = directory in uiState.collapsedDirectories
                            item(key = "header_$directory") {
                                val directorySessionStatuses = sessions.mapNotNull {
                                    uiState.sessionStatuses[it.id]
                                }
                                val hasActiveSession = directorySessionStatuses.any {
                                    it == SessionStatus.BUSY || it == SessionStatus.RETRY
                                }
                                val directoryTokens = sessions.sumOf { uiState.sessionTokens[it.id] ?: 0L }
                                DirectoryHeader(
                                    directory = directory,
                                    isCollapsed = isCollapsed,
                                    sessionCount = sessions.size,
                                    branch = uiState.vcsBranch,
                                    hasActiveSession = hasActiveSession,
                                    totalTokens = directoryTokens,
                                    onToggle = { onToggleDirectoryCollapsed(directory) },
                                )
                            }
                            if (!isCollapsed) {
                                item(key = "quick_$directory") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        QuickAccessCard(
                                            icon = Icons.Default.Terminal,
                                            label = "Terminal",
                                            badgeCount = uiState.activePtyCount,
                                            modifier = Modifier.weight(1f),
                                            onClick = onNavigateToTerminal,
                                            onLongClick = { showPtyList = true },
                                        )
                                        QuickAccessCard(
                                            icon = Icons.Default.Folder,
                                            label = "Files",
                                            modifier = Modifier.weight(1f),
                                            onClick = { onNavigateToFiles(directory) },
                                        )
                                    }
                                }
                                items(
                                    items = sessions,
                                    key = { it.id },
                                ) { session ->
                                    val status = uiState.sessionStatuses[session.id]
                                        ?: SessionStatus.IDLE
                                    val isSelected = session.id in uiState.selectedSessions

                                    SessionRow(
                                        session = session,
                                        status = status,
                                        isSelectionMode = uiState.isSelectionMode,
                                        isSelected = isSelected,
                                        isUnread = session.id in uiState.unreadSessions,
                                        onClick = {
                                            if (uiState.isSelectionMode) {
                                                onToggleSelection(session.id)
                                            } else {
                                                onNavigateToChat(session.id)
                                            }
                                        },
                                        onLongClick = {
                                            if (!uiState.isSelectionMode) {
                                                onEnterSelectionMode(session.id)
                                            }
                                        },
                                        onSwipeDelete = { onDeleteSession(session.id) },
                                        onSwipeRename = { renamingSession = session },
                                        onArchive = { onArchiveSession(session.id) },
                                        onUnarchive = { onUnarchiveSession(session.id) },
                                        onShowChildren = {
                                            onLoadSessionChildren(session.id)
                                            childrenParentSession = session
                                        },
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Selection TopAppBar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    selectedCount: Int,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onExitSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection",
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select all",
                )
            }
            IconButton(onClick = onArchiveSelected) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = "Archive",
                )
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

// ---------------------------------------------------------------------------
// Search TopAppBar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search sessions...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { /* no-op, filtering is live */ },
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
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
// Directory Header
// ---------------------------------------------------------------------------

@Composable
private fun DirectoryHeader(
    directory: String,
    isCollapsed: Boolean,
    sessionCount: Int,
    branch: String?,
    hasActiveSession: Boolean,
    totalTokens: Long,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Folder icon
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (isCollapsed) "Expand" else "Collapse",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = directory.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasActiveSession) {
                        Spacer(modifier = Modifier.width(6.dp))
                        PulsingDot(
                            color = StatusConnected,
                            modifier = Modifier.size(6.dp),
                        )
                    }
                }
                if (branch != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = branch,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Token count
            if (totalTokens > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = formatTokenCount(totalTokens),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            // Count badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "$sessionCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session Row with Swipe-to-Dismiss
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
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
    onShowChildren: () -> Unit,
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
                        text = { Text("Show children") },
                        onClick = {
                            showContextMenu = false
                            onShowChildren()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
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
// Status Indicator
// ---------------------------------------------------------------------------

@Composable
private fun UnifiedStatusIndicator(
    status: SessionStatus,
    isUnread: Boolean,
    modifier: Modifier = Modifier,
) {
    when (status) {
        SessionStatus.BUSY -> PulsingDot(
            color = StatusConnected,
            modifier = modifier,
        )
        SessionStatus.RETRY -> PulsingDot(
            color = Color(0xFFFFA000),
            modifier = modifier,
        )
        SessionStatus.IDLE -> {
            if (isUnread) {
                StatusDot(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = pulseAlpha)),
    )
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun SessionEmptyState(
    onCreateSession: () -> Unit,
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
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No sessions",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(28.dp))
            ExtendedFloatingActionButton(
                onClick = onCreateSession,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = { Text("New Session") },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rename Session Dialog
// ---------------------------------------------------------------------------

@Composable
fun RenameSessionDialog(
    currentTitle: String,
    onConfirm: (newTitle: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Session",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Children Sessions Dialog
// ---------------------------------------------------------------------------

@Composable
fun ChildrenSessionsDialog(
    parentTitle: String,
    children: List<Session>,
    onChildClick: (Session) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Children of \"$parentTitle\"",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            if (children.isEmpty()) {
                Text(
                    text = "No child sessions found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(children, key = { it.id }) { child ->
                        Surface(
                            onClick = { onChildClick(child) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = child.title.ifBlank { "Untitled" },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatTimestamp(child.time.created),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Date Formatting Helper
// ---------------------------------------------------------------------------

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

// ---------------------------------------------------------------------------
// Quick Access Card (Terminal / Files)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAccessCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                if (badgeCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else "$badgeCount",
                            color = MaterialTheme.colorScheme.onError,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

private fun formatTokenCount(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> "${(tokens / 100_000).toInt() / 10.0}M"
        tokens >= 1_000 -> "${(tokens / 100).toInt() / 10.0}k"
        else -> "$tokens"
    }
}
