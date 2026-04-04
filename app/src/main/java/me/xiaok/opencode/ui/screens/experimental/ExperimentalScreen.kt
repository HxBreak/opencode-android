package me.xiaok.opencode.ui.screens.experimental

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------

private val TabTitles = listOf("Workspaces", "Worktrees", "Resources")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalRoute(
    onNavigateBack: () -> Unit,
    viewModel: ExperimentalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Error snackbar
    val errorMsg = uiState.error
    if (errorMsg != null) {
        LaunchedEffect(errorMsg) {
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    ExperimentalScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onTabSelected = { viewModel.selectTab(it) },
        onRefresh = { viewModel.loadAll() },
        onCreateWorkspace = { id, type, branch -> viewModel.createWorkspace(id, type, branch) },
        onDeleteWorkspace = { viewModel.deleteWorkspace(it) },
        onCreateWorktree = { name, cmd -> viewModel.createWorktree(name, cmd) },
        onDeleteWorktree = { viewModel.deleteWorktree(it) },
        onResetWorktree = { viewModel.resetWorktree(it) },
        onRefreshWorkspaces = { viewModel.loadWorkspaces() },
        onRefreshWorktrees = { viewModel.loadWorktrees() },
        onRefreshResources = { viewModel.loadResources() },
    )
}

// ---------------------------------------------------------------------------
// Stateless Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalScreen(
    uiState: ExperimentalUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onCreateWorkspace: (id: String?, type: String, branch: String?) -> Unit,
    onDeleteWorkspace: (workspaceId: String) -> Unit,
    onCreateWorktree: (name: String?, startCommand: String?) -> Unit,
    onDeleteWorktree: (directory: String) -> Unit,
    onResetWorktree: (directory: String) -> Unit,
    onRefreshWorkspaces: () -> Unit,
    onRefreshWorktrees: () -> Unit,
    onRefreshResources: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Experimental",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab) {
                TabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = { Text(text = title) },
                    )
                }
            }

            when (uiState.selectedTab) {
                0 -> WorkspacesTab(
                    uiState = uiState,
                    onCreate = onCreateWorkspace,
                    onDelete = onDeleteWorkspace,
                    onRefresh = onRefreshWorkspaces,
                )
                1 -> WorktreesTab(
                    uiState = uiState,
                    onCreate = onCreateWorktree,
                    onDelete = onDeleteWorktree,
                    onReset = onResetWorktree,
                    onRefresh = onRefreshWorktrees,
                )
                2 -> ResourcesTab(
                    uiState = uiState,
                    onRefresh = onRefreshResources,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Workspaces Tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspacesTab(
    uiState: ExperimentalUiState,
    onCreate: (id: String?, type: String, branch: String?) -> Unit,
    onDelete: (workspaceId: String) -> Unit,
    onRefresh: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        CreateWorkspaceDialog(
            onConfirm = { id, type, branch ->
                onCreate(id, type, branch)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoadingWorkspaces,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Create button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Workspace")
                }
            }

            if (uiState.isLoadingWorkspaces && uiState.workspaces == null) {
                LoadingBox()
            } else {
                val workspaceList = parseWorkspaceList(uiState.workspaces)
                if (workspaceList.isEmpty()) {
                    EmptyBox("No workspaces found")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    ) {
                        items(items = workspaceList, key = { it.id }) { workspace ->
                            WorkspaceCard(
                                workspace = workspace,
                                onDelete = { onDelete(workspace.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Worktrees Tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorktreesTab(
    uiState: ExperimentalUiState,
    onCreate: (name: String?, startCommand: String?) -> Unit,
    onDelete: (directory: String) -> Unit,
    onReset: (directory: String) -> Unit,
    onRefresh: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        CreateWorktreeDialog(
            onConfirm = { name, cmd ->
                onCreate(name, cmd)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoadingWorktrees,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Worktree")
                }
            }

            if (uiState.isLoadingWorktrees && uiState.worktrees.isEmpty()) {
                LoadingBox()
            } else if (uiState.worktrees.isEmpty()) {
                EmptyBox("No worktrees found")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    items(items = uiState.worktrees, key = { it }) { directory ->
                        WorktreeCard(
                            directory = directory,
                            onDelete = { onDelete(directory) },
                            onReset = { onReset(directory) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Resources Tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourcesTab(
    uiState: ExperimentalUiState,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = uiState.isLoadingResources,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (uiState.isLoadingResources && uiState.resources == null) {
            LoadingBox()
        } else if (uiState.resources == null) {
            EmptyBox("No resources available")
        } else {
            val resourceText = formatJson(uiState.resources)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Experimental Resources",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = resourceText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun WorkspaceCard(
    workspace: WorkspaceItem,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = workspace.id.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            if (workspace.type.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type: ${workspace.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (workspace.branch != null) {
                Text(
                    text = "Branch: ${workspace.branch}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
            }
        }
    }
}

@Composable
private fun WorktreeCard(
    directory: String,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = directory,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun CreateWorkspaceDialog(
    onConfirm: (id: String?, type: String, branch: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Workspace",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("ID (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        id.ifBlank { null },
                        type,
                        branch.ifBlank { null },
                    )
                },
            ) {
                Text("Create")
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
private fun CreateWorktreeDialog(
    onConfirm: (name: String?, startCommand: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startCommand by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Worktree",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startCommand,
                    onValueChange = { startCommand = it },
                    label = { Text("Start Command (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.ifBlank { null },
                        startCommand.ifBlank { null },
                    )
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Shared Components
// ---------------------------------------------------------------------------

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBox(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Parsed Models
// ---------------------------------------------------------------------------

private data class WorkspaceItem(
    val id: String,
    val type: String = "",
    val branch: String? = null,
)

private fun parseWorkspaceList(json: JsonElement?): List<WorkspaceItem> {
    if (json == null) return emptyList()
    return try {
        when (json) {
            is JsonArray -> json.mapNotNull { element ->
                parseWorkspaceItem(element)
            }
            is JsonObject -> {
                // Might be a map of id -> workspace object
                json.entries.mapNotNull { (key, value) ->
                    if (value is JsonObject) {
                        parseWorkspaceItem(value, fallbackId = key)
                    } else {
                        WorkspaceItem(id = key)
                    }
                }
            }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseWorkspaceItem(element: JsonElement, fallbackId: String? = null): WorkspaceItem? {
    if (element !is JsonObject) return null
    val id = element["id"]?.jsonPrimitive?.content ?: fallbackId ?: return null
    val type = element["type"]?.jsonPrimitive?.content ?: ""
    val branch = element["branch"]?.jsonPrimitive?.content
    return WorkspaceItem(id = id, type = type, branch = branch)
}

private fun formatJson(json: JsonElement?): String {
    if (json == null) return ""
    return try {
        when (json) {
            is JsonObject -> json.entries.joinToString("\n") { (key, value) ->
                "$key: ${formatJsonValue(value)}"
            }
            is JsonArray -> json.joinToString("\n") { formatJsonValue(it) }
            is JsonPrimitive -> json.content
            else -> json.toString()
        }
    } catch (_: Exception) {
        json.toString()
    }
}

private fun formatJsonValue(value: JsonElement): String {
    return when (value) {
        is JsonPrimitive -> value.content
        is JsonObject -> value.toString()
        is JsonArray -> value.toString()
        else -> value.toString()
    }
}
