package me.xiaok.opencode.ui.screens.server

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.xiaok.opencode.domain.model.McpServerConfig
import me.xiaok.opencode.domain.model.McpServerCreateRequest
import me.xiaok.opencode.domain.model.McpStatus

// ---------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpManagementRoute(
    onNavigateBack: () -> Unit,
    viewModel: McpManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    McpManagementScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = { viewModel.loadServers() },
        onAddServer = { request -> viewModel.addServer(request) },
        onConnectServer = { name -> viewModel.connectServer(name) },
        onDisconnectServer = { name -> viewModel.disconnectServer(name) },
        onRemoveAuth = { name -> viewModel.removeAuth(name) },
        onShowAddDialog = { viewModel.showAddDialog() },
        onDismissAddDialog = { viewModel.dismissAddDialog() },
        onClearError = { viewModel.clearError() },
    )
}

// ---------------------------------------------------------------------------
// Stateless Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpManagementScreen(
    uiState: McpManagementUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddServer: (McpServerCreateRequest) -> Unit,
    onConnectServer: (String) -> Unit,
    onDisconnectServer: (String) -> Unit,
    onRemoveAuth: (String) -> Unit,
    onShowAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onClearError: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    // Error snackbar
    val errorMsg = uiState.error
    if (errorMsg != null) {
        LaunchedEffect(errorMsg) {
            snackbarHostState.showSnackbar(errorMsg)
            onClearError()
        }
    }

    // Add dialog
    if (uiState.showAddDialog) {
        AddMcpServerDialog(
            onConfirm = onAddServer,
            onDismiss = onDismissAddDialog,
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MCP Servers",
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
                    IconButton(onClick = onShowAddDialog) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Server",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.padding(innerPadding),
        ) {
            if (uiState.isLoading && uiState.mcpServers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.mcpServers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No MCP servers configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(onClick = onShowAddDialog) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Server")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    items(
                        items = uiState.mcpServers.entries.toList(),
                        key = { it.key },
                    ) { (name, status) ->
                        McpServerCard(
                            name = name,
                            status = status,
                            onConnect = { onConnectServer(name) },
                            onDisconnect = { onDisconnectServer(name) },
                            onRemoveAuth = { onRemoveAuth(name) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MCP Server Card
// ---------------------------------------------------------------------------

@Composable
private fun McpServerCard(
    name: String,
    status: McpStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemoveAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            // Row 1: Name + Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = status.status)
            }

            // Error message
            if (status.status == "failed" && !status.error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = status.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Row 2: Action buttons
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status.status == "connected") {
                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Disconnect",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Connect",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                if (status.status == "needs_auth" || status.status == "connected") {
                    OutlinedButton(
                        onClick = onRemoveAuth,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove Auth",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status Badge
// ---------------------------------------------------------------------------

@Composable
private fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (label, containerColor, contentColor) = when (status) {
        "connected" -> Triple(
            "Connected",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
        )
        "needs_auth" -> Triple(
            "Needs Auth",
            Color(0xFFFFF8E1),
            Color(0xFFF57F17),
        )
        "needs_client_registration" -> Triple(
            "Registration",
            Color(0xFFFFF8E1),
            Color(0xFFF57F17),
        )
        "failed" -> Triple(
            "Failed",
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
        )
        "disabled" -> Triple(
            "Disabled",
            Color(0xFFF5F5F5),
            Color(0xFF757575),
        )
        else -> Triple(
            status.replaceFirstChar { it.uppercase() },
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Add MCP Server Dialog
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMcpServerDialog(
    onConfirm: (McpServerCreateRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("local") }
    var command by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add MCP Server",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Server name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Type selector
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Local") },
                            onClick = {
                                selectedType = "local"
                                typeExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remote") },
                            onClick = {
                                selectedType = "remote"
                                typeExpanded = false
                            },
                        )
                    }
                }

                // Command field (for local)
                if (selectedType == "local") {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command") },
                        placeholder = { Text("e.g. npx, -y, @modelcontextprotocol/server-memory") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                        ),
                    )
                }

                // URL field (for remote)
                if (selectedType == "remote") {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                        ),
                    )
                }

                // Enabled toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enabled",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Start server on launch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = McpServerConfig(
                        type = selectedType,
                        command = if (selectedType == "local") {
                            command.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        } else {
                            emptyList()
                        },
                        url = if (selectedType == "remote") url.trim() else "",
                        enabled = enabled,
                    )
                    onConfirm(
                        McpServerCreateRequest(
                            name = name.trim(),
                            config = config,
                        )
                    )
                },
                enabled = name.isNotBlank() &&
                    (selectedType == "local" && command.isNotBlank() ||
                        selectedType == "remote" && url.isNotBlank()),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
