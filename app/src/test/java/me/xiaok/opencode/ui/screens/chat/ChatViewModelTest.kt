package me.xiaok.opencode.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.DraftRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.MetadataCache
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.fixtures.TestFixtures
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
import me.xiaok.opencode.utils.CoroutineTestRule
import me.xiaok.opencode.utils.ErrorCollector
import me.xiaok.opencode.utils.ImageCompressor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val testServer = TestFixtures.testServerConnection()
    private val testSession = TestFixtures.testSession()
    private val testMessage = TestFixtures.testMessage()
    private val testUserMessage = TestFixtures.testMessage(
        info = TestFixtures.testUserMessageInfo()
    )

    private lateinit var api: OpenCodeApi
    private lateinit var eventReducer: EventReducer
    private lateinit var serverRepository: ServerRepository
    private lateinit var draftRepository: DraftRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var imageCompressor: ImageCompressor
    private lateinit var cacheRepository: CacheRepository
    private lateinit var metadataCache: MetadataCache
    private lateinit var errorCollector: ErrorCollector
    private lateinit var sessionStatsUseCase: SessionStatsUseCase
    private lateinit var draftManagementUseCase: DraftManagementUseCase
    private lateinit var mentionManagementUseCase: MentionManagementUseCase
    private lateinit var permissionQuestionUseCase: PermissionQuestionUseCase
    private lateinit var sessionNavigationUseCase: SessionNavigationUseCase
    private lateinit var sessionOpsUseCase: me.xiaok.opencode.ui.screens.chat.usecases.SessionOpsUseCase
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var modelSelectionUseCase: ModelSelectionUseCase
    private lateinit var messageLoadingUseCase: MessageLoadingUseCase
    private lateinit var chatCommandUseCase: ChatCommandUseCase
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0

        testScope = coroutineRule.testScope
        cacheRepository = mockk(relaxed = true)
        eventReducer = EventReducer(cacheRepository, testScope)

        api = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)
        draftRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        imageCompressor = mockk(relaxed = true)
        errorCollector = mockk(relaxed = true)
        metadataCache = mockk(relaxed = true)
        sessionStatsUseCase = SessionStatsUseCase()
        draftManagementUseCase = DraftManagementUseCase(draftRepository)
        mentionManagementUseCase = MentionManagementUseCase(api, eventReducer, serverRepository)
        permissionQuestionUseCase = PermissionQuestionUseCase(api, eventReducer, serverRepository, errorCollector)
        sessionNavigationUseCase = SessionNavigationUseCase(api, eventReducer, serverRepository)
        sessionOpsUseCase = me.xiaok.opencode.ui.screens.chat.usecases.SessionOpsUseCase(api, eventReducer, serverRepository, errorCollector)
        sendMessageUseCase = SendMessageUseCase(api, eventReducer, serverRepository, draftRepository, settingsRepository, errorCollector)
        modelSelectionUseCase = ModelSelectionUseCase(api, eventReducer, serverRepository, settingsRepository, metadataCache)
        messageLoadingUseCase = MessageLoadingUseCase(api, eventReducer, serverRepository, settingsRepository, errorCollector)
        chatCommandUseCase = ChatCommandUseCase(eventReducer, settingsRepository, sessionOpsUseCase, modelSelectionUseCase, errorCollector)

        every { serverRepository.getServer(any()) } returns testServer
        every { serverRepository.servers } returns MutableStateFlow(listOf(testServer))
        coEvery { api.listMessages(any(), any(), limit = any(), before = any()) } returns OpenCodeApi.MessagesPage(emptyList(), null)
        coEvery { api.getProviders(any()) } returns TestFixtures.testProviderList()
        coEvery { api.getAgents(any()) } returns listOf(
            TestFixtures.testAgentConfig(),
            TestFixtures.testAgentConfig(name = "explore", mode = "subagent")
        )
        coEvery { api.getCommands(any()) } returns listOf(TestFixtures.testCommandInfo())
        coEvery { metadataCache.getProviders(any(), any()) } returns TestFixtures.testProviderList()
        coEvery { metadataCache.getAgents(any(), any()) } returns listOf(
            TestFixtures.testAgentConfig(),
            TestFixtures.testAgentConfig(name = "explore", mode = "subagent")
        )
        coEvery { metadataCache.getCommands(any(), any()) } returns listOf(TestFixtures.testCommandInfo())
        coEvery { api.getSessionChildren(any(), any()) } returns emptyList()
        coEvery { api.replyQuestion(any(), any(), any()) } returns true
        coEvery { api.rejectQuestion(any(), any()) } returns true
        every { settingsRepository.initialMessages } returns flowOf(50)
        coEvery { settingsRepository.chatFontSize } returns flowOf("medium")
        coEvery { settingsRepository.getRecentAgent(any()) } returns flowOf(null)
        coEvery { settingsRepository.getRecentModel(any()) } returns flowOf(null)
        coEvery { api.getConfig(any()) } returns kotlinx.serialization.json.JsonObject(emptyMap())
        every { draftRepository.getDraft(any()) } returns MutableStateFlow(null)
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(
        serverId: String = testServer.id,
        sessionId: String = testSession.id,
    ): ChatViewModel {
        val savedStateHandle = SavedStateHandle(mapOf(
            "serverId" to serverId,
            "sessionId" to sessionId,
        ))
        return ChatViewModel(
            savedStateHandle,
            api,
            eventReducer,
            serverRepository,
            draftRepository,
            settingsRepository,
            imageCompressor,
            errorCollector,
            sessionStatsUseCase,
            draftManagementUseCase,
            mentionManagementUseCase,
            permissionQuestionUseCase,
            sessionNavigationUseCase,
            sessionOpsUseCase,
            sendMessageUseCase,
            modelSelectionUseCase,
            messageLoadingUseCase,
            chatCommandUseCase,
        )
    }

    // ====================================================================
    // loadMessages
    // ====================================================================

    @Test
    fun `loadMessages loads from API and sets messages in EventReducer`() = testScope.runTest {
        val messages = listOf(testUserMessage, testMessage)
        coEvery { api.listMessages(any(), any(), limit = any()) } returns OpenCodeApi.MessagesPage(messages, null)

        val vm = createViewModel()
        advanceUntilIdle()

        val stored = eventReducer.messages.value[testSession.id]
        assertNotNull(stored)
        assertEquals(2, stored!!.size)
    }

    @Test
    fun `loadMessages sets error when server not found`() = testScope.runTest {
        every { serverRepository.getServer(any()) } returns null

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.error != null }
        assertEquals("Server not found", state.error)
        collectJob.cancel()
    }

    @Test
    fun `loadMessages sets error on API exception`() = testScope.runTest {
        coEvery { api.listMessages(any(), any(), limit = any()) } throws RuntimeException("Network error")

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.error != null }
        assertEquals("Network error", state.error)
        collectJob.cancel()
    }

    // ====================================================================
    // sendMessage
    // ====================================================================

    @Test
    fun `sendMessage calls API with text parts and clears draft`() = testScope.runTest {
        coEvery { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) } just Runs
        coEvery { api.listMessages(any(), any(), limit = any()) } returns OpenCodeApi.MessagesPage(emptyList(), null)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("Hello world")
        advanceUntilIdle()

        coVerify { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) }
        coVerify { draftRepository.clearDraft(testSession.id) }
    }

    @Test
    fun `sendMessage with blank text is a no-op`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) }
    }

    @Test
    fun `sendMessage with isSending true is a no-op`() = testScope.runTest {
        coEvery { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) } coAnswers {
            delay(1000)
        }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("first")
        advanceTimeBy(1)
        vm.sendMessage("second")
        advanceUntilIdle()

        coVerify(exactly = 1) { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) }
    }

    @Test
    fun `sendMessage with shell command prefix calls runShell`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("!ls -la")
        advanceUntilIdle()

        coVerify { api.runShell(any(), any(), "ls -la") }
        coVerify(exactly = 0) { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) }
    }

    @Test
    fun `sendMessage with blank text and no images is a no-op`() = testScope.runTest {
        coEvery { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) } just Runs
        coEvery { api.listMessages(any(), any(), limit = any()) } returns OpenCodeApi.MessagesPage(emptyList(), null)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("")
        advanceUntilIdle()

        coVerify(exactly = 0) { api.promptAsync(any(), any(), parts = any(), agent = any(), model = any()) }
    }

    // ====================================================================
    // loadProviders
    // ====================================================================

    @Test
    fun `loadProviders loads from API and filters connected`() = testScope.runTest {
        val provider = TestFixtures.testProvider()
        val providerList = TestFixtures.testProviderList(
            all = listOf(provider),
            connected = listOf("anthropic"),
        )
        coEvery { api.getProviders(any()) } returns providerList

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.providers.isNotEmpty() }
        assertEquals(1, state.providers.size)
        assertEquals("anthropic", state.providers.first().id)
        collectJob.cancel()
    }

    // ====================================================================
    // loadAgents
    // ====================================================================

    @Test
    fun `loadAgents loads from API and selects default agent`() = testScope.runTest {
        val agents = listOf(
            TestFixtures.testAgentConfig(name = "code", mode = "primary"),
            TestFixtures.testAgentConfig(name = "explore", mode = "subagent", hidden = false),
        )
        coEvery { api.getAgents(any()) } returns agents

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.agents.size == 2 }
        assertEquals(2, state.agents.size)
        assertEquals("code", state.selectedAgent)
        collectJob.cancel()
    }

    // ====================================================================
    // loadCommands
    // ====================================================================

    @Test
    fun `loadCommands loads from API`() = testScope.runTest {
        val commands = listOf(
            TestFixtures.testCommandInfo(name = "commit"),
            TestFixtures.testCommandInfo(name = "review"),
        )
        coEvery { api.getCommands(any()) } returns commands

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.commands.size == 2 }
        assertEquals(2, state.commands.size)
        collectJob.cancel()
    }

    // ====================================================================
    // selectAgent
    // ====================================================================

    @Test
    fun `selectAgent updates state`() = testScope.runTest {
        coEvery { settingsRepository.setRecentAgent(any(), any()) } just Awaits

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectAgent("explore")
        advanceUntilIdle()

        val state = vm.uiState.first { it.selectedAgent == "explore" }
        assertEquals("explore", state.selectedAgent)
        collectJob.cancel()

        coVerify { settingsRepository.setRecentAgent(testServer.id, "explore") }
    }

    // ====================================================================
    // selectModel
    // ====================================================================

    @Test
    fun `selectModel updates state`() = testScope.runTest {
        coEvery { settingsRepository.setRecentModel(any(), any()) } just Awaits
        val model = TestFixtures.testModelRef()

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectModel(model)
        advanceUntilIdle()

        val state = vm.uiState.first { it.selectedModel != null }
        assertEquals(model, state.selectedModel)
        collectJob.cancel()

        coVerify { settingsRepository.setRecentModel(testServer.id, model) }
    }

    // ====================================================================
    // selectVariant
    // ====================================================================

    @Test
    fun `selectVariant updates state`() = testScope.runTest {
        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectVariant("fast")
        advanceUntilIdle()

        val state = vm.uiState.first { it.selectedVariant == "fast" }
        assertEquals("fast", state.selectedVariant)
        collectJob.cancel()
    }

    // ====================================================================
    // replyPermission
    // ====================================================================

    @Test
    fun `replyPermission calls API and removes permission`() = testScope.runTest {
        val perm = TestFixtures.testPermissionRequest()
        eventReducer.processEvent(testServer.id, SseEvent.PermissionAsked(perm))
        advanceUntilIdle()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.replyPermission(perm.id, "once")
        advanceUntilIdle()

        coVerify { api.replyPermission(any(), perm.id, any()) }
        val remaining = eventReducer.permissions.value[testSession.id] ?: emptyList()
        assertTrue(remaining.none { it.id == perm.id })
    }

    // ====================================================================
    // replyQuestion
    // ====================================================================

    @Test
    fun `replyQuestion calls API and removes question`() = testScope.runTest {
        val question = TestFixtures.testQuestionRequest()
        eventReducer.processEvent(testServer.id, SseEvent.QuestionAsked(question))
        advanceUntilIdle()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.replyQuestion(question, listOf(listOf("React")))
        advanceUntilIdle()

        coVerify { api.replyQuestion(any(), question.id, listOf(listOf("React"))) }
        val remaining = eventReducer.questions.value[testSession.id] ?: emptyList()
        assertTrue(remaining.none { it.id == question.id })
    }

    // ====================================================================
    // rejectQuestion
    // ====================================================================

    @Test
    fun `rejectQuestion calls API and removes question`() = testScope.runTest {
        val question = TestFixtures.testQuestionRequest()
        eventReducer.processEvent(testServer.id, SseEvent.QuestionAsked(question))
        advanceUntilIdle()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.rejectQuestion(question)
        advanceUntilIdle()

        coVerify { api.rejectQuestion(any(), question.id) }
        val remaining = eventReducer.questions.value[testSession.id] ?: emptyList()
        assertTrue(remaining.none { it.id == question.id })
    }

    // ====================================================================
    // saveDraft
    // ====================================================================

    @Test
    fun `saveDraft saves to DraftRepository`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.saveDraft("Hello draft")
        advanceUntilIdle()

        coVerify { draftRepository.saveDraft(testSession.id, match<ChatDraft> { it.text == "Hello draft" }) }
    }

    @Test
    fun `saveDraft with blank text clears draft`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.saveDraft("   ")
        advanceUntilIdle()

        coVerify { draftRepository.clearDraft(testSession.id) }
    }

    // ====================================================================
    // addDraftImage / removeDraftImage
    // ====================================================================

    @Test
    fun `addDraftImage updates draft with new URI`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.addDraftImage("content://image1.png")
        advanceUntilIdle()

        coVerify {
            draftRepository.saveDraft(
                testSession.id,
                match<ChatDraft> { it.imageUris.contains("content://image1.png") }
            )
        }
    }

    @Test
    fun `removeDraftImage removes URI from draft`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.addDraftImage("content://image1.png")
        advanceUntilIdle()

        vm.removeDraftImage("content://image1.png")
        advanceUntilIdle()

        coVerify {
            draftRepository.saveDraft(
                testSession.id,
                match<ChatDraft> { !it.imageUris.contains("content://image1.png") }
            )
        }
    }

    // ====================================================================
    // deleteMessage
    // ====================================================================

    @Test
    fun `deleteMessage calls API and processes removal event`() = testScope.runTest {
        eventReducer.setMessages(testSession.id, listOf(testMessage))
        advanceUntilIdle()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteMessage(testMessage.info.id)
        advanceUntilIdle()

        coVerify { api.deleteMessage(any(), testSession.id, testMessage.info.id) }
        val msgs = eventReducer.messages.value[testSession.id] ?: emptyList()
        assertTrue(msgs.none { it.info.id == testMessage.info.id })
    }

    // ====================================================================
    // forkSession
    // ====================================================================

    @Test
    fun `forkSession calls API and invokes callback`() = testScope.runTest {
        val forkedSession = TestFixtures.testSession(id = "ses_forked")
        coEvery { api.forkSession(any(), any(), any()) } returns forkedSession

        val vm = createViewModel()
        advanceUntilIdle()

        var result: String? = null
        vm.forkSession("msg_123") { result = it }
        advanceUntilIdle()

        coVerify { api.forkSession(any(), testSession.id, "msg_123") }
        assertEquals("ses_forked", result)
    }

    // ====================================================================
    // shareSession
    // ====================================================================

    @Test
    fun `shareSession calls API and invokes callback with URL`() = testScope.runTest {
        val share = TestFixtures.testSessionShare()
        coEvery { api.shareSession(any(), any()) } returns share

        val vm = createViewModel()
        advanceUntilIdle()

        var result: String? = null
        vm.shareSession { result = it }
        advanceUntilIdle()

        coVerify { api.shareSession(any(), testSession.id) }
        assertEquals(share.url, result)
    }

    // ====================================================================
    // unshareSession
    // ====================================================================

    @Test
    fun `unshareSession calls API and refreshes session`() = testScope.runTest {
        val updatedSession = TestFixtures.testSession()
        coEvery { api.unshareSession(any(), any()) } returns true
        coEvery { api.getSession(any(), any()) } returns updatedSession

        val vm = createViewModel()
        advanceUntilIdle()

        vm.unshareSession()
        advanceUntilIdle()

        coVerify { api.unshareSession(any(), testSession.id) }
        coVerify { api.getSession(any(), testSession.id) }
    }

    // ====================================================================
    // revertSession
    // ====================================================================

    @Test
    fun `revertSession calls API`() = testScope.runTest {
        coEvery { api.revertSession(any(), any(), any()) } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.revertSession("msg_revert")
        advanceUntilIdle()

        coVerify { api.revertSession(any(), testSession.id, "msg_revert") }
    }

    // ====================================================================
    // summarizeSession
    // ====================================================================

    @Test
    fun `summarizeSession calls API`() = testScope.runTest {
        coEvery { api.summarizeSession(any(), any()) } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.summarizeSession()
        advanceUntilIdle()

        coVerify { api.summarizeSession(any(), testSession.id) }
    }

    // ====================================================================
    // abortSession
    // ====================================================================

    @Test
    fun `abortSession calls API`() = testScope.runTest {
        coEvery { api.abortSession(any(), any()) } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        vm.abortSession()
        advanceUntilIdle()

        coVerify { api.abortSession(any(), testSession.id) }
    }

    // ====================================================================
    // dismissError
    // ====================================================================

    @Test
    fun `dismissError clears error`() = testScope.runTest {
        coEvery { api.listMessages(any(), any(), limit = any()) } throws RuntimeException("Test error")

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val stateWithErr = vm.uiState.first { it.error != null }
        assertEquals("Test error", stateWithErr.error)

        vm.dismissError()

        val stateAfter = vm.uiState.first { it.error == null }
        assertNull(stateAfter.error)
        collectJob.cancel()
    }

    // ====================================================================
    // exportSession
    // ====================================================================

    @Test
    fun `exportSession returns markdown string`() = testScope.runTest {
        val session = TestFixtures.testSession(title = "My Chat")
        eventReducer.setSessions(testServer.id, listOf(session))
        advanceUntilIdle()

        val messages = listOf(
            TestFixtures.testMessage(
                info = TestFixtures.testUserMessageInfo(id = "msg_u1"),
                parts = listOf(TestFixtures.testTextPart(id = "p1", messageId = "msg_u1", text = "Hello"))
            ),
            TestFixtures.testMessage(
                info = TestFixtures.testMessageInfo(id = "msg_a1", role = "assistant"),
                parts = listOf(TestFixtures.testTextPart(id = "p2", messageId = "msg_a1", text = "Hi there"))
            ),
        )
        eventReducer.setMessages(testSession.id, messages)
        messages.forEach { msg ->
            eventReducer.setParts(msg.info.id, msg.parts)
        }
        advanceUntilIdle()

        val vm = createViewModel()
        advanceUntilIdle()

        val result = vm.exportSession()

        assertTrue(result.startsWith("# My Chat"))
        assertTrue(result.contains("## User"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("## Assistant"))
        assertTrue(result.contains("Hi there"))
    }

    // ====================================================================
    // loadOlderMessages
    // ====================================================================

    @Test
    fun `loadOlderMessages loads older messages with cursor`() = testScope.runTest {
        val initialMessages = listOf(testUserMessage, testMessage)
        val olderMessage = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_old1")
        )

        coEvery { api.listMessages(any(), any(), limit = any()) } returns OpenCodeApi.MessagesPage(initialMessages, "cursor_1")
        coEvery {
            api.listMessages(any(), any(), limit = any(), before = "cursor_1")
        } returns OpenCodeApi.MessagesPage(listOf(olderMessage), null)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadOlderMessages()
        advanceUntilIdle()

        coVerify { api.listMessages(any(), any(), limit = any(), before = "cursor_1") }
        val stored = eventReducer.messages.value[testSession.id] ?: emptyList()
        assertEquals(3, stored.size)
    }

    @Test
    fun `loadOlderMessages sets hasOlderMessages false when no more`() = testScope.runTest {
        val manyMessages = List(50) { i ->
            TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_$i"))
        }
        coEvery { api.listMessages(any(), any(), limit = any()) } returns OpenCodeApi.MessagesPage(manyMessages, "cursor_1")
        coEvery { api.listMessages(any(), any(), limit = any(), before = "cursor_1") } returns OpenCodeApi.MessagesPage(emptyList(), null)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadOlderMessages()
        advanceUntilIdle()

        // _hasOlderMessages is read as .value in uiState combine chain (not a reactive source).
        // Verify via reflection since it won't trigger uiState re-emission on its own.
        val hasOlderField = ChatViewModel::class.java.getDeclaredField("_hasOlderMessages")
        hasOlderField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val hasOlderFlow = hasOlderField.get(vm) as MutableStateFlow<Boolean>
        assertFalse(hasOlderFlow.value)
    }

    @Test
    fun `loadOlderMessages does nothing when already loading more`() = testScope.runTest {
        val manyMessages = List(50) { i ->
            TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_$i"))
        }
        coEvery { api.listMessages(any(), any(), limit = any()) } returns OpenCodeApi.MessagesPage(manyMessages, "cursor_1")
        coEvery { api.listMessages(any(), any(), limit = any(), before = any()) } returns OpenCodeApi.MessagesPage(emptyList(), null)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadOlderMessages()
        vm.loadOlderMessages()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.listMessages(any(), any(), limit = any(), before = any()) }
    }

    // ====================================================================
    // auto-scroll
    // ====================================================================

    @Test
    fun `toggleAutoScroll toggles state`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        // Read _autoScrollEnabled via reflection since it's private and not a flow source in uiState
        val autoScrollField = ChatViewModel::class.java.getDeclaredField("_autoScrollEnabled")
        autoScrollField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val autoScrollFlow = autoScrollField.get(vm) as MutableStateFlow<Boolean>

        assertTrue(autoScrollFlow.value)
        vm.toggleAutoScroll()
        assertFalse(autoScrollFlow.value)
        vm.toggleAutoScroll()
        assertTrue(autoScrollFlow.value)
    }

    @Test
    fun `setAutoScroll sets explicit value`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val autoScrollField = ChatViewModel::class.java.getDeclaredField("_autoScrollEnabled")
        autoScrollField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val autoScrollFlow = autoScrollField.get(vm) as MutableStateFlow<Boolean>

        assertTrue(autoScrollFlow.value)
        vm.setAutoScroll(false)
        assertFalse(autoScrollFlow.value)
        vm.setAutoScroll(true)
        assertTrue(autoScrollFlow.value)
    }

    // ====================================================================
    // clearAttachedImages
    // ====================================================================

    @Test
    fun `clearAttachedImages clears all images`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.clearAttachedImages()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.attachedImages.isEmpty())
    }

    // ====================================================================
    // sendMessage error handling
    // ====================================================================

    @Test
    fun `sendMessage restores draft on failure`() = testScope.runTest {
        coEvery {
            api.promptAsync(any(), any(), parts = any(), agent = any(), model = any())
        } throws RuntimeException("Send failed")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendMessage("Test message")
        advanceUntilIdle()

        coVerify {
            draftRepository.saveDraft(testSession.id, match<ChatDraft> { it.text == "Test message" })
        }
    }

    // ====================================================================
    // init block: missing serverId throws
    // ====================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when serverId is missing`() {
        val handle = SavedStateHandle(mapOf("sessionId" to testSession.id))
        ChatViewModel(
            handle, api, eventReducer, serverRepository,
            draftRepository, settingsRepository, imageCompressor, errorCollector,
            sessionStatsUseCase, draftManagementUseCase, mentionManagementUseCase,
            permissionQuestionUseCase, sessionNavigationUseCase, sessionOpsUseCase,
            sendMessageUseCase, modelSelectionUseCase, messageLoadingUseCase, chatCommandUseCase,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when sessionId is missing`() {
        val handle = SavedStateHandle(mapOf("serverId" to testServer.id))
        ChatViewModel(
            handle, api, eventReducer, serverRepository,
            draftRepository, settingsRepository, imageCompressor, errorCollector,
            sessionStatsUseCase, draftManagementUseCase, mentionManagementUseCase,
            permissionQuestionUseCase, sessionNavigationUseCase, sessionOpsUseCase,
            sendMessageUseCase, modelSelectionUseCase, messageLoadingUseCase, chatCommandUseCase,
        )
    }

    // ====================================================================
    // childSessions (Hover Sentinel)
    // ====================================================================

    @Test
    fun `childSessions empty when no children exist`() = testScope.runTest {
        eventReducer.setSessions(testServer.id, listOf(testSession))

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.childSessions.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `childSessions returns filtered and sorted list`() = testScope.runTest {
        val child1 = TestFixtures.testSession(
            id = "child1",
            parentID = testSession.id,
            title = "First child",
            time = TestFixtures.testSessionTime(created = 100L),
        )
        val child2 = TestFixtures.testSession(
            id = "child2",
            parentID = testSession.id,
            title = "Second child",
            time = TestFixtures.testSessionTime(created = 200L),
        )
        val unrelated = TestFixtures.testSession(
            id = "other",
            parentID = "different-parent",
            title = "Unrelated",
        )

        eventReducer.setSessions(testServer.id, listOf(testSession, child1, child2, unrelated))

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.childSessions.size == 2 }
        assertEquals(2, state.childSessions.size)
        assertEquals("First child", state.childSessions[0].session.title)
        assertEquals("Second child", state.childSessions[1].session.title)
        collectJob.cancel()
    }

    @Test
    fun `childSessions includes status for each child`() = testScope.runTest {
        val child1 = TestFixtures.testSession(
            id = "child-busy",
            parentID = testSession.id,
        )
        val child2 = TestFixtures.testSession(
            id = "child-idle",
            parentID = testSession.id,
        )

        eventReducer.setSessions(testServer.id, listOf(testSession, child1, child2))
        eventReducer.updateSessionStatus("child-busy", SessionStatus.Busy)
        eventReducer.updateSessionStatus("child-idle", SessionStatus.Idle)

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.childSessions.size == 2 }
        val busyChild = state.childSessions.first { it.session.id == "child-busy" }
        val idleChild = state.childSessions.first { it.session.id == "child-idle" }
        assertTrue(busyChild.status is SessionStatus.Busy)
        assertTrue(idleChild.status is SessionStatus.Idle)
        collectJob.cancel()
    }

    @Test
    fun `childSessions updates when SSE status changes`() = testScope.runTest {
        val child = TestFixtures.testSession(
            id = "child-update",
            parentID = testSession.id,
        )

        eventReducer.setSessions(testServer.id, listOf(testSession, child))

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val initialState = vm.uiState.first { it.childSessions.size == 1 }
        assertTrue(initialState.childSessions.first().status is SessionStatus.Idle)

        eventReducer.updateSessionStatus("child-update", SessionStatus.Busy)
        advanceUntilIdle()

        val updatedState = vm.uiState.first {
            it.childSessions.isNotEmpty() && it.childSessions.first().status is SessionStatus.Busy
        }
        assertTrue(updatedState.childSessions.first().status is SessionStatus.Busy)
        collectJob.cancel()
    }

    @Test
    fun `childSessions handles missing status defaults to Idle`() = testScope.runTest {
        val child = TestFixtures.testSession(
            id = "child-no-status",
            parentID = testSession.id,
        )

        eventReducer.setSessions(testServer.id, listOf(testSession, child))

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.childSessions.size == 1 }
        val childInfo = state.childSessions.first()
        assertTrue(childInfo.status is SessionStatus.Idle)
        collectJob.cancel()
    }

    @Test
    fun `childSessions handles Retry status correctly`() = testScope.runTest {
        val child = TestFixtures.testSession(
            id = "child-retry",
            parentID = testSession.id,
        )

        eventReducer.setSessions(testServer.id, listOf(testSession, child))
        eventReducer.updateSessionStatus("child-retry", SessionStatus.Retry(attempt = 3, message = "timeout"))

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.first { it.childSessions.size == 1 }
        val retryStatus = state.childSessions.first().status
        assertTrue(retryStatus is SessionStatus.Retry)
        assertEquals(3, (retryStatus as SessionStatus.Retry).attempt)
        collectJob.cancel()
    }

    @Test
    fun `childSessions updates when sessions are added`() = testScope.runTest {
        val child1 = TestFixtures.testSession(
            id = "child-add1",
            parentID = testSession.id,
        )

        val vm = createViewModel()
        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.childSessions.size)

        eventReducer.setSessions(testServer.id, listOf(testSession, child1))
        advanceUntilIdle()

        val state = vm.uiState.first { it.childSessions.isNotEmpty() }
        assertEquals(1, state.childSessions.size)
        assertEquals("child-add1", state.childSessions.first().session.id)

        val child2 = TestFixtures.testSession(
            id = "child-add2",
            parentID = testSession.id,
        )
        eventReducer.setSessions(testServer.id, listOf(testSession, child1, child2))
        advanceUntilIdle()

        val updated = vm.uiState.first { it.childSessions.size == 2 }
        assertEquals(2, updated.childSessions.size)
        collectJob.cancel()
    }
}
