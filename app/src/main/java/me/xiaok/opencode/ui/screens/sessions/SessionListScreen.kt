package me.xiaok.opencode.ui.screens.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import me.xiaok.opencode.domain.model.Session
import me.xiaok.opencode.domain.model.SessionStatus
import me.xiaok.opencode.ui.screens.terminal.PtyListDialog

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
        serverId = serverId,
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
        onMarkAllAsRead = { viewModel.markAllAsRead() },
        onToggleDirectoryCollapsed = { viewModel.toggleDirectoryCollapsed(it) },
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
    serverId: String,
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
    onMarkAllAsRead: () -> Unit,
    onToggleDirectoryCollapsed: (directory: String) -> Unit,
    onNavigateToChat: (sessionId: String) -> Unit,
    onNavigateBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToTerminalWithPty: (ptyId: String) -> Unit = {},
    onNavigateToFiles: (directory: String) -> Unit = {},
    onPtyDelete: (ptyId: String) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var renamingSession by remember { mutableStateOf<Session?>(null) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var showPtyList by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
                        if (uiState.unreadSessions.isNotEmpty()) {
                            IconButton(onClick = onMarkAllAsRead) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Mark all as read",
                                )
                            }
                        }
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
                    modifier = Modifier.testTag("fab_new_session"),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session",
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
                SessionEmptyState()
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
                                    it !is SessionStatus.Idle
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
                                        ?: SessionStatus.Idle
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
                                        onCopyUrl = {
                                            val server = uiState.serverConnection
                                            val base = "opencode://session/$serverId/${session.id}"
                                            val uri = if (server != null) {
                                                val params = buildMap {
                                                    put("serverName", server.name)
                                                    put("serverUrl", server.baseUrl)
                                                    if (server.username.isNotEmpty()) put("username", server.username)
                                                    if (server.password.isNotEmpty()) put("password", server.password)
                                                }.entries.joinToString("&", prefix = "?") { (k, v) ->
                                                    "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
                                                }
                                                base + params
                                            } else {
                                                base
                                            }
                                            clipboardManager.setText(AnnotatedString(uri))
                                            Toast.makeText(context, "Session URL copied", Toast.LENGTH_SHORT).show()
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
