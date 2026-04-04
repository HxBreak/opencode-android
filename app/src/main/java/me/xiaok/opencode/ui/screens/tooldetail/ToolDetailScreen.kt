package me.xiaok.opencode.ui.screens.tooldetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.xiaok.opencode.domain.model.ToolState

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless ToolDetailScreen
// ---------------------------------------------------------------------------

@Composable
fun ToolDetailRoute(
    onNavigateBack: () -> Unit,
    onNavigateToSession: (String) -> Unit = {},
    viewModel: ToolDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ToolDetailScreen(
        toolName = uiState.toolName,
        state = uiState.state,
        childSessionId = uiState.childSessionId,
        onNavigateBack = onNavigateBack,
        onNavigateToSession = onNavigateToSession,
    )
}

// ---------------------------------------------------------------------------
// Stateless Screen
// ---------------------------------------------------------------------------

private val ColorToolPending = Color(0xFFFFA000)
private val ColorToolRunning = Color(0xFF42A5F5)
private val ColorToolCompleted = Color(0xFF66BB6A)
private val ColorToolError = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailScreen(
    toolName: String,
    state: ToolState,
    childSessionId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToSession: (String) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val (statusColor, statusLabel) = when {
        state.isPending -> ColorToolPending to "Pending"
        state.isRunning -> ColorToolRunning to "Running"
        state.isCompleted -> ColorToolCompleted to "Completed"
        state.isError -> ColorToolError to "Error"
        else -> Color(0xFF9E9E9E) to state.status.replaceFirstChar { it.uppercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Status dot
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = statusColor,
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        // Status pill — only for non-completed states
                        if (!state.isCompleted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Navigate to sub-agent session button
            if (childSessionId != null) {
                Card(
                    onClick = { onNavigateToSession(childSessionId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "View sub-agent session",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Title section
            if (state.title.isNotEmpty()) {
                SectionCard(title = "Title") {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }

            // Input section (raw JSON)
            if (state.input != null) {
                SectionCard(title = "Input") {
                    Text(
                        text = state.input.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Output section
            if (state.output.isNotEmpty()) {
                val isDiff = toolName == "edit" && state.output.contains("\n---") ||
                        state.output.lines().any { it.startsWith("@@") || it.startsWith("+") || it.startsWith("-") }

                if (isDiff) {
                    SectionCard(title = "Changes") {
                        DiffContent(text = state.output)
                    }
                } else {
                    SectionCard(title = "Output") {
                        Text(
                            text = state.output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }

            // Error section
            if (state.error.isNotEmpty()) {
                SectionCard(
                    title = "Error",
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section Card
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Diff Content with syntax highlighting
// ---------------------------------------------------------------------------

@Composable
private fun DiffContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        text.lines().forEach { line ->
            val bgColor = when {
                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                line.startsWith("-") && !line.startsWith("---") -> Color(0xFFE53935).copy(alpha = 0.08f)
                line.startsWith("@@") -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> Color.Transparent
            }
            val textColor = when {
                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2E7D32)
                line.startsWith("-") && !line.startsWith("---") -> Color(0xFFC62828)
                line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            val textStyle = when {
                line.startsWith("@@") -> MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                else -> MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = line,
                style = textStyle,
                color = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}
