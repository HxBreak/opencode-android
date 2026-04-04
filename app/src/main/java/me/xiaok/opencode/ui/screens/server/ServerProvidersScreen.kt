package me.xiaok.opencode.ui.screens.server

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import me.xiaok.opencode.domain.model.Provider
import me.xiaok.opencode.domain.model.ProviderList
import me.xiaok.opencode.ui.components.common.ProviderIcon

// ---------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerProvidersRoute(
    serverId: String,
    onNavigateBack: () -> Unit,
    viewModel: ServerProvidersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ServerProvidersScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = { viewModel.loadProviders() },
        onConnectWithApiKey = { providerId, apiKey ->
            viewModel.connectWithApiKey(providerId, apiKey)
        },
        onDisconnectProvider = { providerId ->
            viewModel.disconnectProvider(providerId)
        },
        onStartOAuth = { providerId, methodIndex ->
            viewModel.startOAuth(providerId, methodIndex)
        },
        onCompleteOAuth = { providerId, methodIndex, code ->
            viewModel.completeOAuth(providerId, methodIndex, code)
        },
        onClearOAuthState = { viewModel.clearOAuthState() },
        onClearError = { viewModel.clearError() },
    )
}

// ---------------------------------------------------------------------------
// Stateless Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerProvidersScreen(
    uiState: ProvidersUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onConnectWithApiKey: (providerId: String, apiKey: String) -> Unit,
    onDisconnectProvider: (providerId: String) -> Unit,
    onStartOAuth: (providerId: String, methodIndex: Int) -> Unit,
    onCompleteOAuth: (providerId: String, methodIndex: Int, code: String) -> Unit,
    onClearOAuthState: () -> Unit,
    onClearError: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog state
    var showConnectDialog by remember { mutableStateOf<String?>(null) } // providerId
    var showOAuthDialog by remember { mutableStateOf<String?>(null) } // providerId

    // Determine connected vs available providers
    val connectedProviders = uiState.providers.all.filter { provider ->
        provider.id in uiState.providers.connected
    }
    val availableProviders = uiState.providers.all.filter { provider ->
        provider.id !in uiState.providers.connected
    }

    // Error snackbar
    val errorMsg = uiState.error
    if (errorMsg != null) {
        androidx.compose.runtime.LaunchedEffect(errorMsg) {
            snackbarHostState.showSnackbar(errorMsg)
            onClearError()
        }
    }

    // OAuth URL handling — open in browser when URL arrives
    val oauthUrl = uiState.oauthUrl
    if (oauthUrl != null) {
        androidx.compose.runtime.LaunchedEffect(oauthUrl) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl))
            context.startActivity(intent)
        }
    }

    // Connect dialog (API Key)
    if (showConnectDialog != null) {
        val providerId = showConnectDialog!!
        val provider = uiState.providers.all.find { it.id == providerId }
        ApiKeyDialog(
            providerName = provider?.name ?: providerId,
            onConfirm = { apiKey ->
                onConnectWithApiKey(providerId, apiKey)
                showConnectDialog = null
            },
            onDismiss = { showConnectDialog = null },
        )
    }

    // OAuth dialog (paste code)
    if (showOAuthDialog != null) {
        val providerId = showOAuthDialog!!
        val provider = uiState.providers.all.find { it.id == providerId }
        val methodIndex = remember(providerId, uiState.authMethods) {
            findOAuthMethodIndex(uiState.authMethods, providerId)
        }
        OAuthCodeDialog(
            providerName = provider?.name ?: providerId,
            instructions = uiState.oauthInstructions,
            onAuthorize = {
                if (methodIndex != null) {
                    onStartOAuth(providerId, methodIndex)
                }
            },
            onComplete = { code ->
                if (methodIndex != null) {
                    onCompleteOAuth(providerId, methodIndex, code)
                }
                showOAuthDialog = null
            },
            onDismiss = {
                showOAuthDialog = null
                onClearOAuthState()
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Providers",
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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.padding(innerPadding),
        ) {
            if (uiState.isLoading && uiState.providers.all.isEmpty()) {
                // Full-screen loading
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.providers.all.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No providers found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    // Connected section
                    if (connectedProviders.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Connected")
                        }
                        items(
                            items = connectedProviders,
                            key = { it.id },
                        ) { provider ->
                            ProviderCard(
                                provider = provider,
                                isConnected = true,
                                onDisconnect = { onDisconnectProvider(provider.id) },
                                onConnect = {},
                            )
                        }
                    }

                    // Available section
                    if (availableProviders.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Available")
                        }
                        items(
                            items = availableProviders,
                            key = { it.id },
                        ) { provider ->
                            ProviderCard(
                                provider = provider,
                                isConnected = false,
                                onDisconnect = {},
                                onConnect = {
                                    val authMethods = uiState.authMethods
                                    val hasOAuth = hasOAuthMethod(authMethods, provider.id)
                                    if (hasOAuth) {
                                        showOAuthDialog = provider.id
                                    } else {
                                        showConnectDialog = provider.id
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section Header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

// ---------------------------------------------------------------------------
// Provider Card
// ---------------------------------------------------------------------------

@Composable
private fun ProviderCard(
    provider: Provider,
    isConnected: Boolean,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
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
            // Row 1: Icon + Name + Source badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderIcon(
                    providerId = provider.id,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = provider.name.ifEmpty { provider.id },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                SourceBadge(source = provider.source)
            }

            // Row 2: Model count + names preview
            val modelCount = provider.models.size
            if (modelCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$modelCount model${if (modelCount != 1) "s" else ""}" +
                        if (modelCount <= 3) {
                            ": ${provider.models.keys.joinToString(", ")}"
                        } else {
                            ": ${provider.models.keys.take(3).joinToString(", ")} +${modelCount - 3} more"
                        },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Row 3: Action button
            Spacer(modifier = Modifier.height(12.dp))
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Disconnect",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Source Badge
// ---------------------------------------------------------------------------

@Composable
private fun SourceBadge(
    source: String,
    modifier: Modifier = Modifier,
) {
    if (source.isEmpty()) return

    val (label, containerColor, contentColor) = when (source) {
        "env" -> Triple(
            "Environment",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
        )
        "config" -> Triple(
            "Config",
            Color(0xFFE3F2FD),
            Color(0xFF1565C0),
        )
        else -> Triple(
            source.replaceFirstChar { it.uppercase() },
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
// API Key Dialog
// ---------------------------------------------------------------------------

@Composable
private fun ApiKeyDialog(
    providerName: String,
    onConfirm: (apiKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect to $providerName",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter your API key to connect this provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
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
                                    "Hide API key"
                                } else {
                                    "Show API key"
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(apiKey.trim()) },
                enabled = apiKey.isNotBlank(),
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
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// OAuth Code Dialog
// ---------------------------------------------------------------------------

@Composable
private fun OAuthCodeDialog(
    providerName: String,
    instructions: String?,
    onAuthorize: () -> Unit,
    onComplete: (code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect to $providerName",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (instructions != null) {
                    Text(
                        text = instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Authorize this app to access your $providerName account, then paste the authorization code below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalButton(
                    onClick = onAuthorize,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorize in Browser")
                }

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Authorization Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onComplete(code.trim()) },
                enabled = code.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Submit")
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
// Auth Method Helpers
// ---------------------------------------------------------------------------

/**
 * Check if a provider has OAuth auth methods defined.
 * Auth methods JSON structure (expected):
 * { "<providerId>": [ { "type": "api" | "oauth", ... }, ... ] }
 */
private fun hasOAuthMethod(authMethods: JsonElement?, providerId: String): Boolean {
    if (authMethods == null) return false
    return try {
        val methods = authMethods.jsonObject[providerId]?.jsonArray ?: return false
        methods.any { element ->
            val obj = element.jsonObject
            obj["type"]?.jsonPrimitive?.content == "oauth"
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Find the index of the first OAuth method for a provider.
 */
private fun findOAuthMethodIndex(authMethods: JsonElement?, providerId: String): Int? {
    if (authMethods == null) return null
    return try {
        val methods = authMethods.jsonObject[providerId]?.jsonArray ?: return null
        methods.indexOfFirst { element ->
            val obj = element.jsonObject
            obj["type"]?.jsonPrimitive?.content == "oauth"
        }.takeIf { it >= 0 }
    } catch (_: Exception) {
        null
    }
}
