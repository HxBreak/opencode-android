package me.xiaok.opencode.ui.screens.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.DraftRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.domain.model.MentionItem
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

data class ChatUiState(
    val session: Session? = null,
    val messages: List<Message> = emptyList(),
    val parts: Map<String, List<Part>> = emptyMap(),
    val permissions: List<PermissionRequest> = emptyList(),
    val questions: List<QuestionRequest> = emptyList(),
    val sessionDiffs: List<FileDiff> = emptyList(),
    val sessionStatus: SessionStatus = SessionStatus.IDLE,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasOlderMessages: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    val draftText: String = "",
    val providers: List<Provider> = emptyList(),
    val agents: List<AgentConfig> = emptyList(),
    val commands: List<CommandInfo> = emptyList(),
    val selectedAgent: String? = null,
    val selectedModel: ModelRef? = null,
    val selectedVariant: String? = null,
    val contextUsagePercent: Int = 0,
    val totalTokens: Long = 0L,
    val totalCost: Double = 0.0,
    val conversationTurns: Int = 0,
    val draftImageUris: List<String> = emptyList(),
    val attachedImages: List<AttachedImage> = emptyList(),
    val mentions: List<MentionItem> = emptyList(),
    val autoScrollEnabled: Boolean = true,
    val childSessionIds: Map<String, String> = emptyMap(),
    val chatFontSize: String = "medium",
    val submittingQuestionIds: Set<String> = emptySet(),
    val sessionWebUrl: String = "",
    val activePtyCount: Int = 0,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val draftRepository: DraftRepository,
    private val settingsRepository: SettingsRepository,
    private val imageCompressor: ImageCompressor,
    private val errorCollector: ErrorCollector,
    private val sessionStatsUseCase: SessionStatsUseCase,
    private val draftManagementUseCase: DraftManagementUseCase,
    private val mentionManagementUseCase: MentionManagementUseCase,
    private val permissionQuestionUseCase: PermissionQuestionUseCase,
    private val sessionNavigationUseCase: SessionNavigationUseCase,
    private val sessionOpsUseCase: SessionOpsUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val modelSelectionUseCase: ModelSelectionUseCase,
    private val messageLoadingUseCase: MessageLoadingUseCase,
    private val chatCommandUseCase: ChatCommandUseCase,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")
    private val sessionId: String = savedStateHandle["sessionId"]
        ?: throw IllegalArgumentException("sessionId is required")

    private val _isSending = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasOlderMessages = MutableStateFlow(true)
    private var _nextCursor: String? = null

    private val _submittingQuestionIds = MutableStateFlow<Set<String>>(emptySet())
    private var _draftCleared = false

    private val _attachedImages = MutableStateFlow<List<AttachedImage>>(emptyList())
    private val _mentions = MutableStateFlow<List<MentionItem>>(emptyList())
    private val _autoScrollEnabled = MutableStateFlow(true)
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
    }.flatMapLatest { partial ->
        combine(
            eventReducer.sessionStatuses.map { it[sessionId] ?: SessionStatus.IDLE },
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
                    combine(draftRepository.getDraft(sessionId), sessionStats, childSessionIdsFlow, eventReducer.ptySessions) { draft, stats, subSessionIds, allPtys ->
                        if (draft != null) {
                            if (modelSelectionUseCase.selectedAgent.value == null && draft.selectedAgent != null) {
                                modelSelectionUseCase.selectedAgent.value = draft.selectedAgent
                            }
                            if (modelSelectionUseCase.selectedModel.value == null && draft.selectedModel != null) {
                                modelSelectionUseCase.selectedModel.value = draft.selectedModel
                            }
                            if (modelSelectionUseCase.selectedVariant.value == null && draft.selectedVariant != null) {
                                modelSelectionUseCase.selectedVariant.value = draft.selectedVariant
                            }
                        }

                        ChatUiState(
                            session = partial.session,
                            messages = partial.messages,
                            parts = partial.parts,
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
                            chatFontSize = _chatFontSize.value,
                            submittingQuestionIds = _submittingQuestionIds.value,
                            sessionWebUrl = buildSessionWebUrl(partial.session),
                            activePtyCount = allPtys[serverId]?.count { (_, pty) -> pty.status != "exited" } ?: 0,
                        )
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private data class SelectorPartialState(
        val providers: List<Provider>,
        val agents: List<AgentConfig>,
        val commands: List<CommandInfo>,
        val selectedAgent: String?,
        val selectedModel: ModelRef?,
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

    private fun buildSessionWebUrl(session: Session?): String {
        return sessionNavigationUseCase.buildSessionWebUrl(serverId, session)
    }

    init {
        viewModelScope.launch {
            eventReducer.markSessionViewed(serverId, sessionId)
        }
        loadMessages()
        viewModelScope.launch { modelSelectionUseCase.loadProviders(serverId) }
        modelSelectionUseCase.observeHiddenFilter(serverId, viewModelScope)
        viewModelScope.launch { modelSelectionUseCase.loadAgents(serverId) }
        viewModelScope.launch { modelSelectionUseCase.loadCommands(serverId) }
        viewModelScope.launch { modelSelectionUseCase.loadConfiguredModel(serverId) }
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

    private fun loadPendingQuestions() {
        viewModelScope.launch {
            try {
                sessionNavigationUseCase.loadPendingQuestions(serverId, sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "loadPendingQuestions: failed", e)
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
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.abortSession(server, sessionId, directory = directory)
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
                    Log.e(TAG, "attachImage: compression returned null for uri=$uri")
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

    private fun <T> executeSessionOp(
        operationName: String,
        onResult: ((T) -> Unit)? = null,
        block: suspend (serverId: String, sessionId: String, directory: String?) -> T,
    ) {
        viewModelScope.launch {
            try {
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val result = block(serverId, sessionId, directory)
                onResult?.invoke(result)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to $operationName"
            }
        }
    }

    fun refreshSessionDiffs() {
        viewModelScope.launch {
            try {
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val workspace = eventReducer.sessions.value[sessionId]?.workspaceID
                sessionOpsUseCase.refreshSessionDiffs(serverId, sessionId, directory, workspace)
            } catch (_: Exception) { }
        }
    }

    fun dismissDiffs() {
        sessionOpsUseCase.dismissDiffs(serverId, sessionId)
    }

    fun forkSession(messageId: String, onResult: (String) -> Unit) {
        executeSessionOp("fork session", onResult) { sId, sesId, dir ->
            sessionOpsUseCase.forkSession(sId, sesId, messageId, dir)
        }
    }

    fun shareSession(onResult: (String) -> Unit) {
        executeSessionOp("share session", onResult) { sId, sesId, dir ->
            sessionOpsUseCase.shareSession(sId, sesId, dir)
        }
    }

    fun unshareSession() {
        executeSessionOp<Unit>("unshare session") { sId, sesId, dir ->
            sessionOpsUseCase.unshareSession(sId, sesId, dir)
        }
    }

    fun revertSession(messageId: String) {
        executeSessionOp<Unit>("revert session") { sId, sesId, dir ->
            sessionOpsUseCase.revertSession(sId, sesId, messageId, dir)
        }
    }

    fun summarizeSession() {
        executeSessionOp<Unit>("summarize session") { sId, sesId, dir ->
            sessionOpsUseCase.summarizeSession(sId, sesId, modelSelectionUseCase.selectedModel.value, dir)
        }
    }

    fun unrevertSession() {
        executeSessionOp<Unit>("unrevert session") { sId, sesId, dir ->
            sessionOpsUseCase.unrevertSession(sId, sesId, dir)
        }
    }

    fun renameSession(newTitle: String) {
        executeSessionOp<Unit>("rename session") { sId, sesId, dir ->
            sessionOpsUseCase.renameSession(sId, sesId, newTitle, dir)
        }
    }

    fun deleteSession() {
        executeSessionOp<Unit>("delete session") { sId, sesId, dir ->
            sessionOpsUseCase.deleteSession(sId, sesId, dir)
        }
    }

    suspend fun exportSession(): String {
        return sessionOpsUseCase.exportSession(serverId, sessionId)
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
                    else -> {}
                }
            }
        }
        return isLocal
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
