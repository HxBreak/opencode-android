package me.xiaok.opencode.ui.screens.terminal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import me.xiaok.opencode.domain.model.PtyInfo
import me.xiaok.opencode.ui.components.terminal.TerminalView

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless TerminalScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalRoute(
    onNavigateBack: () -> Unit,
    onNavigateToPty: (serverId: String, sessionId: String?, ptyId: String) -> Unit,
    onNavigateToNewTerminal: (serverId: String, sessionId: String?) -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val terminalState by viewModel.terminalState.collectAsStateWithLifecycle()
    val ptySessions by viewModel.ptyList.collectAsStateWithLifecycle()

    var showPtyListDialog by remember { mutableStateOf(false) }

    if (showPtyListDialog) {
        val runningPtys = ptySessions.filter { it.status == "running" }
        PtyListDialog(
            ptys = runningPtys,
            currentPtyId = uiState.ptyId,
            onPtyClick = { pty ->
                showPtyListDialog = false
                onNavigateToPty(viewModel.serverId, viewModel.sessionId, pty.id)
            },
            onPtyDelete = { ptyId ->
                viewModel.deletePty(ptyId)
            },
            onCreateNew = {
                showPtyListDialog = false
                onNavigateToNewTerminal(viewModel.serverId, viewModel.sessionId)
            },
            onDismiss = { showPtyListDialog = false },
        )
    }

    TerminalScreen(
        uiState = uiState,
        terminalState = terminalState,
        onNavigateBack = onNavigateBack,
        onSendTerminalInput = { viewModel.sendTerminalInput(it) },
        onResizeTerminal = { cols, rows -> viewModel.resizeTerminal(cols, rows) },
        onRetry = { viewModel.startTerminal() },
        onTitleLongPress = { showPtyListDialog = true },
    )
}

// ---------------------------------------------------------------------------
// Stateless TerminalScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(
    uiState: TerminalUiState,
    terminalState: TerminalState?,
    onNavigateBack: () -> Unit,
    onSendTerminalInput: (String) -> Unit,
    onResizeTerminal: (Int, Int) -> Unit,
    onRetry: () -> Unit,
    onTitleLongPress: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Terminal",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onTitleLongPress,
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
                .padding(innerPadding),
        ) {
            if (terminalState != null) {
                TerminalView(
                    terminalState = terminalState,
                    modifier = Modifier.fillMaxSize(),
                    onTextInput = onSendTerminalInput,
                    onTerminalResize = { cols, rows -> onResizeTerminal(cols, rows) },
                )
            } else if (uiState.isConnecting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connecting to terminal...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.error ?: "Failed to connect",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.TextButton(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

// Type alias to avoid fully-qualified reference
private typealias TerminalState = me.xiaok.opencode.ui.components.terminal.TerminalState
