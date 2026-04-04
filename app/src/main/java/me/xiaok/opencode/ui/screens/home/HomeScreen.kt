package me.xiaok.opencode.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.ui.theme.StatusConnected
import me.xiaok.opencode.ui.theme.StatusConnecting
import me.xiaok.opencode.ui.theme.StatusError
import java.util.UUID

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless HomeScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onNavigateToProjects: (serverId: String) -> Unit,
    onNavigateToServerSettings: (serverId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onAddServer = { viewModel.addServer(it) },
        onUpdateServer = { viewModel.updateServer(it) },
        onRemoveServer = { viewModel.removeServer(it) },
        onConnect = { viewModel.connect(it) },
        onDisconnect = { viewModel.disconnect(it) },
        onNavigateToProjects = onNavigateToProjects,
        onNavigateToServerSettings = onNavigateToServerSettings,
        onNavigateToSettings = onNavigateToSettings,
    )
}

// ---------------------------------------------------------------------------
// Stateless HomeScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddServer: (ServerConnection) -> Unit,
    onUpdateServer: (ServerConnection) -> Unit,
    onRemoveServer: (serverId: String) -> Unit,
    onConnect: (serverId: String) -> Unit,
    onDisconnect: (serverId: String) -> Unit,
    onNavigateToProjects: (serverId: String) -> Unit,
    onNavigateToServerSettings: (serverId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<ServerConnection?>(null) }

    if (showAddDialog) {
        AddEditServerDialog(
            existingServer = editingServer,
            onSave = { server ->
                if (editingServer != null) {
                    onUpdateServer(server)
                } else {
                    onAddServer(server)
                }
                showAddDialog = false
                editingServer = null
            },
            onDismiss = {
                showAddDialog = false
                editingServer = null
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OpenCode",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add server",
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (uiState.servers.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(innerPadding),
                onAddServer = { showAddDialog = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(
                    items = uiState.servers,
                    key = { it.id },
                ) { server ->
                    val connectionState = uiState.connectionStates[server.id]
                        ?: ServerRepository.ConnectionState.DISCONNECTED

                    ServerCard(
                        server = server,
                        connectionState = connectionState,
                        serverVersion = uiState.serverVersions[server.id],
                        onConnect = { onConnect(server.id) },
                        onDisconnect = { onDisconnect(server.id) },
                        onNavigateToProjects = { onNavigateToProjects(server.id) },
                        onNavigateToServerSettings = { onNavigateToServerSettings(server.id) },
                        onEdit = {
                            editingServer = server
                            showAddDialog = true
                        },
                        onRemove = { onRemoveServer(server.id) },
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

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(
    onAddServer: () -> Unit,
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
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No servers configured",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add a server to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onAddServer) {
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
}

// ---------------------------------------------------------------------------
// Server Card — status dot + name + subtitle, overflow menu for actions
// ---------------------------------------------------------------------------

@Composable
private fun ServerCard(
    server: ServerConnection,
    connectionState: ServerRepository.ConnectionState,
    serverVersion: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToServerSettings: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (connectionState) {
        is ServerRepository.ConnectionState.CONNECTED -> StatusConnected
        is ServerRepository.ConnectionState.CONNECTING -> StatusConnecting
        is ServerRepository.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outlineVariant
        is ServerRepository.ConnectionState.ERROR -> StatusError
    }

    val isConnected = connectionState is ServerRepository.ConnectionState.CONNECTED
    val isConnecting = connectionState is ServerRepository.ConnectionState.CONNECTING
    val isDisconnected = connectionState is ServerRepository.ConnectionState.DISCONNECTED
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isConnected) {
                    Modifier.clickable(onClick = onNavigateToProjects)
                } else if (isDisconnected || connectionState is ServerRepository.ConnectionState.ERROR) {
                    Modifier.clickable(onClick = onConnect)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Name + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = when (connectionState) {
                        is ServerRepository.ConnectionState.CONNECTED -> server.baseUrl.removePrefix("http://").removePrefix("https://")
                        is ServerRepository.ConnectionState.CONNECTING -> "Connecting…"
                        is ServerRepository.ConnectionState.DISCONNECTED -> "Tap to connect"
                        is ServerRepository.ConnectionState.ERROR -> connectionState.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (connectionState) {
                        is ServerRepository.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        is ServerRepository.ConnectionState.CONNECTING -> StatusConnecting
                        is ServerRepository.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        is ServerRepository.ConnectionState.ERROR -> StatusError
                    },
                    maxLines = 1,
                )
            }

            // Version (small, faded, right-aligned)
            if (!serverVersion.isNullOrEmpty()) {
                Text(
                    text = "v$serverVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }

            // Overflow menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (isConnected) {
                        DropdownMenuItem(
                            text = { Text("Projects") },
                            onClick = {
                                showMenu = false
                                onNavigateToProjects()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Server Settings") },
                            onClick = {
                                showMenu = false
                                onNavigateToServerSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            onClick = {
                                showMenu = false
                                onDisconnect()
                            },
                        )
                    }
                    if (!isConnecting) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add / Edit Server Dialog — simplified: Name+URL core, advanced options collapsible
// ---------------------------------------------------------------------------

@Composable
fun AddEditServerDialog(
    existingServer: ServerConnection?,
    onSave: (ServerConnection) -> Unit,
    onDismiss: () -> Unit,
) {
    val isEdit = existingServer != null

    var name by remember { mutableStateOf(existingServer?.name ?: "") }
    var url by remember { mutableStateOf(existingServer?.baseUrl ?: "") }
    var username by remember { mutableStateOf(existingServer?.username ?: "") }
    var password by remember { mutableStateOf(existingServer?.password ?: "") }
    var autoConnect by remember { mutableStateOf(existingServer?.autoConnect ?: true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val nameError = name.isBlank()
    val urlError = url.isBlank()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Server" else "Add Server",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Core fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    isError = urlError,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                        )
                    },
                    supportingText = {
                        Text(
                            text = "http:// will be prepended if no scheme is provided",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Advanced toggle
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                ) {
                    Text(
                        text = if (showAdvanced) "Hide advanced" else "Advanced",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // Advanced fields
                if (showAdvanced) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Auto-connect",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalUrl = if (
                        url.isNotBlank() &&
                        !url.startsWith("http://", ignoreCase = true) &&
                        !url.startsWith("https://", ignoreCase = true)
                    ) {
                        "http://$url"
                    } else {
                        url
                    }

                    val server = ServerConnection(
                        id = existingServer?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        baseUrl = finalUrl.trim(),
                        username = username.trim(),
                        password = password.trim(),
                        autoConnect = autoConnect,
                    )
                    onSave(server)
                },
                enabled = !nameError && !urlError,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
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
