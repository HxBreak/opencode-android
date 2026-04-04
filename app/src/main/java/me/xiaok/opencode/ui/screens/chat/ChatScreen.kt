package me.xiaok.opencode.ui.screens.chat

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import kotlinx.coroutines.launch
import me.xiaok.opencode.domain.model.BuiltInCommand
import me.xiaok.opencode.domain.model.FileDiff
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.domain.model.Part
import me.xiaok.opencode.domain.model.QuestionRequest
import me.xiaok.opencode.domain.model.SessionStatus

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless ChatScreen
// ---------------------------------------------------------------------------

@Composable
fun ChatRoute(
    serverId: String,
    sessionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToForkedSession: ((String) -> Unit)? = null,
    onNavigateToSession: (String) -> Unit = {},
    onNavigateToNewSession: () -> Unit = {},
    onNavigateToSessionList: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToToolDetail: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Debug: log state changes for tracking real-time update issues
    LaunchedEffect(uiState.messages.size, uiState.sessionStatus) {
        Log.d("ChatScreen", "uiState: msgs=${uiState.messages.size}, status=${uiState.sessionStatus}, " +
            "partsKeys=${uiState.parts.size}, permissions=${uiState.permissions.size}, " +
            "questions=${uiState.questions.size}, isLoading=${uiState.isLoading}")
    }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.attachImage(it) }
        },
    )

    // Built-in command handler
    val handleBuiltInCommand: (BuiltInCommand) -> Unit = { cmd ->
        val handled = viewModel.executeBuiltInCommand(cmd)
        if (!handled) {
            // Navigation commands — handled by caller
            when (cmd.id) {
                "new" -> onNavigateToNewSession()
                "sessions" -> onNavigateToSessionList()
                "terminal" -> onNavigateToTerminal()
                "files" -> onNavigateToFiles()
                "settings" -> onNavigateToSettings()
                "mcp" -> onNavigateToMcp()
                "model" -> { /* model picker is a dialog, handled in ChatInputBar */ }
                "agent" -> { /* agent cycling handled in ViewModel */ }
            }
        }
    }

    ChatScreen(
        uiState = uiState,
        onSendMessage = { viewModel.sendMessage(it) },
        onAbort = { viewModel.abortSession() },
        onReplyPermission = { id, reply -> viewModel.replyPermission(id, reply) },
        onReplyQuestion = { question, answers -> viewModel.replyQuestion(question, answers) },
        onRejectQuestion = { question -> viewModel.rejectQuestion(question) },
        onSaveDraft = { viewModel.saveDraft(it) },
        onForkSession = { messageId ->
            viewModel.forkSession(messageId) { forkedId ->
                onNavigateToForkedSession?.invoke(forkedId)
            }
        },
        onCopySessionUrl = {
            val url = uiState.sessionWebUrl
            if (url.isNotEmpty()) {
                clipboardManager.setText(AnnotatedString(url))
                Toast.makeText(context, "Session URL copied", Toast.LENGTH_SHORT).show()
            }
        },
        onRevertSession = { viewModel.revertSession(it) },
        onUnrevertSession = { viewModel.unrevertSession() },
        onLoadOlderMessages = { viewModel.loadOlderMessages() },
        onNavigateBack = onNavigateBack,
        onNavigateToTerminal = onNavigateToTerminal,
        onAgentSelected = { viewModel.selectAgent(it) },
        onModelSelected = { viewModel.selectModel(it) },
        onVariantSelected = { viewModel.selectVariant(it) },
        onAttachImage = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onRemoveImage = { viewModel.removeImage(it) },
        onSearchFiles = { query -> viewModel.searchFiles(query) },
        onRenameSession = { newTitle -> viewModel.renameSession(newTitle) },
        onCopyMessage = { text ->
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        },
        onDeleteMessage = { messageId -> viewModel.deleteMessage(messageId) },
        onDismissDiffs = { viewModel.dismissDiffs() },
        onExportSession = {
            scope.launch {
                try {
                    val markdown = viewModel.exportSession()
                    // Share via Android share sheet
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, markdown)
                        type = "text/markdown"
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(sendIntent, "Export chat")
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onAutoScrollToggled = { viewModel.toggleAutoScroll() },
        onNavigateToSession = onNavigateToSession,
        onDeleteSession = {
            viewModel.deleteSession {
                onNavigateBack()
            }
        },
        onBuiltInCommand = handleBuiltInCommand,
        onNavigateToToolDetail = onNavigateToToolDetail,
    )
}

// ---------------------------------------------------------------------------
// Stateless ChatScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onAbort: () -> Unit,
    onReplyPermission: (String, String) -> Unit,
    onReplyQuestion: (QuestionRequest, List<List<String>>) -> Unit,
    onRejectQuestion: (QuestionRequest) -> Unit,
    onSaveDraft: (String) -> Unit,
    onForkSession: (String) -> Unit,
    onCopySessionUrl: () -> Unit = {},
    onRevertSession: (String) -> Unit,
    onUnrevertSession: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    onRenameSession: (String) -> Unit = {},
    onExportSession: () -> Unit = {},
    onAgentSelected: (String?) -> Unit = {},
    onModelSelected: (ModelRef?) -> Unit = {},
    onVariantSelected: (String?) -> Unit = {},
    onAttachImage: () -> Unit = {},
    onRemoveImage: (Int) -> Unit = {},
    onSearchFiles: suspend (String) -> List<String> = { emptyList() },
    onCopyMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onDismissDiffs: () -> Unit = {},
    onAutoScrollToggled: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    onDeleteSession: () -> Unit = {},
    onBuiltInCommand: (BuiltInCommand) -> Unit = {},
    onNavigateToToolDetail: (String) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var inputText by remember(uiState.draftText) { mutableStateOf(uiState.draftText) }
    var showMenu by remember { mutableStateOf(false) }

    // Active permission dialog (show first of each)
    val activePermission = uiState.permissions.firstOrNull()

    // Revert confirmation dialog
    var revertMessageId by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialog
    var deleteMessageId by remember { mutableStateOf<String?>(null) }

    // Delete session confirmation dialog
    var showDeleteSessionDialog by remember { mutableStateOf(false) }

    // Rename dialog
    var showRenameDialog by remember { mutableStateOf(false) }

    var previousMessageCount by remember { mutableStateOf(0) }
    var pendingScrollOffset by remember { mutableStateOf(0) }

    // Auto-scroll to bottom only when user is near bottom and new messages arrive
    val messagesSnapshot = uiState.messages

    // Derive a content fingerprint that changes on any content update (text + non-text parts)
    val lastMessageId = messagesSnapshot.lastOrNull()?.id
    val lastParts = lastMessageId?.let { uiState.parts[it] }
    val contentFingerprint = if (uiState.autoScrollEnabled) {
        lastParts?.sumOf {
            when (it) {
                is Part.Text -> it.text.length
                is Part.Reasoning -> it.text.length
                else -> 1 // non-text parts change height without changing text length
            }
        } ?: 0
    } else 0

    // Scroll logic: auto-scroll ON = always chase the bottom; OFF = do nothing
    LaunchedEffect(messagesSnapshot.size, contentFingerprint) {
        val newSize = messagesSnapshot.size
        val oldSize = previousMessageCount

        if (!uiState.autoScrollEnabled) {
            // Auto-scroll OFF: still track count, but never scroll
            // (only exception: prepend offset below)
            previousMessageCount = newSize
            // Apply pending scroll offset from prepend even when auto-scroll is off
            if (pendingScrollOffset > 0) {
                listState.scrollToItem(listState.firstVisibleItemIndex + pendingScrollOffset)
                pendingScrollOffset = 0
            }
            return@LaunchedEffect
        }

        if (newSize > oldSize && oldSize > 0 && pendingScrollOffset == 0) {
            // New messages arrived — scroll to the very last item in the list
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastItemIndex >= 0) {
                listState.animateScrollToItem(lastItemIndex)
            }
        } else if (oldSize == 0 && newSize > 0) {
            // Initial load — always scroll to bottom
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastItemIndex >= 0) {
                listState.scrollToItem(lastItemIndex)
            }
        } else if (newSize == oldSize && contentFingerprint > 0) {
            // Streaming update (same message count, content changed) — chase bottom
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastItemIndex >= 0) {
                listState.animateScrollToItem(lastItemIndex)
            }
        }

        // Apply pending scroll offset from prepend
        if (pendingScrollOffset > 0) {
            listState.scrollToItem(listState.firstVisibleItemIndex + pendingScrollOffset)
            pendingScrollOffset = 0
        }

        previousMessageCount = newSize
    }

    // When older messages are prepended (isLoadingMore transitions from true→false),
    // we need to offset scroll position to maintain the user's current view.
    // We track the message count before and after to compute the offset.
    LaunchedEffect(uiState.isLoadingMore) {
        if (!uiState.isLoadingMore && previousMessageCount > 0) {
            val currentSize = messagesSnapshot.size
            if (currentSize > previousMessageCount) {
                val addedCount = currentSize - previousMessageCount
                pendingScrollOffset = addedCount
                previousMessageCount = currentSize
            }
        }
    }

    // Load older messages when scrolled to top
    // Guard: skip if list is still empty (initial layout hasn't happened yet)
    LaunchedEffect(listState.firstVisibleItemIndex, uiState.hasOlderMessages, uiState.isLoadingMore) {
        if (listState.layoutInfo.totalItemsCount > 0 &&
            listState.firstVisibleItemIndex <= 1 &&
            uiState.hasOlderMessages &&
            !uiState.isLoadingMore &&
            !uiState.isLoading
        ) {
            onLoadOlderMessages()
        }
    }

    // Show error as Snackbar
    val currentError = uiState.error
    LaunchedEffect(currentError) {
        if (currentError != null) {
            snackbarHostState.showSnackbar(currentError)
        }
    }

    // Permission dialog
    activePermission?.let { request ->
        PermissionDialog(
            request = request,
            onReply = onReplyPermission,
            onDismiss = {},
        )
    }





    // Revert confirmation dialog
    revertMessageId?.let { messageId ->
        val message = uiState.messages.find { it.id == messageId }
        val messagePreview = message?.parts
            ?.filterIsInstance<Part.Text>()
            ?.firstOrNull()
            ?.text
            ?: ""

        RevertConfirmationDialog(
            messagePreview = messagePreview,
            onConfirm = { onRevertSession(messageId) },
            onDismiss = { revertMessageId = null },
        )
    }

    // Rename session dialog
    if (showRenameDialog) {
        RenameSessionDialog(
            currentTitle = uiState.session?.title?.ifEmpty { "Chat" } ?: "Chat",
            onConfirm = { newTitle ->
                onRenameSession(newTitle)
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    // Delete session confirmation dialog
    if (showDeleteSessionDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSessionDialog = false },
            title = { Text("Delete session") },
            text = { Text("Are you sure you want to delete this session? All messages and history will be permanently removed. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSessionDialog = false
                        onDeleteSession()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSessionDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete confirmation dialog
    deleteMessageId?.let { messageId ->
        AlertDialog(
            onDismissRequest = { deleteMessageId = null },
            title = { Text("Delete message") },
            text = { Text("Are you sure you want to delete this message? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMessage(messageId)
                        deleteMessageId = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteMessageId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.session?.title?.ifEmpty { "Chat" } ?: "Chat",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        if (uiState.sessionStatus != SessionStatus.IDLE) {
                            Text(
                                text = when (uiState.sessionStatus) {
                                    SessionStatus.BUSY -> "Working..."
                                    SessionStatus.RETRY -> "Retrying..."
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
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
                actions = {
                    // Terminal navigation
                    IconButton(onClick = onNavigateToTerminal) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Terminal",
                        )
                    }
                    if (uiState.sessionStatus != SessionStatus.IDLE) {
                        IconButton(onClick = onAbort) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Session URL") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onCopySessionUrl()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onExportSession()
                                },
                            )
                            // Unrevert only if session has revert info
                            if (uiState.session?.revert != null) {
                                DropdownMenuItem(
                                    text = { Text("Unrevert") },
                                    onClick = {
                                        showMenu = false
                                        onUnrevertSession()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteSessionDialog = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { newText ->
                    inputText = newText
                    onSaveDraft(newText)
                },
                onSend = {
                    onSendMessage(inputText)
                    inputText = ""
                },
                sessionStatus = uiState.sessionStatus,
                isSending = uiState.isSending,
                sessionTitle = uiState.session?.title?.ifEmpty { "" } ?: "",
                contextUsagePercent = uiState.contextUsagePercent,
                totalTokens = uiState.totalTokens,
                totalCost = uiState.totalCost,
                conversationTurns = uiState.conversationTurns,
                agents = uiState.agents,
                selectedAgent = uiState.selectedAgent,
                onAgentSelected = onAgentSelected,
                providers = uiState.providers,
                selectedModel = uiState.selectedModel,
                onModelSelected = onModelSelected,
                selectedVariant = uiState.selectedVariant,
                variants = uiState.selectedModel?.let { ref ->
                    uiState.providers
                        .find { it.id == ref.providerID }
                        ?.models?.get(ref.modelID)
                        ?.variantNames ?: emptyList()
                } ?: emptyList(),
                onVariantSelected = onVariantSelected,
                attachedImages = uiState.attachedImages,
                onAttachImage = onAttachImage,
                onRemoveImage = onRemoveImage,
                commands = uiState.commands,
                onBuiltInCommand = onBuiltInCommand,
                onSearchFiles = onSearchFiles,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Session diff banner
                if (uiState.sessionDiffs.isNotEmpty()) {
                    SessionDiffCard(
                        diffs = uiState.sessionDiffs,
                        onDismiss = onDismissDiffs,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                if (uiState.messages.isEmpty() && !uiState.isLoading) {
                    ChatEmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                    ) {
                        // Loading indicator at top when fetching older messages
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }

                        items(
                            items = uiState.messages,
                            key = { it.id },
                        ) { message ->
                            val messageParts = uiState.parts[message.id] ?: emptyList()
                            val isLastMessage = message.id == uiState.messages.lastOrNull()?.id
                            val isActiveSession = uiState.sessionStatus == SessionStatus.BUSY
                            val hasReasoningPart = messageParts.any { it is Part.Reasoning }
                            val hasTextPart = messageParts.any { it is Part.Text }
                            val isLatestActiveReasoning = isLastMessage && isActiveSession && hasReasoningPart && !hasTextPart

                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                MessageBubble(
                                    message = message,
                                    parts = uiState.parts[message.id] ?: emptyList(),
                                    onMenuClick = { showMenu = true },
                                    onNavigateToSession = onNavigateToSession,
                                    childSessionIds = uiState.childSessionIds,
                                    fontSize = uiState.chatFontSize,
                                    onQuestionClick = {
                                        // Question tool card click — question is shown inline at list bottom
                                        Log.d("ChatScreen", "onQuestionClick: questions=${uiState.questions.size}")
                                    },
                                    onNavigateToToolDetail = onNavigateToToolDetail,
                                    isLatestActiveReasoning = isLatestActiveReasoning,
                                )
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    // Copy message text
                                    DropdownMenuItem(
                                        text = { Text("Copy") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            val text = (uiState.parts[message.id] ?: message.parts)
                                                .filterIsInstance<Part.Text>()
                                                .joinToString("\n") { it.text }
                                            onCopyMessage(text)
                                        },
                                    )
                                    // Delete message
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            deleteMessageId = message.id
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fork from here") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Default.CallSplit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onForkSession(message.id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Revert to here") },
                                        onClick = {
                                            showMenu = false
                                            revertMessageId = message.id
                                        },
                                    )
                                }
                            }
                        }

                        // Pending question cards at bottom of message list
                        items(
                            items = uiState.questions,
                            key = { "question_${it.id}" },
                        ) { question ->
                            QuestionCard(
                                question = question,
                                onSubmit = { answers ->
                                    onReplyQuestion(question, answers)
                                },
                                onReject = {
                                    onRejectQuestion(question)
                                },
                                isSubmitting = question.id in uiState.submittingQuestionIds,
                            )
                        }

                        // Spacer for bottom bar
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Floating message navigation buttons
            if (uiState.messages.size > 3) {
                MessageNavigationButtons(
                    listState = listState,
                    messageCount = uiState.messages.size,
                    isLoadingMore = uiState.isLoadingMore,
                    autoScrollEnabled = uiState.autoScrollEnabled,
                    onAutoScrollToggled = onAutoScrollToggled,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 12.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Message Bubble
// ---------------------------------------------------------------------------

@Composable
private fun MessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    when {
        message.isUser -> {
            val allParts = parts.ifEmpty { message.parts }
            val hasCompactionOnly = allParts.isNotEmpty() && allParts.all { it is Part.Compaction }
            if (hasCompactionOnly) {
                // Compaction-only user message → render as divider (matches Web UI behavior)
                allParts.filterIsInstance<Part.Compaction>().forEach { part ->
                    PartRenderer(part = part)
                }
            } else {
                UserMessageBubble(message = message, parts = parts, onMenuClick = onMenuClick)
            }
        }
        message.isAssistant -> AssistantMessageBubble(
            message = message,
            parts = parts,
            onMenuClick = onMenuClick,
            onNavigateToSession = onNavigateToSession,
            childSessionIds = childSessionIds,
            fontSize = fontSize,
            onQuestionClick = onQuestionClick,
            onNavigateToToolDetail = onNavigateToToolDetail,
            isLatestActiveReasoning = isLatestActiveReasoning,
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp,
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onMenuClick() }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Prefer SSE-streamed parts (uiState.parts), fallback to message.parts
                val textParts = (parts.ifEmpty { message.parts }).filterIsInstance<Part.Text>()
                textParts.forEach { part ->
                    if (part.text.isNotEmpty()) {
                        Text(
                            text = part.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: Message,
    parts: List<Part>,
    onMenuClick: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    childSessionIds: Map<String, String> = emptyMap(),
    fontSize: String = "medium",
    onQuestionClick: (() -> Unit)? = null,
    onNavigateToToolDetail: (String) -> Unit = {},
    isLatestActiveReasoning: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onMenuClick() }
                )
            }
    ) {
        // Error indicator
        val errorInfo = message.info.error
        if (errorInfo != null && errorInfo.message.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorInfo.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Parts
        parts.forEach { part ->
            PartRenderer(
                part = part,
                onNavigateToSession = onNavigateToSession,
                childSessionIds = childSessionIds,
                fontSize = fontSize,
                onNavigateToToolDetail = onNavigateToToolDetail,
                isLatestActiveReasoning = isLatestActiveReasoning,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun ChatEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Send a message to begin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Message Navigation Buttons
// ---------------------------------------------------------------------------

@Composable
private fun MessageNavigationButtons(
    listState: LazyListState,
    messageCount: Int,
    isLoadingMore: Boolean,
    autoScrollEnabled: Boolean,
    onAutoScrollToggled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offset = if (isLoadingMore) 1 else 0 // loading indicator occupies 1 item

    // Calculate current visible message range
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    val firstVisibleItem = visibleItems.firstOrNull()?.index ?: 0

    // Map LazyColumn item indices to message indices
    val firstVisibleMessageIndex = (firstVisibleItem - offset).coerceIn(0, messageCount - 1)

    // "Current" message = first fully visible message (user's reading position)
    val currentMessageIndex = firstVisibleMessageIndex.coerceIn(0, messageCount - 1)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Previous message
        SmallFabButton(
            onClick = {
                scope.launch {
                    val target = (currentMessageIndex - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(target + offset)
                }
            },
            enabled = currentMessageIndex > 0,
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Previous message",
        )

        // Auto-scroll toggle — ON = always chase bottom; OFF = no auto-scroll
        SmallFabButton(
            onClick = {
                onAutoScrollToggled()
                if (!autoScrollEnabled) {
                    // Turning ON: scroll to bottom immediately
                    scope.launch {
                        val lastItem = listState.layoutInfo.totalItemsCount - 1
                        if (lastItem >= 0) {
                            listState.animateScrollToItem(lastItem)
                        }
                    }
                }
            },
            enabled = true,
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = if (autoScrollEnabled) "Auto-scroll ON" else "Auto-scroll OFF",
            isActive = autoScrollEnabled,
        )
    }
}

@Composable
private fun SmallFabButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    Surface(
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = when {
            isActive -> MaterialTheme.colorScheme.primary
            enabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when {
            isActive -> MaterialTheme.colorScheme.onPrimary
            enabled -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        shadowElevation = 3.dp,
        onClick = if (enabled) onClick else ({}),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Session Diff Card
// ---------------------------------------------------------------------------

@Composable
private fun SessionDiffCard(
    diffs: List<FileDiff>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val totalAdd = diffs.sumOf { it.additions }
    val totalDel = diffs.sumOf { it.deletions }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${diffs.size} file${if (diffs.size != 1) "s" else ""} changed",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = buildAnnotatedString {
                        if (totalAdd > 0) {
                            withStyle(SpanStyle(color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)) {
                                append("+$totalAdd")
                            }
                        }
                        if (totalAdd > 0 && totalDel > 0) append(" ")
                        if (totalDel > 0) {
                            withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.Medium)) {
                                append("-$totalDel")
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    diffs.forEach { diff ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = diff.path,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                if (diff.additions > 0) {
                                    Text(
                                        text = "+${diff.additions}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50),
                                    )
                                }
                                if (diff.deletions > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "-${diff.deletions}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFE53935),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
