package me.xiaok.opencode.ui.screens.chat
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.DraftRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.domain.model.MentionItem
import me.xiaok.opencode.domain.model.Todo
import me.xiaok.opencode.ui.screens.chat.usecases.ChatCommandUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.DraftManagementUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.MentionManagementUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.MessageLoadingUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.ModelSelectionUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.PermissionQuestionUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.SendMessageUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.SessionNavigationUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.SessionOpsUseCase
import me.xiaok.opencode.ui.screens.chat.usecases.SessionStatsUseCase
import me.xiaok.opencode.utils.ErrorCollector
import me.xiaok.opencode.utils.ImageCompressor
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    internal val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val draftRepository: DraftRepository,
    private val settingsRepository: SettingsRepository,
    private val imageCompressor: ImageCompressor,
    internal val errorCollector: ErrorCollector,
    private val sessionStatsUseCase: SessionStatsUseCase,
    private val draftManagementUseCase: DraftManagementUseCase,
    private val mentionManagementUseCase: MentionManagementUseCase,
    private val permissionQuestionUseCase: PermissionQuestionUseCase,
    private val sessionNavigationUseCase: SessionNavigationUseCase,
    internal val sessionOpsUseCase: SessionOpsUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    internal val modelSelectionUseCase: ModelSelectionUseCase,
    private val messageLoadingUseCase: MessageLoadingUseCase,
    private val chatCommandUseCase: ChatCommandUseCase,
) : ViewModel() {

    internal val vmScope: CoroutineScope get() = viewModelScope

    internal val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")
    internal val sessionId: String = savedStateHandle["sessionId"]
        ?: throw IllegalArgumentException("sessionId is required")

    private val _isSending = MutableStateFlow(false)
    internal val _error = MutableStateFlow<String?>(null)
    private val _commandMessage = MutableStateFlow<String?>(null)
    private val _commandMessageId = MutableStateFlow(0L)
    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasOlderMessages = MutableStateFlow(true)
    private var _nextCursor: String? = null

    private val _submittingQuestionIds = MutableStateFlow<Set<String>>(emptySet())
    private var _draftCleared = false
    private var _draftSelectionRestored = false

    private val _attachedImages = MutableStateFlow<List<AttachedImage>>(emptyList())
    private val _mentions = MutableStateFlow<List<MentionItem>>(emptyList())
    private val _autoScrollEnabled = MutableStateFlow(true)
    private val _shareUrl = MutableStateFlow<String?>(null)
    private val _chatFontSize = settingsRepository.chatFontSize.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), "medium"
    )

    private val _selectorState = combine(
        modelSelectionUseCase.providers,
        modelSelectionUseCase.agents,
        modelSelectionUseCase.commands,
        modelSelectionUseCase.selectedAgent,
        modelSelectionUseCase.selectedModel,
    ) { providers, agents, commands, selAgent, selModel ->
        SelectorPartialState(providers, agents, commands, selAgent, selModel)
    }

    private val sessionStats: Flow<SessionStatsUseCase.SessionStats> = combine(
        eventReducer.messages.map { it[sessionId] ?: emptyList() },
        modelSelectionUseCase.providers,
        modelSelectionUseCase.selectedModel,
    ) { messages, providers, selectedModel ->
        sessionStatsUseCase.computeSessionStats(messages, providers, selectedModel)
    }.distinctUntilChanged()

    private val childSessionIdsFlow: Flow<Map<String, String>> = combine(
        eventReducer.messages.map { it[sessionId] ?: emptyList() },
        eventReducer.parts,
        eventReducer.sessions,
    ) { messages, allParts, allSessions ->
        computeChildSessionIds(sessionId, messages, allParts, allSessions)
    }.distinctUntilChanged()

    private val childSessionsFlow: Flow<List<ChildSessionInfo>> = combine(
        eventReducer.sessions,
        eventReducer.sessionStatuses,
    ) { allSessions, allStatuses ->
        allSessions.values
            .filter { it.parentID == sessionId }
            .sortedBy { it.time.created }
            .map { child ->
                ChildSessionInfo(child, allStatuses[child.id] ?: SessionStatus.Idle)
            }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = combine(
        eventReducer.sessions.map { it[sessionId] },
        eventReducer.messages.map { it[sessionId] ?: emptyList() },
        eventReducer.parts,
        eventReducer.permissions.map { it[sessionId] ?: emptyList() },
        eventReducer.questions,
        eventReducer.sessionDiffs.map { it[sessionId] ?: emptyList() },
        eventReducer.sessions,
    ) { array ->
        val session = array[0] as Session?
        val messages = array[1] as List<Message>
        val parts = array[2] as Map<String, List<Part>>
        val permissions = array[3] as List<PermissionRequest>
        val allQuestions = array[4] as Map<String, List<QuestionRequest>>
        val diffs = array[5] as List<FileDiff>
        val allSessions = array[6] as Map<String, Session>

        val descendantIds = findAllDescendants(sessionId, allSessions)
        val mergedQuestions = (listOf(sessionId) + descendantIds)
            .flatMap { sid -> allQuestions[sid] ?: emptyList() }

        val currentSubmitting = _submittingQuestionIds.value
        if (currentSubmitting.isNotEmpty()) {
            val activeIds = mergedQuestions.map { it.id }.toSet()
            val staleIds = currentSubmitting - activeIds
            if (staleIds.isNotEmpty()) {
                _submittingQuestionIds.value = currentSubmitting - staleIds
            }
        }

        Log.d(TAG, "combine: sessionId=$sessionId, questions=${mergedQuestions.size}, descendants=${descendantIds.size}, sessions=${allSessions.size}")
        ChatPartialState(session, messages, parts, permissions, mergedQuestions, diffs, allSessions)
    }.map { partial ->
        partial to groupMessagesIntoTurns(partial.messages, partial.parts)
    }.distinctUntilChanged { (old, _), (new, _) ->
        old == new
    }
    .conflate()
    .flatMapLatest { (partial, turns) ->
        combine(
            eventReducer.sessionStatuses.map { it[sessionId] ?: SessionStatus.Idle },
            _isLoading,
            _isLoadingMore,
            _isSending,
            _error,
        ) { status, loading, loadingMore, sending, err ->
            ChatLoadingState(status, loading, loadingMore, sending, err)
        }.flatMapLatest { loading ->
            combine(
                _selectorState,
                modelSelectionUseCase.selectedVariant,
            ) { selector, selVariant ->
                SelectorState(selector, selVariant)
            }.flatMapLatest { selector ->
                _attachedImages.flatMapLatest { images ->
                    val todosFlow = eventReducer.todos.map { it[sessionId] ?: emptyList() }
                    combine(
                        combine(draftRepository.getDraft(sessionId), sessionStats, childSessionIdsFlow, childSessionsFlow, eventReducer.ptySessions) { draft, stats, subSessionIds, childSessions, allPtys ->
                            DraftCombineResult(draft, stats, subSessionIds, childSessions, allPtys)
                        },
                        todosFlow,
                    ) { draftResult, todos ->
                        val draft = draftResult.draft
                        val stats = draftResult.stats
                        val subSessionIds = draftResult.subSessionIds
                        val childSessions = draftResult.childSessions
                        val allPtys = draftResult.allPtys
                        if (draft != null && !_draftSelectionRestored) {
                            _draftSelectionRestored = true
                            if (draft.selectedAgent != null) {
                                modelSelectionUseCase.selectedAgent.value = draft.selectedAgent
                            }
                            if (draft.selectedModel != null) {
                                modelSelectionUseCase.selectedModel.value = draft.selectedModel
                            }
                            if (draft.selectedVariant != null) {
                                modelSelectionUseCase.selectedVariant.value = draft.selectedVariant
                            }
                        }

                        // Inject per-turn childSessionIdLookup from global subSessionIds
                        val enrichedTurns = if (subSessionIds.isNotEmpty()) {
                            turns.map { turn ->
                                val subtaskPartIds = turn.partLookup.keys.map { it.partId }.toSet()
                                turn.copy(
                                    childSessionIdLookup = subSessionIds.filterKeys { it in subtaskPartIds }
                                )
                            }
                        } else turns

                        ChatUiState(
                            session = partial.session,
                            messages = partial.messages,
                            parts = partial.parts,
                            turns = enrichedTurns,
                            permissions = partial.permissions,
                            questions = partial.questions,
                            sessionDiffs = partial.sessionDiffs,
                            sessionStatus = loading.status,
                            isLoading = loading.isLoading,
                            isLoadingMore = loading.isLoadingMore,
                            hasOlderMessages = _hasOlderMessages.value,
                            isSending = loading.isSending,
                            error = loading.error,
                            draftText = if (_draftCleared) "" else (draft?.text ?: ""),
                            providers = selector.partial.providers,
                            agents = selector.partial.agents,
                            commands = selector.partial.commands,
                            selectedAgent = selector.partial.selectedAgent,
                            selectedModel = selector.partial.selectedModel,
                            selectedVariant = selector.selectedVariant,
                            contextUsagePercent = stats.contextUsagePercent,
                            totalTokens = stats.totalTokens,
                            totalCost = stats.totalCost,
                            conversationTurns = stats.conversationTurns,
                            draftImageUris = if (_draftCleared) emptyList() else (draft?.imageUris ?: emptyList()),
                            attachedImages = images,
                            mentions = _mentions.value,
                            autoScrollEnabled = _autoScrollEnabled.value,
                            childSessionIds = subSessionIds,
                            childSessions = childSessions,
                            chatFontSize = _chatFontSize.value,
                            submittingQuestionIds = _submittingQuestionIds.value,
                            activePtyCount = allPtys[serverId]?.count { (_, pty) -> pty.status != "exited" } ?: 0,
                            commandMessage = _commandMessage.value,
                            commandMessageId = _commandMessageId.value,
                            shareUrl = _shareUrl.value,
                            shareDisabled = modelSelectionUseCase.shareConfig.value == "disabled",
                            todos = todos,
                        )
                    }
                }
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private data class SelectorPartialState(
        val providers: List<Provider>,
        val agents: List<AgentConfig>,
        val commands: List<CommandInfo>,
        val selectedAgent: String?,
        val selectedModel: ModelRef?,
    )

    private data class DraftCombineResult(
        val draft: ChatDraft?,
        val stats: SessionStatsUseCase.SessionStats,
        val subSessionIds: Map<String, String>,
        val childSessions: List<ChildSessionInfo>,
        val allPtys: Map<String, Map<String, PtyInfo>>,
    )

    private data class SelectorState(
        val partial: SelectorPartialState,
        val selectedVariant: String?,
    )

    private data class ChatPartialState(
        val session: Session?,
        val messages: List<Message>,
        val parts: Map<String, List<Part>>,
        val permissions: List<PermissionRequest>,
        val questions: List<QuestionRequest>,
        val sessionDiffs: List<FileDiff>,
        val allSessions: Map<String, Session>,
    )

    private data class ChatLoadingState(
        val status: SessionStatus,
        val isLoading: Boolean,
        val isLoadingMore: Boolean,
        val isSending: Boolean,
        val error: String?,
    )

    init {
        viewModelScope.launch {
            eventReducer.markSessionViewed(serverId, sessionId)
        }
        loadMessages()
        loadSessionStatus()
        viewModelScope.launch { modelSelectionUseCase.loadProviders(serverId) }
        modelSelectionUseCase.observeHiddenFilter(serverId, viewModelScope)
        viewModelScope.launch { modelSelectionUseCase.loadAgents(serverId) }
        viewModelScope.launch { modelSelectionUseCase.loadCommands(serverId) }
        viewModelScope.launch { modelSelectionUseCase.loadConfiguredModel(serverId) }
        loadChildSessions()
        loadPendingQuestions()
        loadTodosFromParts()
        observeReconnection()
    }

    /**
     * Observe SSE connection state changes and refresh chat data on reconnection.
     *
     * When the server reconnects after a disconnection (e.g. app backgrounded + network lost),
     * SSE events that occurred during the gap are permanently lost. This observer detects
     * the reconnection and triggers a full state refresh to recover from stale state.
     *
     * The first CONNECTED is skipped because [init] already loads initial data.
     */
    private fun observeReconnection() {
        var hadFirstConnection = false
        serverRepository.connectionStates
            .map { it[serverId] is ServerRepository.ConnectionState.CONNECTED }
            .distinctUntilChanged()
            .onEach { isConnected ->
                if (isConnected) {
                    if (hadFirstConnection) {
                        Log.d(TAG, "SSE reconnected, refreshing chat state for session=$sessionId")
                        refreshOnReconnect()
                    }
                    hadFirstConnection = true
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Refresh chat state after SSE reconnection.
     * Calls the same load methods used during initialization to recover missed SSE events.
     */
    private fun refreshOnReconnect() {
        loadSessionStatus()
        loadMessages()
        loadChildSessions()
        loadPendingQuestions()
    }

    override fun onCleared() {
        super.onCleared()
        eventReducer.clearViewedSession(serverId)
    }

    fun toggleAutoScroll() {
        _autoScrollEnabled.value = !_autoScrollEnabled.value
    }

    fun setAutoScroll(enabled: Boolean) {
        _autoScrollEnabled.value = enabled
    }

    fun loadMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = messageLoadingUseCase.loadInitial(serverId, sessionId)) {
                is MessageLoadingUseCase.LoadResult.Success -> {
                    _nextCursor = result.nextCursor
                    _hasOlderMessages.value = result.nextCursor != null
                    modelSelectionUseCase.tryApplyModelDefaults()
                }
                is MessageLoadingUseCase.LoadResult.Error -> {
                    _error.value = result.message
                }
            }
            _isLoading.value = false
        }
    }

    private fun loadChildSessions() {
        viewModelScope.launch {
            try {
                sessionNavigationUseCase.loadChildSessions(serverId, sessionId)
            } catch (_: Exception) {
                Log.d(TAG, "loadChildSessions: no children or failed for session=$sessionId")
            }
        }
    }

    /** Proactively query status to handle cold starts or missed SSE events. */
    private fun loadSessionStatus() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val statuses = api.getSessionStatuses(server, directory = directory)
                val status = statuses[sessionId] ?: return@launch
                eventReducer.updateSessionStatus(sessionId, status)
            } catch (e: Exception) {
                Log.d(TAG, "loadSessionStatus: failed for session=$sessionId", e)
            }
        }
    }

    private fun loadPendingQuestions() {
        viewModelScope.launch {
            try {
                sessionNavigationUseCase.loadPendingQuestions(serverId, sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "loadPendingQuestions: failed", e)
            }
        }
    }

    /**
     * Load initial todo state from the latest todowrite tool call in message parts.
     * This handles the cold-start case where SSE events were missed.
     * If SSE already delivered todos (e.g. from a live session), this is a no-op.
     */
    private fun loadTodosFromParts() {
        viewModelScope.launch {
            // Wait for messages to be loaded first
            eventReducer.messages.map { it[sessionId] ?: emptyList() }
                .first { it.isNotEmpty() }

            // Don't overwrite if SSE already delivered todos
            if (eventReducer.todos.value[sessionId] != null) return@launch

            val allParts = eventReducer.parts.value
            val sessionMessages = eventReducer.messages.value[sessionId] ?: return@launch

            // Scan all parts for the last completed todowrite tool call
            val todoParts = sessionMessages.flatMap { msg ->
                (allParts[msg.id] ?: emptyList())
                    .filterIsInstance<Part.Tool>()
                    .filter { it.tool == "todowrite" && it.state.isCompleted }
            }

            val lastTodoPart = todoParts.lastOrNull() ?: return@launch
            val todosJson = lastTodoPart.state.metadata
                ?.let { (it as? JsonObject)?.get("todos") }
                ?: return@launch

            try {
                val json = Json { ignoreUnknownKeys = true }
                val todos = json.decodeFromJsonElement<List<Todo>>(todosJson)
                eventReducer.updateTodos(sessionId, todos)
            } catch (e: Exception) {
                Log.d(TAG, "loadTodosFromParts: failed to parse todos from parts", e)
            }
        }
    }

    fun loadOlderMessages() {
        viewModelScope.launch {
            if (_isLoadingMore.value || !_hasOlderMessages.value) return@launch

            val cursor = _nextCursor
            if (cursor == null) {
                _hasOlderMessages.value = false
                return@launch
            }

            _isLoadingMore.value = true
            when (val result = messageLoadingUseCase.loadOlder(serverId, sessionId, cursor)) {
                is MessageLoadingUseCase.LoadResult.Success -> {
                    _nextCursor = result.nextCursor
                    if (result.nextCursor == null) {
                        _hasOlderMessages.value = false
                    }
                }
                is MessageLoadingUseCase.LoadResult.Error -> {
                    _error.value = result.message
                }
            }
            _isLoadingMore.value = false
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && _attachedImages.value.isEmpty()) return
        if (_isSending.value) return

        _draftCleared = true

        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            Log.d(TAG, "sendMessage: ENTRY serverId=$serverId, text=${text.take(50)}")

            val ctx = SendMessageUseCase.SendContext(
                serverId = serverId,
                sessionId = sessionId,
                text = text,
                mentions = _mentions.value,
                attachedImages = _attachedImages.value,
                selectedAgent = modelSelectionUseCase.selectedAgent.value,
                selectedModel = modelSelectionUseCase.selectedModel.value,
                selectedVariant = modelSelectionUseCase.selectedVariant.value,
                draftImageUris = uiState.value.draftImageUris,
                sessionDirectory = eventReducer.sessions.value[sessionId]?.directory,
            )

            when (val result = sendMessageUseCase.execute(ctx)) {
                is SendMessageUseCase.SendResult.Success -> {
                    _draftCleared = false
                    _attachedImages.value = emptyList()
                    _mentions.value = emptyList()
                }
                is SendMessageUseCase.SendResult.ShellCommandSent -> {
                    _draftCleared = false
                    _attachedImages.value = emptyList()
                    _mentions.value = emptyList()
                }
                is SendMessageUseCase.SendResult.Error -> {
                    _draftCleared = false
                    if (result.message.isNotEmpty()) {
                        _error.value = result.message
                    }
                }
            }
            _isSending.value = false
            _draftCleared = false
        }
    }

    fun selectAgent(agent: String?) {
        viewModelScope.launch { modelSelectionUseCase.selectAgent(serverId, agent) }
    }

    fun selectModel(model: ModelRef?) {
        viewModelScope.launch { modelSelectionUseCase.selectModel(serverId, model) }
    }

    fun selectVariant(variant: String?) {
        viewModelScope.launch { modelSelectionUseCase.selectVariant(serverId, variant) }
    }

    fun abortSession() {
        viewModelScope.launch {
            try {
                val directory = eventReducer.sessions.value[sessionId]?.directory
                sessionOpsUseCase.abortSession(serverId, sessionId, directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to abort"
            }
        }
    }

    fun replyPermission(permissionId: String, reply: String, message: String? = null) {
        viewModelScope.launch {
            val result = permissionQuestionUseCase.replyPermission(serverId, sessionId, permissionId, reply, message)
            result.exceptionOrNull()?.let { e ->
                _error.value = e.message ?: "Failed to reply"
            }
        }
    }

    fun replyQuestion(question: QuestionRequest, answers: List<List<String>>) {
        viewModelScope.launch {
            _submittingQuestionIds.value = _submittingQuestionIds.value + question.id
            when (val result = permissionQuestionUseCase.replyQuestion(serverId, question, answers)) {
                is PermissionQuestionUseCase.QuestionResult.Success -> {
                }
                is PermissionQuestionUseCase.QuestionResult.ApiFailure -> {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                    _error.value = result.errorMessage
                }
                is PermissionQuestionUseCase.QuestionResult.ServerNotFound -> {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                }
            }
        }
    }

    fun rejectQuestion(question: QuestionRequest) {
        viewModelScope.launch {
            _submittingQuestionIds.value = _submittingQuestionIds.value + question.id
            when (val result = permissionQuestionUseCase.rejectQuestion(serverId, question)) {
                is PermissionQuestionUseCase.QuestionResult.Success -> {
                }
                is PermissionQuestionUseCase.QuestionResult.ApiFailure -> {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                    _error.value = result.errorMessage
                }
                is PermissionQuestionUseCase.QuestionResult.ServerNotFound -> {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                }
            }
        }
    }

    fun saveDraft(text: String) {
        viewModelScope.launch {
            draftManagementUseCase.saveDraft(
                sessionId, text,
                modelSelectionUseCase.selectedAgent.value,
                modelSelectionUseCase.selectedModel.value,
                modelSelectionUseCase.selectedVariant.value,
                uiState.value.draftImageUris,
            )
        }
    }

    fun addDraftImage(uri: String) {
        viewModelScope.launch {
            val updated = draftManagementUseCase.addDraftImage(
                sessionId, uri,
                modelSelectionUseCase.selectedAgent.value,
                modelSelectionUseCase.selectedModel.value,
                modelSelectionUseCase.selectedVariant.value,
                uiState.value.draftImageUris,
            )
            // The draft flow will emit the updated image URIs, no manual state needed
        }
    }

    fun removeDraftImage(uri: String) {
        viewModelScope.launch {
            val updated = draftManagementUseCase.removeDraftImage(
                sessionId, uri,
                modelSelectionUseCase.selectedAgent.value,
                modelSelectionUseCase.selectedModel.value,
                modelSelectionUseCase.selectedVariant.value,
                uiState.value.draftImageUris,
            )
        }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val compressed = imageCompressor.compress(uri)
                if (compressed == null) {
                    val msg = "Failed to attach image: compression failed for uri=$uri"
                    Log.e(TAG, msg)
                    errorCollector.logError(msg, "Chat")
                    _error.value = "Failed to attach image: compression failed"
                    return@launch
                }
                val mimeType = imageCompressor.getMimeType(uri)
                val base64 = imageCompressor.run { compressed.toBase64() }
                val current = _attachedImages.value
                _attachedImages.value = current + AttachedImage(uri, base64, mimeType)
                Log.d(TAG, "attachImage: success, uri=$uri, size=${compressed.size} bytes, mimeType=$mimeType")
            } catch (e: Exception) {
                Log.e(TAG, "attachImage: failed for uri=$uri", e)
                errorCollector.logError(e, "Chat")
                _error.value = "Failed to attach image: ${e.message}"
            }
        }
    }

    fun removeImage(index: Int) {
        val current = _attachedImages.value
        if (index in current.indices) {
            _attachedImages.value = current.toMutableList().apply { removeAt(index) }
        }
    }

    fun clearAttachedImages() {
        _attachedImages.value = emptyList()
    }

    fun addMention(mention: MentionItem, start: Int, end: Int) {
        _mentions.value = mentionManagementUseCase.addMention(_mentions.value, mention, start, end)
    }

    fun removeMention(displayText: String) {
        _mentions.value = mentionManagementUseCase.removeMention(_mentions.value, displayText)
    }

    fun clearMentions() {
        _mentions.value = mentionManagementUseCase.clearMentions()
    }

    fun reconcileMentions(text: String) {
        _mentions.value = mentionManagementUseCase.reconcileMentions(_mentions.value, text)
    }

    fun getMentionDisplayTexts(): Set<String> {
        return mentionManagementUseCase.getMentionDisplayTexts(_mentions.value)
    }

    suspend fun searchFiles(query: String): List<String> {
        return mentionManagementUseCase.searchFiles(serverId, sessionId, query)
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.deleteMessage(server, sessionId, messageId, directory = directory)
                eventReducer.processEvent(serverId, SseEvent.MessageRemoved(sessionId, messageId))
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to delete message"
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private fun computeChildSessionIds(
        parentSessionId: String,
        messages: List<Message>,
        allParts: Map<String, List<Part>>,
        allSessions: Map<String, Session>,
    ): Map<String, String> {
        return sessionNavigationUseCase.computeChildSessionIds(parentSessionId, messages, allParts, allSessions)
    }

    private fun findAllDescendants(
        parentSessionId: String,
        allSessions: Map<String, Session>,
    ): List<String> {
        return sessionNavigationUseCase.findAllDescendants(parentSessionId, allSessions)
    }

    fun executeBuiltInCommand(
        command: BuiltInCommand,
    ): Boolean {
        val isLocal = command.id !in listOf("new", "sessions", "terminal", "files", "settings", "mcp", "model", "agent")
        if (isLocal) {
            viewModelScope.launch {
                when (val result = chatCommandUseCase.execute(command, serverId, sessionId)) {
                    is ChatCommandUseCase.CommandResult.Error -> _error.value = result.message
                    is ChatCommandUseCase.CommandResult.ShareSuccess -> _shareUrl.value = result.url
                    is ChatCommandUseCase.CommandResult.Handled -> {
                        _commandMessage.value = "/${command.id} executed"
                        _commandMessageId.value = System.currentTimeMillis()
                    }
                    else -> {}
                }
            }
        }
        return isLocal
    }

    fun dismissShareDialog() {
        _shareUrl.value = null
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
