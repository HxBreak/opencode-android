package me.xiaok.opencode.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless SettingsScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToIconPreview: () -> Unit = {},
    onNavigateToErrorLog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToIconPreview = onNavigateToIconPreview,
        onNavigateToErrorLog = onNavigateToErrorLog,
        onSetTheme = { viewModel.setTheme(it) },
        onSetReconnectMode = { viewModel.setReconnectMode(it) },
        onSetChatFontSize = { viewModel.setChatFontSize(it) },
        onSetInitialMessages = { viewModel.setInitialMessages(it) },
        onSetImageCompress = { viewModel.setImageCompress(it) },
        onSetNotificationsEnabled = { viewModel.setNotificationsEnabled(it) },
        onClearCacheData = { viewModel.clearCacheData() },
    )
}

// ---------------------------------------------------------------------------
// Stateless SettingsScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onNavigateToIconPreview: () -> Unit = {},
    onNavigateToErrorLog: () -> Unit = {},
    onSetTheme: (String) -> Unit,
    onSetReconnectMode: (String) -> Unit,
    onSetChatFontSize: (String) -> Unit,
    onSetInitialMessages: (Int) -> Unit,
    onSetImageCompress: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onClearCacheData: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showReconnectModeDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "Theme",
            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
            selected = uiState.theme,
            onSelect = { onSetTheme(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showFontSizeDialog) {
        SingleChoiceDialog(
            title = "Chat Font Size",
            options = listOf("small" to "Small", "medium" to "Medium", "large" to "Large"),
            selected = uiState.chatFontSize,
            onSelect = { onSetChatFontSize(it); showFontSizeDialog = false },
            onDismiss = { showFontSizeDialog = false },
        )
    }

    if (showReconnectModeDialog) {
        SingleChoiceDialog(
            title = "Reconnect Mode",
            options = listOf("aggressive" to "Aggressive (5s)", "normal" to "Normal (30s)", "conservative" to "Conservative (60s)"),
            selected = uiState.reconnectMode,
            onSelect = { onSetReconnectMode(it); showReconnectModeDialog = false },
            onDismiss = { showReconnectModeDialog = false },
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    text = "Clear Cache",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            },
            text = {
                Text("This will delete all cached sessions and messages from local storage. Data on the server will not be affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearCacheData()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
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
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // --- General ---
            item {
                SectionHeader(
                    title = "General",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            item {
                ClickableSettingItem(
                    title = "Reconnect mode",
                    subtitle = reconnectModeLabel(uiState.reconnectMode),
                    onClick = { showReconnectModeDialog = true },
                )
            }

            // --- Appearance ---
            item {
                SectionHeader(
                    title = "Appearance",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            item {
                ClickableSettingItem(
                    title = "Theme",
                    subtitle = themeLabel(uiState.theme),
                    onClick = { showThemeDialog = true },
                )
            }
            item {
                ClickableSettingItem(
                    title = "Icon preview",
                    subtitle = "Preview and select AI-generated app icons",
                    onClick = onNavigateToIconPreview,
                )
            }

            // --- Chat Display ---
            item {
                SectionHeader(
                    title = "Chat Display",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            item {
                ClickableSettingItem(
                    title = "Font size",
                    subtitle = fontSizeLabel(uiState.chatFontSize),
                    onClick = { showFontSizeDialog = true },
                )
            }
            item {
                SliderSettingItem(
                    title = "Initial messages",
                    subtitle = "Load ${uiState.initialMessages} messages per session",
                    value = uiState.initialMessages.toFloat(),
                    valueRange = 25f..200f,
                    onValueChange = { onSetInitialMessages(it.toInt()) },
                )
            }

            // --- Images ---
            item {
                SectionHeader(
                    title = "Images",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            item {
                SwitchSettingItem(
                    title = "Compress images",
                    subtitle = "Reduce image size before uploading",
                    checked = uiState.imageCompress,
                    onCheckedChange = onSetImageCompress,
                )
            }

            // --- Notifications ---
            item {
                SectionHeader(
                    title = "Notifications",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            item {
                SwitchSettingItem(
                    title = "Notifications",
                    subtitle = "Receive notifications for session events",
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = onSetNotificationsEnabled,
                )
            }

            // --- Debug ---
            item {
                SectionHeader(
                    title = "Debug",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            item {
                ClickableSettingItem(
                    title = "Error log",
                    subtitle = "View collected error reports",
                    onClick = onNavigateToErrorLog,
                )
            }
            item {
                ClickableSettingItem(
                    title = "Clear cache",
                    subtitle = "Delete all cached sessions and messages",
                    onClick = { showClearCacheDialog = true },
                )
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
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
    icon: @Composable () -> Unit = {},
) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Switch Setting Item
// ---------------------------------------------------------------------------

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

// ---------------------------------------------------------------------------
// Clickable Setting Item (for dropdowns)
// ---------------------------------------------------------------------------

@Composable
private fun ClickableSettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Slider Setting Item
// ---------------------------------------------------------------------------

@Composable
private fun SliderSettingItem(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Single Choice Dialog (for dropdown settings)
// ---------------------------------------------------------------------------

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Label helpers
// ---------------------------------------------------------------------------

private fun themeLabel(value: String): String = when (value) {
    "system" -> "System"
    "light" -> "Light"
    "dark" -> "Dark"
    else -> value
}

private fun fontSizeLabel(value: String): String = when (value) {
    "small" -> "Small"
    "medium" -> "Medium"
    "large" -> "Large"
    else -> value
}

private fun reconnectModeLabel(value: String): String = when (value) {
    "aggressive" -> "Aggressive (5s)"
    "normal" -> "Normal (30s)"
    "conservative" -> "Conservative (60s)"
    else -> value
}
