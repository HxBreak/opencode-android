package me.xiaok.opencode.ui.screens.chat

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import kotlinx.coroutines.launch
import me.xiaok.opencode.ui.components.common.formatTokenCount
import me.xiaok.opencode.domain.model.BuiltInCommand
import me.xiaok.opencode.domain.model.BuiltInCommands
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.MentionItem
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
    onNavigateToForkedSession: ((String) -> Unit)? = null,
    onNavigateToSession: (String) -> Unit = {},
    onNavigateToNewSession: () -> Unit = {},
    onNavigateToSessionList: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToToolDetail: (String) -> Unit = {},
    onNavigateToSessionDiff: () -> Unit = {},
    onNavigateToFullScreenEditor: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val content by viewModel.sessionContent.collectAsStateWithLifecycle()
    val loading by viewModel.loadingState.collectAsStateWithLifecycle()
    val selection by viewModel.selectionState.collectAsStateWithLifecycle()
    val input by viewModel.inputState.collectAsStateWithLifecycle()
    val stats by viewModel.statsState.collectAsStateWithLifecycle()
    val chatFontSize by viewModel.chatFontSize.collectAsStateWithLifecycle()
    val autoScrollEnabled by viewModel.autoScrollEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var shareDialogUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is ChatUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is ChatUiEvent.ShowShareDialog -> shareDialogUrl = event.url
            }
        }
    }

    // Debug: log state changes for tracking real-time update issues
    LaunchedEffect(content.messages.size, loading.sessionStatus) {
        Log.d("ChatScreen", "state: msgs=${content.messages.size}, turns=${content.turns.size}, status=${loading.sessionStatus}, " +
            "partsKeys=${content.parts.size}, permissions=${content.permissions.size}, " +
            "questions=${content.questions.size}, isLoading=${loading.isLoading}")
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
                "files" -> onNavigateToFiles()
                "settings" -> onNavigateToSettings()
                "mcp" -> onNavigateToMcp()
                "model" -> { /* model picker is a dialog, handled in ChatInputBar */ }
                "agent" -> { /* agent cycling handled in ViewModel */ }
            }
        }
    }

    ChatScreen(
        content = content,
        loading = loading,
        selection = selection,
        input = input,
        stats = stats,
        chatFontSize = chatFontSize,
        autoScrollEnabled = autoScrollEnabled,
        snackbarHostState = snackbarHostState,
        onSendMessage = { viewModel.sendMessage(it) },
        onAbort = { viewModel.abortSession() },
        onReplyPermission = { id, reply -> viewModel.replyPermission(id, reply) },
        onReplyQuestion = { question, answers -> viewModel.replyQuestion(question, answers) },
        onRejectQuestion = { question -> viewModel.rejectQuestion(question) },
        onSaveDraft = { viewModel.saveDraft(it) },
        onReconcileMentions = { viewModel.reconcileMentions(it) },
        onForkSession = { messageId ->
            viewModel.forkSession(messageId) { forkedId ->
                onNavigateToForkedSession?.invoke(forkedId)
            }
        },
        onRevertSession = { viewModel.revertSession(it) },
        onUnrevertSession = { viewModel.unrevertSession() },
        onLoadOlderMessages = { viewModel.loadOlderMessages() },
        onNavigateBack = onNavigateBack,
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
            onNavigateBack()
            viewModel.deleteSession()
        },
        onMentionSelect = { mention, start, end -> viewModel.addMention(mention, start, end) },
        onBuiltInCommand = handleBuiltInCommand,
        onNavigateToToolDetail = onNavigateToToolDetail,
        onNavigateToSessionDiff = onNavigateToSessionDiff,
        onNavigateToFullScreenEditor = onNavigateToFullScreenEditor,
    )

    shareDialogUrl?.let { url ->
        ShareUrlDialog(
            url = url,
            onDismiss = { shareDialogUrl = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    content: ChatContentState,
    loading: ChatLoadingState,
    selection: ChatSelectionState,
    input: ChatInputState,
    stats: ChatStatsState,
    chatFontSize: String,
    autoScrollEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    onSendMessage: (String) -> Unit,
    onAbort: () -> Unit,
    onReplyPermission: (String, String) -> Unit,
    onReplyQuestion: (QuestionRequest, List<List<String>>) -> Unit,
    onRejectQuestion: (QuestionRequest) -> Unit,
    onSaveDraft: (String) -> Unit,
    onReconcileMentions: (String) -> Unit = {},
    onForkSession: (String) -> Unit,
    onRevertSession: (String) -> Unit,
    onUnrevertSession: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onNavigateBack: () -> Unit,
    onRenameSession: (String) -> Unit = {},
    onExportSession: () -> Unit = {},
    onAgentSelected: (String?) -> Unit = {},
    onModelSelected: (ModelRef?) -> Unit = {},
    onVariantSelected: (String?) -> Unit = {},
    onAttachImage: () -> Unit = {},
    onRemoveImage: (Int) -> Unit = {},
    onSearchFiles: suspend (String) -> List<String> = { emptyList() },
    onMentionSelect: (MentionItem, Int, Int) -> Unit = { _, _, _ -> },
    onCopyMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onAutoScrollToggled: () -> Unit = {},
    onNavigateToSession: (String) -> Unit = {},
    onDeleteSession: () -> Unit = {},
    onBuiltInCommand: (BuiltInCommand) -> Unit = {},
    onNavigateToToolDetail: (String) -> Unit = {},
    onNavigateToSessionDiff: () -> Unit = {},
    onNavigateToFullScreenEditor: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember(input.draftText) { mutableStateOf(input.draftText) }
    var showMenu by remember { mutableStateOf(false) }

    // Active permission dialog (show first of each)
    val activePermission = content.permissions.firstOrNull()

    // Revert confirmation dialog
    var revertMessageId by remember { mutableStateOf<String?>(null) }

    var deleteMessageId by remember { mutableStateOf<String?>(null) }

    val onDeleteMessageRemembered = remember {{ id: String -> deleteMessageId = id }}
    val onRevertSessionRemembered = remember {{ id: String -> revertMessageId = id }}

    // Delete session confirmation dialog

    // Rename dialog
    var showRenameDialog by remember { mutableStateOf(false) }

    var previousMessageCount by remember { mutableStateOf(0) }
    var pendingScrollOffset by remember { mutableStateOf(0) }

    var sentinelVisible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(content.childSessions.size) {
        if (content.childSessions.isNotEmpty()) {
            sentinelVisible = true
        }
    }

    LaunchedEffect(FullScreenEditorState.resultText) {
        val editedText = FullScreenEditorState.consumeResult()
        if (editedText != null) {
            inputText = editedText
            onSaveDraft(editedText)
        }
    }

    // Auto-scroll to bottom only when user is near bottom and new content arrives
    val turnsSnapshot = content.turns

    val lastTurn = turnsSnapshot.lastOrNull()
    val contentFingerprint = if (autoScrollEnabled) {
        lastTurn?.partLookup?.values?.sumOf {
            when (it) {
                is Part.Text -> it.text.length
                is Part.Reasoning -> it.text.length
                else -> 1
            }
        } ?: 0
    } else 0

    // Scroll logic: auto-scroll ON = always chase the bottom; OFF = do nothing
    LaunchedEffect(turnsSnapshot.size, contentFingerprint) {
        val newSize = turnsSnapshot.size
        val oldSize = previousMessageCount

        if (!autoScrollEnabled) {
            previousMessageCount = newSize
            if (pendingScrollOffset > 0) {
                listState.scrollToItem(listState.firstVisibleItemIndex + pendingScrollOffset)
                pendingScrollOffset = 0
            }
            return@LaunchedEffect
        }

        if (newSize > oldSize && oldSize > 0 && pendingScrollOffset == 0) {
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastItemIndex >= 0) {
                listState.animateScrollToItem(lastItemIndex)
            }
        } else if (oldSize == 0 && newSize > 0) {
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastItemIndex >= 0) {
                listState.scrollToItem(lastItemIndex)
            }
        } else if (newSize == oldSize && contentFingerprint > 0) {
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            if (lastItemIndex >= 0) {
                listState.animateScrollToItem(lastItemIndex)
            }
        }

        if (pendingScrollOffset > 0) {
            listState.scrollToItem(listState.firstVisibleItemIndex + pendingScrollOffset)
            pendingScrollOffset = 0
        }

        previousMessageCount = newSize
    }

    // When older messages are prepended (isLoadingMore transitions from true→false),
    // we need to offset scroll position to maintain the user's current view.
    // We track the message count before and after to compute the offset.
    LaunchedEffect(loading.isLoadingMore) {
        if (!loading.isLoadingMore && previousMessageCount > 0) {
            val currentSize = turnsSnapshot.size
            if (currentSize > previousMessageCount) {
                val addedCount = currentSize - previousMessageCount
                pendingScrollOffset = addedCount
                previousMessageCount = currentSize
            }
        }
    }

    // Load older messages when scrolled to top
    // Guard: skip if list is still empty (initial layout hasn't happened yet)
    LaunchedEffect(listState.firstVisibleItemIndex, loading.hasOlderMessages, loading.isLoadingMore, loading.isLoading) {
        if (listState.layoutInfo.totalItemsCount > 0 &&
            listState.firstVisibleItemIndex <= 1 &&
            loading.hasOlderMessages &&
            !loading.isLoadingMore &&
            !loading.isLoading
        ) {
            onLoadOlderMessages()
        }
    }

    val currentError = loading.error
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
        val turn = content.turns.find { it.userMessage.id == messageId }
        val messagePreview = turn?.userMessage?.parts
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
            currentTitle = content.session?.title?.ifEmpty { "Chat" } ?: "Chat",
            onConfirm = { newTitle ->
                onRenameSession(newTitle)
            },
            onDismiss = { showRenameDialog = false },
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
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
            ChatTopBar(
                sessionTitle = content.session?.title?.ifEmpty { "Chat" } ?: "Chat",
                totalTokens = stats.totalTokens,
                isShared = content.session?.share != null,
                sessionStatus = loading.sessionStatus,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onNavigateToSessionDiff = onNavigateToSessionDiff,
                onAbort = onAbort,
                onExportSession = onExportSession,
                onRenameSession = { showRenameDialog = true },
                onUnrevertSession = onUnrevertSession,
                onDeleteSession = onDeleteSession,
                hasRevert = content.session?.revert != null,
            )
        },
        bottomBar = {
            Column {
                // Todo sentinel — appears above input bar when todos exist
                var todoSentinelVisible by remember { mutableStateOf(true) }
                AnimatedVisibility(
                    visible = todoSentinelVisible && content.todos.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    TodoSentinel(
                        modifier = Modifier.padding(top = 4.dp),
                        todos = content.todos,
                        onDismiss = { todoSentinelVisible = false },
                    )
                }
                // Reset sentinel visibility when active (non-completed) todos appear
                LaunchedEffect(content.todos.size, content.todos.any { it.status != "completed" && it.status != "cancelled" }) {
                    val hasActive = content.todos.any { it.status != "completed" && it.status != "cancelled" }
                    if (hasActive) {
                        todoSentinelVisible = true
                    }
                }

                ChatInputBar(
                text = inputText,
                onTextChange = { newText ->
                    inputText = newText
                    onSaveDraft(newText)
                    onReconcileMentions(newText)
                },
                onSend = {
                    val trimmed = inputText.trimStart()
                    if (trimmed.startsWith("/") && trimmed.count { it == '/' } == 1) {
                        val commandName = trimmed.removePrefix("/").substringBefore(" ")
                        val cmd = BuiltInCommands.match(commandName)
                        if (cmd != null) {
                            onBuiltInCommand(cmd)
                        } else {
                            // Server-side command → handled by SendMessageUseCase
                            onSendMessage(inputText)
                        }
                    } else {
                        onSendMessage(inputText)
                    }
                    inputText = ""
                },
                sessionStatus = loading.sessionStatus,
                isSending = loading.isSending,
                sessionTitle = content.session?.title?.ifEmpty { "" } ?: "",
                stats = stats,
                selection = selection,
                attachedImages = input.attachedImages,
                onAttachImage = onAttachImage,
                onRemoveImage = onRemoveImage,
                onAgentSelected = onAgentSelected,
                onModelSelected = onModelSelected,
                onVariantSelected = onVariantSelected,
                onBuiltInCommand = onBuiltInCommand,
                onSearchFiles = onSearchFiles,
                onMentionSelect = onMentionSelect,
                mentionDisplayTexts = input.mentions.map { it.displayText }.toSet(),
                onExpand = {
                    FullScreenEditorState.prepare(inputText)
                    onNavigateToFullScreenEditor()
                },
            )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (content.messages.isEmpty() && !loading.isLoading) {
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
                    if (loading.isLoadingMore) {
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
                        items = content.turns,
                        key = { it.turnId },
                    ) { turn ->
                        val isLastTurn = turn.turnId == content.turns.lastOrNull()?.turnId
                        val isActiveSession = loading.sessionStatus is SessionStatus.Busy

                        TurnBubble(
                            turn = turn,
                            onCopyMessage = onCopyMessage,
                            onDeleteMessage = onDeleteMessageRemembered,
                            onForkSession = onForkSession,
                            onRevertSession = onRevertSessionRemembered,
                            onNavigateToSession = onNavigateToSession,
                            onNavigateToToolDetail = onNavigateToToolDetail,
                            fontSize = chatFontSize,
                            isLastTurn = isLastTurn,
                            isActiveSession = isActiveSession,
                        )
                    }

                    // Pending question cards at bottom of message list
                    items(
                        items = content.questions,
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
                            isSubmitting = question.id in loading.submittingQuestionIds,
                        )
                    }

                    // Spacer for bottom bar
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = sentinelVisible && content.childSessions.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                HoverSentinel(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    childSessions = content.childSessions,
                    onNavigateToSession = onNavigateToSession,
                    onDismiss = { sentinelVisible = false },
                )
            }

            // Floating message navigation buttons
            if (content.turns.size > 3) {
                MessageNavigationButtons(
                    listState = listState,
                    turnCount = content.turns.size,
                    isLoadingMore = loading.isLoadingMore,
                    autoScrollEnabled = autoScrollEnabled,
                    onAutoScrollToggled = onAutoScrollToggled,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 12.dp),
                )
            }
        }
    }
}
