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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.DraftRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.domain.model.MentionItem
import me.xiaok.opencode.utils.ErrorCollector
import me.xiaok.opencode.utils.ImageCompressor
import javax.inject.Inject

// === UiState ===

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
    val attachedImages: List<ChatViewModel.AttachedImage> = emptyList(),
    val mentions: List<MentionItem> = emptyList(),
    val autoScrollEnabled: Boolean = true,
    val childSessionIds: Map<String, String> = emptyMap(), // subtaskPartId -> childSessionId
    val chatFontSize: String = "medium", // "small", "medium", "large"
    val submittingQuestionIds: Set<String> = emptySet(),
    val sessionWebUrl: String = "",
    val activePtyCount: Int = 0,
)

// === ViewModel ===

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
) : ViewModel() {

    /**
     * Represents an image attached to a draft message.
     * The [base64] data is compressed WebP ready for inline embedding.
     */
    data class AttachedImage(
        val uri: Uri,
        val base64: String,
        val mimeType: String,
    )

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

    /** Question IDs currently being submitted (reply/reject) — UI should show loading state. */
    private val _submittingQuestionIds = MutableStateFlow<Set<String>>(emptySet())
    /**
     * Flag to suppress draft restoration from DataStore.
     * Set to true when sendMessage clears the draft, to prevent
     * DataStore's async Flow from emitting stale values that would
     * re-populate the input field.
     */
    private var _draftCleared = false

    /** Raw providers from API (connected only, before hidden-model filtering). */
    private val _rawProviders = MutableStateFlow<List<Provider>>(emptyList())
    /** Hidden model/provider sets from local settings (reactive via DataStore). */
    private val _hiddenModels = MutableStateFlow<Set<String>>(emptySet())
    private val _hiddenProviders = MutableStateFlow<Set<String>>(emptySet())
    /** Filtered providers exposed to UI (raw − hidden). */
    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    private val _agents = MutableStateFlow<List<AgentConfig>>(emptyList())
    private val _commands = MutableStateFlow<List<CommandInfo>>(emptyList())
    private val _selectedAgent = MutableStateFlow<String?>(null)
    private val _selectedModel = MutableStateFlow<ModelRef?>(null)
    private val _selectedVariant = MutableStateFlow<String?>(null)
    private val _draftImageUris = MutableStateFlow<List<String>>(emptyList())
    private val _attachedImages = MutableStateFlow<List<AttachedImage>>(emptyList())
    private val _mentions = MutableStateFlow<List<MentionItem>>(emptyList())
    private val _autoScrollEnabled = MutableStateFlow(true)
    private val _chatFontSize = settingsRepository.chatFontSize.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), "medium"
    )

    // Model fallback data (loaded once from config/providers)
    private val _providerDefaults = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _configuredModel = MutableStateFlow<ModelRef?>(null)
    private val _savedModel = MutableStateFlow<ModelRef?>(null)
    private var _modelDefaultsApplied = false

    private val _selectorState = combine(
        _providers,
        _agents,
        _commands,
        _selectedAgent,
        _selectedModel,
    ) { providers, agents, commands, selAgent, selModel ->
        SelectorPartialState(providers, agents, commands, selAgent, selModel)
    }

    // === Cached derived state (avoid recomputation on every streaming delta) ===

    /**
     * Session statistics (tokens, cost, turns) — recomputed when messages
     * or model selection change, NOT on every streaming delta.
     * Subscribed by uiState combine chain so it stays active.
     */
    private val sessionStats: Flow<SessionStats> = combine(
        eventReducer.messages.map { it[sessionId] ?: emptyList() },
        _providers,
        _selectedModel,
    ) { messages, providers, selectedModel ->
        computeSessionStats(messages, providers, selectedModel)
    }.distinctUntilChanged()

    /**
     * Mapping: subtask part ID → child session ID.
     * Only recomputed when messages, parts, or sessions change — NOT on every streaming delta.
     * Subscribed by uiState combine chain so it stays active.
     */
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

        // Collect questions from current session + all descendant sessions (Web behavior)
        val descendantIds = findAllDescendants(sessionId, allSessions)
        val mergedQuestions = (listOf(sessionId) + descendantIds)
            .flatMap { sid -> allQuestions[sid] ?: emptyList() }

        // Clean up submitting IDs that are no longer in questions (SSE confirmed removal)
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
                _selectedVariant,
            ) { selector, selVariant ->
                SelectorState(selector, selVariant)
            }.flatMapLatest { selector ->
                _attachedImages.flatMapLatest { images ->
                    combine(draftRepository.getDraft(sessionId), sessionStats, childSessionIdsFlow, eventReducer.ptySessions) { draft, stats, subSessionIds, allPtys ->
                        // Restore agent/model/variant from draft if available
                        if (draft != null) {
                            if (_selectedAgent.value == null && draft.selectedAgent != null) {
                                _selectedAgent.value = draft.selectedAgent
                            }
                            if (_selectedModel.value == null && draft.selectedModel != null) {
                                _selectedModel.value = draft.selectedModel
                            }
                            if (_selectedVariant.value == null && draft.selectedVariant != null) {
                                _selectedVariant.value = draft.selectedVariant
                            }
                            _draftImageUris.value = draft.imageUris
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

    /**
     * Build the web UI URL for a session.
     * Format: {serverBaseUrl}/{base64(directory)}/session/{sessionID}
     */
    private fun buildSessionWebUrl(session: Session?): String {
        if (session == null) return ""
        val server = serverRepository.getServer(serverId) ?: return ""
        val base = server.baseUrl.trimEnd('/')
        val dirEncoded = android.util.Base64.encodeToString(
            session.directory.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        return "$base/$dirEncoded/session/${session.id}"
    }

    init {
        // Mark this session as viewed (clears unread indicator)
        viewModelScope.launch {
            eventReducer.markSessionViewed(serverId, sessionId)
            // chatFontSize is collected from settingsRepository via _chatFontSize StateFlow
        }
        loadMessages()
        loadProviders()
        observeHiddenFilter()
        loadAgents()
        loadCommands()
        loadConfiguredModel()
        loadChildSessions()
        loadPendingQuestions()
    }

    override fun onCleared() {
        super.onCleared()
        // Clear viewed session when leaving chat
        eventReducer.clearViewedSession(serverId)
    }

    // === Auto-Scroll ===

    fun toggleAutoScroll() {
        _autoScrollEnabled.value = !_autoScrollEnabled.value
    }

    fun setAutoScroll(enabled: Boolean) {
        _autoScrollEnabled.value = enabled
    }

    // === Actions ===

    fun loadMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.d(TAG, "loadMessages: START sessionId=$sessionId")
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _error.value = "Server not found"
                    _isLoading.value = false
                    Log.e(TAG, "loadMessages: server not found, serverId=$serverId")
                    return@launch
                }
                val limit = settingsRepository.initialMessages.first()
                val directory = eventReducer.sessions.value[sessionId]?.directory
                Log.d(TAG, "loadMessages: requesting limit=$limit from API, directory=$directory")
                val page = api.listMessages(server, sessionId, limit = limit, directory = directory)
                val messages = page.messages
                Log.d(TAG, "loadMessages: API returned ${messages.size} messages, nextCursor=${page.nextCursor}")

                // Save server-returned cursor for pagination (instead of fabricating one from message ID)
                _nextCursor = page.nextCursor
                eventReducer.setMessages(sessionId, messages)

                // Server sends X-Next-Cursor only when more pages exist
                _hasOlderMessages.value = page.nextCursor != null

                // Also load parts for each message (use inline parts from API response)
                messages.forEach { message ->
                    if (eventReducer.parts.value[message.id] == null) {
                        eventReducer.setParts(message.id, message.parts)
                    }
                }

                // Log final state after load
                val finalMessages = eventReducer.messages.value[sessionId] ?: emptyList()
                Log.d(TAG, "loadMessages: DONE, messages in reducer=${finalMessages.size}, parts keys=${eventReducer.parts.value.keys.size}")

                // Try applying model defaults now that messages are loaded (Tier 2: recentModel)
                tryApplyModelDefaults()
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to load messages"
                Log.e(TAG, "loadMessages: FAILED", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load child (sub-agent) sessions for the current session.
     * Injects them into EventReducer so the childSessionIds mapping can resolve
     * Subtask parts → child sessions for navigation.
     */
    private fun loadChildSessions() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val children = api.getSessionChildren(server, sessionId, directory = directory)
                if (children.isNotEmpty()) {
                    children.forEach { child ->
                        eventReducer.processEvent(
                            serverId,
                            SseEvent.SessionCreated(child)
                        )
                    }
                    Log.d(TAG, "loadChildSessions: loaded ${children.size} children for session=$sessionId")
                }
            } catch (_: Exception) {
                // Child sessions are optional — don't block chat if this fails
                Log.d(TAG, "loadChildSessions: no children or failed for session=$sessionId")
            }
        }
    }

    /**
     * Load pending questions from REST API.
     * Recovers questions that may have been missed if the SSE event
     * arrived before the chat screen was opened.
     */
    private fun loadPendingQuestions() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    Log.w(TAG, "loadPendingQuestions: server not found for serverId=$serverId")
                    return@launch
                }
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val allQuestions = api.listQuestions(server, directory = directory)
                Log.d(TAG, "loadPendingQuestions: directory=$directory, total=${allQuestions.size}")
                // Group by sessionID and load all into reducer (not just current session)
                allQuestions.groupBy { it.sessionID }.forEach { (sid, questions) ->
                    eventReducer.setQuestions(sid, questions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadPendingQuestions: failed", e)
            }
        }
    }

    fun loadOlderMessages() {
        viewModelScope.launch {
            // Guard: don't load if already loading or no more messages
            if (_isLoadingMore.value || !_hasOlderMessages.value) return@launch

            _isLoadingMore.value = true
            Log.d(TAG, "loadOlderMessages: START sessionId=$sessionId")
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch

                // Use the server-returned cursor (not a fabricated message ID)
                val cursor = _nextCursor
                if (cursor == null) {
                    _hasOlderMessages.value = false
                    Log.d(TAG, "loadOlderMessages: no cursor available, hasOlder=false")
                    return@launch
                }

                val limit = settingsRepository.initialMessages.first()
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val page = api.listMessages(
                    server, sessionId,
                    limit = limit,
                    before = cursor,
                    directory = directory,
                )
                val olderMessages = page.messages

                // Update cursor for next page
                _nextCursor = page.nextCursor

                if (olderMessages.isEmpty()) {
                    _hasOlderMessages.value = false
                    Log.d(TAG, "loadOlderMessages: no older messages found, hasOlder=false")
                } else {
                    eventReducer.prependMessages(sessionId, olderMessages)

                    // Load parts for newly fetched messages
                    olderMessages.forEach { message ->
                        if (eventReducer.parts.value[message.id] == null) {
                            eventReducer.setParts(message.id, message.parts)
                        }
                    }

                    // No more pages if server didn't return a next cursor
                    if (_nextCursor == null) {
                        _hasOlderMessages.value = false
                    }
                    Log.d(TAG, "loadOlderMessages: DONE, fetched=${olderMessages.size}")
                }
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to load older messages"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && _attachedImages.value.isEmpty()) return
        Log.d(TAG, "sendMessage: ENTRY isSending=${_isSending.value}, serverId=$serverId, text=${text.take(50)}")
        if (_isSending.value) return

        // Immediately mark draft as cleared to prevent DataStore async Flow
        // from re-emitting old draft value into uiState.draftText
        _draftCleared = true

        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            Log.d(TAG, "sendMessage: LAUNCHED, serverId=$serverId, allServers=${serverRepository.servers.value.map { it.id }}")

            // Clear draft
            Log.d(TAG, "sendMessage: before clearDraft")
            draftRepository.clearDraft(sessionId)
            _draftCleared = false  // Reset flag after DataStore is cleared
            Log.d(TAG, "sendMessage: after clearDraft")

            try {
                val server = serverRepository.getServer(serverId)
                Log.d(TAG, "sendMessage: server=$server")
                if (server == null) {
                    Log.e(TAG, "sendMessage: server not found for serverId=$serverId")
                    _error.value = "Server not found. Please reconnect."
                    // Restore draft
                    _draftCleared = false
                    val draft = ChatDraft(
                        text = text,
                        selectedAgent = _selectedAgent.value,
                        selectedModel = _selectedModel.value,
                        selectedVariant = _selectedVariant.value,
                        imageUris = _draftImageUris.value,
                    )
                    draftRepository.saveDraft(sessionId, draft)
                    return@launch
                }

                // Shell command mode: text starts with "!"
                if (text.trimStart().startsWith("!")) {
                    val command = text.trimStart().removePrefix("!").trim()
                    if (command.isNotBlank()) {
                        val directory = eventReducer.sessions.value[sessionId]?.directory
                        api.runShell(server, sessionId, command, directory = directory)
                    }
                    _attachedImages.value = emptyList()
                    return@launch
                }

                // Build parts list: text parts + file mention parts + agent mention parts + image parts
                val parts = mutableListOf<Map<String, Any>>()
                val mentions = _mentions.value.sortedBy { it.start }
                val directory = eventReducer.sessions.value[sessionId]?.directory

                if (mentions.isNotEmpty()) {
                    // Split text around mention positions to produce alternating text/mention parts
                    var cursor = 0
                    for (mention in mentions) {
                        // Text before this mention
                        val beforeText = text.substring(cursor, mention.start.coerceIn(cursor, text.length)).trim()
                        if (beforeText.isNotBlank()) {
                            parts.add(mapOf("type" to "text", "text" to beforeText))
                        }

                        when (mention) {
                            is MentionItem.FileMention -> {
                                // Build file:// URL with absolute path
                                val absolutePath = if (mention.path.startsWith("/")) {
                                    mention.path
                                } else {
                                    val dir = directory?.trimEnd('/') ?: ""
                                    "$dir/${mention.path}"
                                }
                                parts.add(mapOf(
                                    "type" to "file",
                                    "url" to "file://$absolutePath",
                                    "mime" to "text/plain",
                                    "filename" to mention.path.substringAfterLast('/'),
                                    "source" to mapOf(
                                        "type" to "file",
                                        "text" to mapOf(
                                            "value" to mention.displayText,
                                            "start" to mention.start,
                                            "end" to mention.end,
                                        ),
                                        "path" to absolutePath,
                                    ),
                                ))
                            }
                            is MentionItem.AgentMention -> {
                                parts.add(mapOf(
                                    "type" to "agent",
                                    "name" to mention.name,
                                    "source" to mapOf(
                                        "value" to mention.displayText,
                                        "start" to mention.start,
                                        "end" to mention.end,
                                    ),
                                ))
                            }
                        }

                        cursor = mention.end.coerceAtMost(text.length)
                    }

                    // Text after last mention
                    val afterText = text.substring(cursor).trim()
                    if (afterText.isNotBlank()) {
                        parts.add(mapOf("type" to "text", "text" to afterText))
                    }
                } else if (text.isNotBlank()) {
                    parts.add(mapOf("type" to "text", "text" to text.trim()))
                }

                // Add attached images as file parts (OpenCode API expects type="file" with url field)
                _attachedImages.value.forEach { image ->
                    parts.add(mapOf(
                        "type" to "file",
                        "url" to "data:${image.mimeType};base64,${image.base64}",
                        "mime" to image.mimeType,
                    ))
                }

                Log.d(TAG, "sendMessage: BEFORE api.promptAsync, parts=$parts, agent=${_selectedAgent.value}, model=${_selectedModel.value}")
                try {
                    api.promptAsync(
                        conn = server,
                        sessionId = sessionId,
                        parts = parts,
                        agent = _selectedAgent.value,
                        model = _selectedModel.value,
                        variant = _selectedVariant.value,
                        directory = uiState.value.session?.directory,
                    )
                    Log.d(TAG, "sendMessage: api.promptAsync returned successfully")
                } catch (inner: Exception) {
                    Log.e(TAG, "sendMessage: api.promptAsync FAILED: ${inner.javaClass.simpleName}: ${inner.message}", inner)
                    throw inner
                }

                // Clear attached images and mentions after successful send
                _attachedImages.value = emptyList()
                _mentions.value = emptyList()

                // Reload messages to ensure user message (with parts) is displayed.
                // SSE may push message.updated with empty parts for user messages,
                // so we fetch from API to get complete data.
                try {
                    val server = serverRepository.getServer(serverId)
                    if (server != null) {
                        val directory = eventReducer.sessions.value[sessionId]?.directory
                        val page = api.listMessages(server, sessionId, limit = settingsRepository.initialMessages.first(), directory = directory)
                        eventReducer.setMessages(sessionId, page.messages)
                        page.messages.forEach { message ->
                            if (eventReducer.parts.value[message.id] == null) {
                                eventReducer.setParts(message.id, message.parts)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "sendMessage: post-send refresh failed (non-critical)", e)
                }
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to send message"
                // Restore draft on failure
                _draftCleared = false
                val draft = ChatDraft(
                    text = text,
                    selectedAgent = _selectedAgent.value,
                    selectedModel = _selectedModel.value,
                    selectedVariant = _selectedVariant.value,
                    imageUris = _draftImageUris.value,
                )
                draftRepository.saveDraft(sessionId, draft)
            } finally {
                _isSending.value = false
                _draftCleared = false
            }
        }
    }

    fun loadProviders() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val providerList = api.getProviders(server)
                val connected = providerList.connected.toSet()
                _rawProviders.value = providerList.all.filter { it.id in connected }
                _providerDefaults.value = providerList.default
                applyHiddenFilter()
                tryApplyModelDefaults()
            } catch (_: Exception) { /* silent fail, providers are optional */ }
        }
    }

    /**
     * Apply hidden-model/provider filters from local settings.
     * Removes hidden providers entirely, and strips hidden models from remaining providers.
     * If the currently selected model becomes hidden, it is deselected.
     */
    private fun applyHiddenFilter() {
        val hiddenProv = _hiddenProviders.value
        val hiddenMod = _hiddenModels.value
        val raw = _rawProviders.value

        _providers.value = raw
            .filter { it.id !in hiddenProv }
            .map { provider ->
                val filteredModels = provider.models.filterKeys { modelId ->
                    "${provider.id}/$modelId" !in hiddenMod
                }
                if (filteredModels.size == provider.models.size) provider else {
                    provider.copy(models = filteredModels)
                }
            }
            .filter { it.models.isNotEmpty() } // Drop providers with no visible models

        // Deselect if the selected model is now hidden
        val sel = _selectedModel.value
        if (sel != null && !isValidModelRef(sel)) {
            _selectedModel.value = null
            _selectedVariant.value = null
            _modelDefaultsApplied = false
            tryApplyModelDefaults()
        }
    }

    /**
     * Reactively observe hidden-model/provider settings from DataStore.
     * When the user changes visibility in ServerModelFilterScreen, this updates immediately.
     */
    private fun observeHiddenFilter() {
        viewModelScope.launch {
            settingsRepository.getHiddenModels(serverId).collect { hidden ->
                _hiddenModels.value = hidden
                applyHiddenFilter()
            }
        }
        viewModelScope.launch {
            settingsRepository.getHiddenProviders(serverId).collect { hidden ->
                _hiddenProviders.value = hidden
                applyHiddenFilter()
            }
        }
    }

    fun loadAgents() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val agentList = api.getAgents(server)
                _agents.value = agentList

                // Only set default if not already restored from draft
                if (_selectedAgent.value == null) {
                    // Try saved preference first
                    val savedAgent = settingsRepository.getRecentAgent(serverId).first()
                    val visibleAgents = agentList.filter { !it.hidden && it.mode != "subagent" }
                    if (savedAgent != null && visibleAgents.any { it.name == savedAgent }) {
                        _selectedAgent.value = savedAgent
                    } else {
                        // Web behavior: default to first visible agent (backend sorts by default_agent)
                        _selectedAgent.value = visibleAgents.firstOrNull()?.name
                    }
                }
            } catch (_: Exception) { /* silent fail */ }
        }
    }

    fun loadCommands() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId)
                if (server == null) {
                    Log.e(TAG, "loadCommands: server not found for serverId=$serverId")
                    return@launch
                }
                val commandList = api.getCommands(server)
                Log.d(TAG, "loadCommands: loaded ${commandList.size} commands")
                _commands.value = commandList
            } catch (e: Exception) {
                Log.e(TAG, "loadCommands: FAILED", e)
            }
        }
    }

    fun selectAgent(agent: String?) {
        _selectedAgent.value = agent
        if (agent != null) {
            viewModelScope.launch { settingsRepository.setRecentAgent(serverId, agent) }
        }
    }
    fun selectModel(model: ModelRef?) {
        _selectedModel.value = model
        _modelDefaultsApplied = true
        if (model != null) {
            val modelVariants = _providers.value
                .find { it.id == model.providerID }
                ?.models?.get(model.modelID)
                ?.variantNames ?: emptyList()
            val currentVariant = _selectedVariant.value
            if (modelVariants.isNotEmpty()) {
                // If current variant is not valid for this model, try saved preference then middle default
                if (currentVariant == null || currentVariant !in modelVariants) {
                    viewModelScope.launch {
                        val saved = settingsRepository.getRecentVariant(serverId, model).first()
                        if (saved != null && saved in modelVariants) {
                            _selectedVariant.value = saved
                        } else {
                            _selectedVariant.value = modelVariants[modelVariants.size / 2]
                        }
                    }
                }
            } else {
                _selectedVariant.value = null
            }
            viewModelScope.launch { settingsRepository.setRecentModel(serverId, model) }
        } else {
            _selectedVariant.value = null
        }
    }
    fun selectVariant(variant: String?) {
        _selectedVariant.value = variant
        val model = _selectedModel.value
        if (model != null) {
            viewModelScope.launch {
                if (variant != null) {
                    settingsRepository.setRecentVariant(serverId, model, variant)
                } else {
                    settingsRepository.clearRecentVariant(serverId, model)
                }
            }
        }
    }

    /**
     * Load model fallback sources: global config + locally saved preference.
     */
    private fun loadConfiguredModel() {
        // Load saved model preference (one-shot, update on change)
        viewModelScope.launch {
            settingsRepository.getRecentModel(serverId).first()?.let { _savedModel.value = it }
        }
        // Load configured model from global config
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val configJson = api.getConfig(server)
                val modelStr = configJson.jsonObject["model"]?.jsonPrimitive?.content
                if (modelStr != null && modelStr.contains("/")) {
                    val parts = modelStr.split("/", limit = 2)
                    if (parts.size == 2) {
                        _configuredModel.value = ModelRef(
                            providerID = parts[0],
                            modelID = parts[1],
                        )
                    }
                }
                tryApplyModelDefaults()
            } catch (_: Exception) { /* silent fail */ }
        }
    }

    /**
     * Apply model selection using the Web-style 3-tier fallback:
     *   1. configuredModel  — from config.model ("provider/model")
     *   2. recentModel      — locally saved per-server model preference
     *   3. defaultModel     — provider defaults from GET /provider
     *
     * Only applies once, and only when no model is selected yet
     * (user hasn't manually chosen and draft hasn't restored).
     */
    private fun tryApplyModelDefaults() {
        if (_modelDefaultsApplied) return
        if (_selectedModel.value != null) {
            _modelDefaultsApplied = true
            return
        }

        val modelToApply: ModelRef? = when {
            // Tier 1: configured model from global config
            _configuredModel.value?.let { isValidModelRef(it) } == true -> _configuredModel.value
            // Tier 2: locally saved recent model preference
            _savedModel.value?.let { isValidModelRef(it) } == true -> _savedModel.value
            // Tier 3: provider defaults from GET /provider response
            else -> {
                val defaults = _providerDefaults.value
                if (defaults.isNotEmpty()) {
                    val providers = _providers.value
                    defaults.entries.firstOrNull { entry ->
                        providers.any { it.id == entry.key && it.models.containsKey(entry.value) }
                    }?.let { ModelRef(providerID = it.key, modelID = it.value) }
                } else null
            }
        }

        if (modelToApply != null) {
            selectModel(modelToApply) // Use selectModel() to get variant restore logic
            return
        }
    }

    /**
     * Check that a ModelRef points to an actual available model.
     */
    private fun isValidModelRef(ref: ModelRef): Boolean {
        if (ref.providerID.isBlank() || ref.modelID.isBlank()) return false
        return _providers.value.any { it.id == ref.providerID && it.models.containsKey(ref.modelID) }
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
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.replyPermission(server, permissionId, PermissionReply(reply, message))
                eventReducer.removePermission(sessionId, permissionId)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to reply"
            }
        }
    }

    fun replyQuestion(question: QuestionRequest, answers: List<List<String>>) {
        viewModelScope.launch {
            _submittingQuestionIds.value = _submittingQuestionIds.value + question.id
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                    return@launch
                }
                val directory = eventReducer.sessions.value[question.sessionID]?.directory
                val success = api.replyQuestion(server, question.id, answers, directory = directory)
                if (!success) {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                    _error.value = "Failed to reply to question"
                }
                // On success: keep submitting state, wait for SSE question.replied event to remove
            } catch (e: Exception) {
                _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to reply"
            }
        }
    }

    fun rejectQuestion(question: QuestionRequest) {
        viewModelScope.launch {
            _submittingQuestionIds.value = _submittingQuestionIds.value + question.id
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                    return@launch
                }
                val directory = eventReducer.sessions.value[question.sessionID]?.directory
                val success = api.rejectQuestion(server, question.id, directory = directory)
                if (!success) {
                    _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                    _error.value = "Failed to reject question"
                }
                // On success: keep submitting state, wait for SSE question.rejected event to remove
            } catch (e: Exception) {
                _submittingQuestionIds.value = _submittingQuestionIds.value - question.id
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to reject"
            }
        }
    }

    fun saveDraft(text: String) {
        viewModelScope.launch {
            if (text.isBlank() && _draftImageUris.value.isEmpty()) {
                draftRepository.clearDraft(sessionId)
            } else {
                val draft = ChatDraft(
                    text = text,
                    selectedAgent = _selectedAgent.value,
                    selectedModel = _selectedModel.value,
                    selectedVariant = _selectedVariant.value,
                    imageUris = _draftImageUris.value,
                )
                draftRepository.saveDraft(sessionId, draft)
            }
        }
    }

    fun addDraftImage(uri: String) {
        viewModelScope.launch {
            val updated = _draftImageUris.value + uri
            _draftImageUris.value = updated
            val draft = ChatDraft(
                text = "",
                selectedAgent = _selectedAgent.value,
                selectedModel = _selectedModel.value,
                selectedVariant = _selectedVariant.value,
                imageUris = updated,
            )
            draftRepository.saveDraft(sessionId, draft)
        }
    }

    fun removeDraftImage(uri: String) {
        viewModelScope.launch {
            val updated = _draftImageUris.value - uri
            _draftImageUris.value = updated
            val draft = ChatDraft(
                text = "",
                selectedAgent = _selectedAgent.value,
                selectedModel = _selectedModel.value,
                selectedVariant = _selectedVariant.value,
                imageUris = updated,
            )
            draftRepository.saveDraft(sessionId, draft)
        }
    }

    /**
     * Attach an image from a content URI. Compresses to WebP and stores as AttachedImage.
     */
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

    /**
     * Remove an attached image by index.
     */
    fun removeImage(index: Int) {
        val current = _attachedImages.value
        if (index in current.indices) {
            _attachedImages.value = current.toMutableList().apply { removeAt(index) }
        }
    }

    /**
     * Clear all attached images.
     */
    fun clearAttachedImages() {
        _attachedImages.value = emptyList()
    }

    // === Mention Management ===

    /**
     * Add a mention item (file or agent) from the @ popup.
     * @param mention The mention to add
     * @param start Start index of the mention text in the input field
     * @param end End index of the mention text in the input field
     */
    fun addMention(mention: MentionItem, start: Int, end: Int) {
        val positioned = when (mention) {
            is MentionItem.FileMention -> mention.copy(start = start, end = end)
            is MentionItem.AgentMention -> mention.copy(start = start, end = end)
        }
        _mentions.value = _mentions.value + positioned
    }

    /**
     * Remove a mention by its display text.
     * Called when the user backspaces over a mention.
     */
    fun removeMention(displayText: String) {
        _mentions.value = _mentions.value.filter { it.displayText != displayText }
    }

    /**
     * Clear all mentions. Called after sending a message.
     */
    fun clearMentions() {
        _mentions.value = emptyList()
    }

    /**
     * Reconcile mentions against current text — removes any mentions whose
     * displayText no longer appears in the text (user edited/deleted them),
     * and updates start/end positions for surviving mentions.
     */
    fun reconcileMentions(text: String) {
        val updated = mutableListOf<MentionItem>()
        for (mention in _mentions.value) {
            val index = text.indexOf(mention.displayText)
            if (index >= 0) {
                // Found the displayText — update positions
                val newMention = when (mention) {
                    is MentionItem.FileMention -> mention.copy(
                        start = index,
                        end = index + mention.displayText.length,
                    )
                    is MentionItem.AgentMention -> mention.copy(
                        start = index,
                        end = index + mention.displayText.length,
                    )
                }
                updated.add(newMention)
            }
            // If displayText not found, mention is dropped (not added to updated)
        }
        _mentions.value = updated
    }

    /**
     * Get the set of mention display texts for VisualTransformation.
     */
    fun getMentionDisplayTexts(): Set<String> {
        return _mentions.value.map { it.displayText }.toSet()
    }

    /**
     * Search files by query for @ mention popup.
     */
    suspend fun searchFiles(query: String): List<String> {
        return try {
            val server = serverRepository.getServer(serverId) ?: return emptyList()
            val directory = eventReducer.sessions.value[sessionId]?.directory
            api.fileSearch(server, query, limit = 20, directory = directory)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.deleteMessage(server, sessionId, messageId, directory = directory)
                // Optimistically remove from local state
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

    /**
     * Refresh session diffs by fetching from the API and pushing into the EventReducer.
     */
    fun refreshSessionDiffs() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val workspace = eventReducer.sessions.value[sessionId]?.workspaceID
                val diffs = api.getSessionDiff(server, sessionId, directory = directory, workspace = workspace)
                eventReducer.processEvent(serverId, SseEvent.SessionDiff(sessionId, diffs))
            } catch (_: Exception) { }
        }
    }

    /**
     * Clear session diffs for the current session.
     */
    fun dismissDiffs() {
        eventReducer.processEvent(serverId, SseEvent.SessionDiff(sessionId, emptyList()))
    }

    fun forkSession(messageId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val currentDirectory = uiState.value.session?.directory
                val forked = api.forkSession(server, sessionId, messageId, directory = currentDirectory)
                onResult(forked.id)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to fork session"
            }
        }
    }

    fun shareSession(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val share = api.shareSession(server, sessionId, directory = directory)
                onResult(share.url)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to share session"
            }
        }
    }

    fun unshareSession() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.unshareSession(server, sessionId, directory = directory)
                // Refresh session to clear share field
                val updated = api.getSession(server, sessionId, directory = directory)
                eventReducer.processEvent(
                    serverId,
                    me.xiaok.opencode.domain.model.SseEvent.SessionUpdated(updated)
                )
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to unshare session"
            }
        }
    }

    fun revertSession(messageId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.revertSession(server, sessionId, messageId, directory = directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to revert session"
            }
        }
    }

    fun summarizeSession() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val model = _selectedModel.value
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.summarizeSession(
                    conn = server,
                    sessionId = sessionId,
                    providerId = model?.providerID?.ifBlank { null },
                    modelId = model?.modelID?.ifBlank { null },
                    directory = directory,
                )
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to summarize session"
            }
        }
    }

    fun unrevertSession() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.unrevertSession(server, sessionId, directory = directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to unrevert session"
            }
        }
    }

    fun renameSession(newTitle: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.updateSession(server, sessionId, title = newTitle, directory = directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to rename session"
            }
        }
    }

    fun deleteSession() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                val deleted = api.deleteSession(server, sessionId, directory = directory)
                if (deleted) {
                    val session = eventReducer.sessions.value[sessionId] ?: return@launch
                    eventReducer.processEvent(serverId, SseEvent.SessionDeleted(session))
                }
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
            }
        }
    }

    /**
     * Export session messages as a markdown string.
     * Returns the full exported text on success.
     */
    suspend fun exportSession(): String {
        val server = serverRepository.getServer(serverId) ?: throw IllegalStateException("Server not found")
        val session = eventReducer.sessions.value[sessionId] ?: throw IllegalStateException("Session not found")
        val messages = eventReducer.messages.value[sessionId] ?: emptyList()

        val sb = StringBuilder()
        sb.appendLine("# ${session.title.ifEmpty { "Chat" }}")
        sb.appendLine()

        for (message in messages) {
            val role = if (message.isUser) "## User" else "## Assistant"
            sb.appendLine(role)
            sb.appendLine()
            val textParts = message.parts.filterIsInstance<Part.Text>()
            for (part in textParts) {
                if (part.text.isNotEmpty()) {
                    sb.appendLine(part.text)
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }

    // === Session Statistics ===

    private data class SessionStats(
        val contextUsagePercent: Int = 0,
        val totalTokens: Long = 0L,
        val totalCost: Double = 0.0,
        val conversationTurns: Int = 0,
    )

    /**
     * Compute session-level statistics from messages.
     * - Total tokens & context usage %: from the last assistant message with tokens (matching Web behavior)
     * - Cost: cumulative across all assistant messages
     * - Conversation turns: count of user messages
     */
    private fun computeSessionStats(
        messages: List<Message>,
        providers: List<Provider>,
        selectedModel: ModelRef?,
    ): SessionStats {
        val userCount = messages.count { it.isUser }

        // Cost: cumulative across all assistant messages (same as Web)
        var totalCost = 0.0
        for (message in messages) {
            val cost = message.info.cost
            if (cost != null) {
                totalCost += cost
            }
        }

        // Total tokens & context usage: from the last assistant message with tokens (same as Web)
        // Web picks the last assistant message and computes total = input + output + reasoning + cache.read + cache.write
        // Context usage % = total / model.contextLimit × 100
        val lastAssistantWithTokens = messages.lastOrNull {
            it.isAssistant && it.info.tokens != null && it.info.tokens!!.total > 0
        }
        val tokens = lastAssistantWithTokens?.info?.tokens
        // total = input + output + reasoning + cache.read + cache.write
        // Backend normalizes: input = inputTokens - cacheRead - cacheWrite (can be negative),
        // so the sum correctly reconstructs the total. Do NOT coerce individual fields to 0.
        val totalTokens = if (tokens != null) {
            tokens.input +
                tokens.output +
                tokens.reasoning +
                tokens.cache.read +
                tokens.cache.write
        } else {
            0L
        }

        val contextLimit = findContextLimit(providers, selectedModel, messages)

        val contextUsagePercent = if (contextLimit > 0 && totalTokens > 0) {
            ((totalTokens.toDouble() / contextLimit) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        return SessionStats(
            contextUsagePercent = contextUsagePercent,
            totalTokens = totalTokens,
            totalCost = totalCost,
            conversationTurns = userCount,
        )
    }

    /**
     * Build mapping: subtask part ID → child session ID.
     * Extracted as a pure function so it can be cached in a separate StateFlow.
     */
    private fun computeChildSessionIds(
        parentSessionId: String,
        messages: List<Message>,
        allParts: Map<String, List<Part>>,
        allSessions: Map<String, Session>,
    ): Map<String, String> {
        val childSessions = allSessions
            .filter { (_, s) -> s.parentID == parentSessionId }
            .values
            .sortedBy { it.time.created }

        val subSessionIds = mutableMapOf<String, String>()
        if (childSessions.isNotEmpty()) {
            val subtaskParts = mutableListOf<Pair<String, Part.Subtask>>()
            for (msg in messages) {
                val msgParts = allParts[msg.id] ?: msg.parts
                for (part in msgParts) {
                    if (part is Part.Subtask) {
                        subtaskParts.add(part.id to part)
                    }
                }
            }

            // Matching strategy (priority order):
            // 1. Exact match: child session title contains "(@agentName" pattern
            //    e.g. "Explore project (@explore subagent)" matches agent "explore"
            // 2. Contains match: child session title contains the agent name (case-insensitive)
            // 3. Positional fallback: assign remaining unmatched sessions in order
            val unmatchedSessions = childSessions.toMutableList()
            val matchedSessionIds = mutableSetOf<String>()

            for ((partId, subtask) in subtaskParts) {
                val agent = subtask.agent
                val bestMatch = unmatchedSessions.firstOrNull { child ->
                    child.title.contains("(@${agent}", ignoreCase = true) &&
                            child.id !in matchedSessionIds
                } ?: unmatchedSessions.firstOrNull { child ->
                    child.title.contains(agent, ignoreCase = true) &&
                            child.id !in matchedSessionIds
                }

                if (bestMatch != null) {
                    subSessionIds[partId] = bestMatch.id
                    unmatchedSessions.remove(bestMatch)
                    matchedSessionIds.add(bestMatch.id)
                } else if (unmatchedSessions.isNotEmpty()) {
                    val fallback = unmatchedSessions.removeAt(0)
                    subSessionIds[partId] = fallback.id
                    matchedSessionIds.add(fallback.id)
                }
            }
        }
        return subSessionIds
    }

    /**
     * Find the context window limit for the current model.
     * Priority: selectedModel > last assistant message's model > first provider model
     */
    private fun findContextLimit(
        providers: List<Provider>,
        selectedModel: ModelRef?,
        messages: List<Message>,
    ): Long {
        // Try selected model first
        if (selectedModel != null) {
            val limit = getModelContextLimit(providers, selectedModel.providerID, selectedModel.modelID)
            if (limit > 0) return limit
        }

        // Try last assistant message's model
        val lastAssistant = messages.lastOrNull { it.isAssistant }
        if (lastAssistant != null) {
            val providerID = lastAssistant.info.providerID
            val modelID = lastAssistant.info.modelID
            if (providerID != null && modelID != null) {
                val limit = getModelContextLimit(providers, providerID, modelID)
                if (limit > 0) return limit
            }
        }

        return 0L
    }

    private fun getModelContextLimit(providers: List<Provider>, providerID: String, modelID: String): Long {
        return providers
            .find { it.id == providerID }
            ?.models?.get(modelID)
            ?.limit?.context
            ?: 0L
    }

    /**
     * Find all descendant session IDs (children, grandchildren, etc.) of the given session.
     * Matches Web's sessionQuestionRequest() recursive tree traversal behavior.
     */
    private fun findAllDescendants(
        parentSessionId: String,
        allSessions: Map<String, Session>,
    ): List<String> {
        val result = mutableListOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(parentSessionId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            allSessions.values
                .filter { it.parentID == current }
                .forEach { child ->
                    result.add(child.id)
                    queue.add(child.id)
                }
        }
        return result
    }

    // === Built-in Command Execution ===

    /**
     * Execute a built-in command. Returns true if the command was handled locally.
     * Navigation commands return false (caller handles navigation).
     */
    fun executeBuiltInCommand(
        command: me.xiaok.opencode.domain.model.BuiltInCommand,
    ): Boolean {
        return when (command.id) {
            "undo" -> { undoLastTurn(); true }
            "redo" -> { redoLastTurn(); true }
            "compact" -> { summarizeSession(); true }
            "share" -> { shareSession {}; true }
            "unshare" -> { unshareSession(); true }
            "fork" -> { forkFromLatestMessage(); true }
            "archive" -> { archiveCurrentSession(); true }
            "variant" -> { cycleVariant(); true }
            "theme" -> { cycleTheme(); true }
            // Navigation commands — return false so caller navigates
            "new", "sessions", "terminal", "files", "settings", "mcp",
            "model", "agent" -> false
            else -> false
        }
    }

    private fun cycleTheme() {
        viewModelScope.launch {
            val current = settingsRepository.theme.first()
            val next = when (current) {
                "system" -> "light"
                "light" -> "dark"
                else -> "dark"
            }
            settingsRepository.setTheme(next)
        }
    }

    /**
     * Undo: abort if busy, then revert to the last user message.
     */
    private fun undoLastTurn() {
        viewModelScope.launch {
            val session = eventReducer.sessions.value[sessionId] ?: return@launch
            val messages = eventReducer.messages.value[sessionId] ?: emptyList()
            val directory = session.directory

            // Abort if session is running
            if (eventReducer.sessionStatuses.value[sessionId] != SessionStatus.IDLE) {
                try {
                    val server = serverRepository.getServer(serverId) ?: return@launch
                    api.abortSession(server, sessionId, directory = directory)
                } catch (_: Exception) {}
            }

            // Find the last user message
            val lastUserMessage = messages.lastOrNull { it.isUser } ?: return@launch

            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.revertSession(server, sessionId, lastUserMessage.id, directory = directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to undo"
            }
        }
    }

    /**
     * Redo: if there's a revert point, find the next user message after it
     * and revert to that. If none, unrevert fully.
     */
    private fun redoLastTurn() {
        viewModelScope.launch {
            val session = eventReducer.sessions.value[sessionId] ?: return@launch
            val revertInfo = session.revert ?: return@launch
            val messages = eventReducer.messages.value[sessionId] ?: emptyList()
            val directory = session.directory

            try {
                val server = serverRepository.getServer(serverId) ?: return@launch

                // Find the first user message after the revert point
                val reverting = revertInfo.messageID
                val afterRevert = messages.dropWhile { it.id != reverting }.drop(1)
                val nextUserMsg = afterRevert.firstOrNull { it.isUser }

                if (nextUserMsg != null) {
                    api.revertSession(server, sessionId, nextUserMsg.id, directory = directory)
                } else {
                    api.unrevertSession(server, sessionId, directory = directory)
                }
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to redo"
            }
        }
    }

    /**
     * Fork from the latest message (simplified version for command use).
     */
    private fun forkFromLatestMessage() {
        viewModelScope.launch {
            val messages = eventReducer.messages.value[sessionId] ?: emptyList()
            val lastMsgId = messages.lastOrNull()?.id ?: return@launch
            forkSession(lastMsgId) { /* navigated via onNavigateToForkedSession if wired */ }
        }
    }

    /**
     * Archive the current session and navigate away.
     */
    private fun archiveCurrentSession() {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val directory = eventReducer.sessions.value[sessionId]?.directory
                api.updateSession(server, sessionId, archived = System.currentTimeMillis(), directory = directory)
                // Session list will be updated via SSE
            } catch (e: Exception) {
                errorCollector.logError(e, "Chat")
                _error.value = e.message ?: "Failed to archive"
            }
        }
    }

    /**
     * Cycle through model variants (fast → think → agentic → null → fast).
     */
    private fun cycleVariant() {
        val variants = listOf("fast", "think", "agentic")
        val current = _selectedVariant.value
        if (current == null) {
            _selectedVariant.value = variants.first()
        } else {
            val idx = variants.indexOf(current)
            if (idx >= 0 && idx < variants.lastIndex) {
                _selectedVariant.value = variants[idx + 1]
            } else {
                _selectedVariant.value = null
            }
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
