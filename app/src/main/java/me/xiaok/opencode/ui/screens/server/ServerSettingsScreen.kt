package me.xiaok.opencode.ui.screens.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ---------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToModelFilter: () -> Unit,
    onNavigateToMcpManagement: () -> Unit,
    onNavigateToExperimental: () -> Unit,
    onNavigateToProjectConfig: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val serverName by viewModel.serverName.collectAsStateWithLifecycle()

    ServerSettingsScreen(
        serverName = serverName,
        onNavigateBack = onNavigateBack,
        onNavigateToProviders = onNavigateToProviders,
        onNavigateToModelFilter = onNavigateToModelFilter,
        onNavigateToMcpManagement = onNavigateToMcpManagement,
        onNavigateToExperimental = onNavigateToExperimental,
        onNavigateToProjectConfig = onNavigateToProjectConfig,
    )
}

// ---------------------------------------------------------------------------
// Stateless Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    serverName: String,
    onNavigateBack: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToModelFilter: () -> Unit,
    onNavigateToMcpManagement: () -> Unit,
    onNavigateToExperimental: () -> Unit,
    onNavigateToProjectConfig: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = serverName.ifEmpty { "Server Settings" },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Providers
            SettingsItem(
                icon = { Icon(Icons.Default.Lan, contentDescription = null) },
                title = "Providers",
                subtitle = "Manage AI provider connections and API keys",
                onClick = onNavigateToProviders,
            )

            // Model Filter
            SettingsItem(
                icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                title = "Model Filter",
                subtitle = "Show or hide models in the chat picker",
                onClick = onNavigateToModelFilter,
            )

            // MCP Servers
            SettingsItem(
                icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                title = "MCP Servers",
                subtitle = "Manage Model Context Protocol servers",
                onClick = onNavigateToMcpManagement,
            )

            // Project Config
            SettingsItem(
                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                title = "Project Config",
                subtitle = "Edit project configuration",
                onClick = onNavigateToProjectConfig,
            )

            // Experimental
            SettingsItem(
                icon = { Icon(Icons.Default.Science, contentDescription = null) },
                title = "Experimental",
                subtitle = "Workspaces, worktrees, and experimental features",
                onClick = onNavigateToExperimental,
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(40.dp)) {
                icon()
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
