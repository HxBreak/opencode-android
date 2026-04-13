package me.xiaok.opencode.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.CacheRepository
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ==========================================================================
// groupMessagesIntoTurns — tests via ChatViewModel internal function
// ==========================================================================

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupMessagesIntoTurnsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val testServer = TestFixtures.testServerConnection()
    private val testSession = TestFixtures.testSession()

    private lateinit var api: OpenCodeApi
    private lateinit var eventReducer: EventReducer
    private lateinit var serverRepository: ServerRepository
    private lateinit var cacheRepository: CacheRepository
    private lateinit var draftRepository: me.xiaok.opencode.data.repository.DraftRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var imageCompressor: ImageCompressor
    private lateinit var metadataCache: MetadataCache
    private lateinit var errorCollector: ErrorCollector
    private lateinit var testScope: TestScope
    private lateinit var sessionStatsUseCase: SessionStatsUseCase
    private lateinit var draftManagementUseCase: DraftManagementUseCase
    private lateinit var mentionManagementUseCase: MentionManagementUseCase
    private lateinit var permissionQuestionUseCase: PermissionQuestionUseCase
    private lateinit var sessionNavigationUseCase: SessionNavigationUseCase
    private lateinit var sessionOpsUseCase: SessionOpsUseCase
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var modelSelectionUseCase: ModelSelectionUseCase
    private lateinit var messageLoadingUseCase: MessageLoadingUseCase
    private lateinit var chatCommandUseCase: ChatCommandUseCase

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
        metadataCache = mockk(relaxed = true)
        errorCollector = mockk(relaxed = true)
        sessionStatsUseCase = SessionStatsUseCase()
        draftManagementUseCase = DraftManagementUseCase(draftRepository)
        mentionManagementUseCase = MentionManagementUseCase(api, eventReducer, serverRepository)
        permissionQuestionUseCase = PermissionQuestionUseCase(api, eventReducer, serverRepository, errorCollector)
        sessionNavigationUseCase = SessionNavigationUseCase(api, eventReducer, serverRepository)
        sessionOpsUseCase = SessionOpsUseCase(api, eventReducer, serverRepository, errorCollector)
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
        every { settingsRepository.initialMessages } returns flowOf(50)
        coEvery { settingsRepository.chatFontSize } returns flowOf("medium")
        coEvery { settingsRepository.getRecentAgent(any()) } returns flowOf(null)
        coEvery { settingsRepository.getRecentModel(any()) } returns flowOf(null)
        coEvery { api.getConfig(any()) } returns kotlinx.serialization.json.JsonObject(emptyMap())
        every { draftRepository.getDraft(any()) } returns MutableStateFlow(null)
    }

    private fun createViewModel(): ChatViewModel {
        val savedStateHandle = SavedStateHandle(mapOf(
            "serverId" to testServer.id,
            "sessionId" to testSession.id,
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

    // --- Helper factories ---

    private fun userMsg(id: String) = TestFixtures.testMessage(
        info = TestFixtures.testUserMessageInfo(id = id)
    )

    private fun assistantMsg(id: String, parentID: String) = TestFixtures.testMessage(
        info = TestFixtures.testMessageInfo(id = id, role = "assistant", parentID = parentID)
    )

    private fun assistantMsgOrphan(id: String) = TestFixtures.testMessage(
        info = TestFixtures.testMessageInfo(id = id, role = "assistant", parentID = null)
    )

    private fun compactionUserMsg(id: String) = TestFixtures.testMessage(
        info = TestFixtures.testMessageInfo(id = id, role = "user"),
        parts = listOf(TestFixtures.testCompactionPart(messageId = id))
    )

    // --- Tests ---

    @Test
    fun `empty message list returns empty turns`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val turns = vm.groupMessagesIntoTurns(emptyList())
        assertTrue(turns.isEmpty())
    }

    @Test
    fun `single user message produces 1 turn with 0 assistant messages`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val turns = vm.groupMessagesIntoTurns(listOf(userMsg("u1")))
        assertEquals(1, turns.size)
        assertEquals("u1", turns[0].userMessage.id)
        assertTrue(turns[0].assistantMessages.isEmpty())
    }

    @Test
    fun `user with 3 assistants produces 1 turn with 3 assistant messages`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val messages = listOf(
            userMsg("u1"),
            assistantMsg("a1", "u1"),
            assistantMsg("a2", "u1"),
            assistantMsg("a3", "u1"),
        )
        val turns = vm.groupMessagesIntoTurns(messages)

        assertEquals(1, turns.size)
        assertEquals("u1", turns[0].userMessage.id)
        assertEquals(3, turns[0].assistantMessages.size)
        assertEquals(listOf("a1", "a2", "a3"), turns[0].assistantMessages.map { it.id })
    }

    @Test
    fun `2 conversation rounds produce 2 turns`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val messages = listOf(
            userMsg("u1"),
            assistantMsg("a1", "u1"),
            userMsg("u2"),
            assistantMsg("a2", "u2"),
        )
        val turns = vm.groupMessagesIntoTurns(messages)

        assertEquals(2, turns.size)
        assertEquals("u1", turns[0].userMessage.id)
        assertEquals(1, turns[0].assistantMessages.size)
        assertEquals("u2", turns[1].userMessage.id)
        assertEquals(1, turns[1].assistantMessages.size)
    }

    @Test
    fun `compaction-only user message produces 1 turn with empty assistants`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val turns = vm.groupMessagesIntoTurns(listOf(compactionUserMsg("u_compact")))
        assertEquals(1, turns.size)
        assertEquals("u_compact", turns[0].userMessage.id)
        assertTrue(turns[0].assistantMessages.isEmpty())
    }

    @Test
    fun `orphan assistant with null parentID is silently dropped when no current turn exists`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val messages = listOf(
            assistantMsgOrphan("a_orphan"),
        )
        val turns = vm.groupMessagesIntoTurns(messages)

        // parentID=null matches currentTurn?.userMessage?.id (both null) but currentTurn is null,
        // so currentTurn?.copy() returns null and the assistant is silently dropped.
        assertTrue(turns.isEmpty())
    }

    @Test
    fun `orphan assistant with mismatched parentID is silently dropped`() = testScope.runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val messages = listOf(
            userMsg("u1"),
            assistantMsg("a1", "u1"),
            // This assistant's parentID doesn't match u1 — orphan, dropped
            assistantMsg("a_stray", "u_nonexistent"),
        )
        val turns = vm.groupMessagesIntoTurns(messages)

        // Only the first turn: u1 + a1. The stray assistant is dropped.
        assertEquals(1, turns.size)
        assertEquals("u1", turns[0].userMessage.id)
        assertEquals(1, turns[0].assistantMessages.size)
        assertEquals("a1", turns[0].assistantMessages[0].id)
    }
}

// ==========================================================================
// renderable — pure function tests (plain JUnit 4)
// ==========================================================================

class RenderableTest {

    @Test
    fun `Text with non-blank text is renderable`() {
        val part = TestFixtures.testTextPart(text = "Hello world")
        assertTrue(renderable(part))
    }

    @Test
    fun `Text with blank text is not renderable`() {
        val part = TestFixtures.testTextPart(text = "   ")
        assertFalse(renderable(part))
    }

    @Test
    fun `Text with empty text is not renderable`() {
        val part = TestFixtures.testTextPart(text = "")
        assertFalse(renderable(part))
    }

    @Test
    fun `Reasoning with non-blank text is renderable`() {
        val part = TestFixtures.testReasoningPart(text = "Let me think...")
        assertTrue(renderable(part))
    }

    @Test
    fun `Reasoning with blank text is not renderable`() {
        val part = TestFixtures.testReasoningPart(text = "  ")
        assertFalse(renderable(part))
    }

    @Test
    fun `Tool todowrite is not renderable (hidden)`() {
        val part = TestFixtures.testToolPart(
            tool = "todowrite",
            state = TestFixtures.testToolState(status = "completed"),
        )
        assertFalse(renderable(part))
    }

    @Test
    fun `Tool bash is renderable`() {
        val part = TestFixtures.testToolPart(
            tool = "bash",
            state = TestFixtures.testToolState(status = "completed"),
        )
        assertTrue(renderable(part))
    }

    @Test
    fun `Tool question with pending status is not renderable`() {
        val part = TestFixtures.testToolPart(
            tool = "question",
            state = TestFixtures.testToolState(status = "pending"),
        )
        assertFalse(renderable(part))
    }

    @Test
    fun `Tool question with running status is not renderable`() {
        val part = TestFixtures.testToolPart(
            tool = "question",
            state = TestFixtures.testToolState(status = "running"),
        )
        assertFalse(renderable(part))
    }

    @Test
    fun `Tool question with completed status is renderable`() {
        val part = TestFixtures.testToolPart(
            tool = "question",
            state = TestFixtures.testToolState(status = "completed"),
        )
        assertTrue(renderable(part))
    }

    @Test
    fun `Tool read with completed status is renderable`() {
        val part = TestFixtures.testToolPart(
            tool = "read",
            state = TestFixtures.testToolState(status = "completed"),
        )
        assertTrue(renderable(part))
    }

    @Test
    fun `Agent is not renderable`() {
        val part = TestFixtures.testAgentPart()
        assertFalse(renderable(part))
    }

    @Test
    fun `Retry is not renderable`() {
        val part = TestFixtures.testRetryPart()
        assertFalse(renderable(part))
    }

    @Test
    fun `Compaction is renderable`() {
        val part = TestFixtures.testCompactionPart()
        assertTrue(renderable(part))
    }

    @Test
    fun `StepStart is not renderable`() {
        val part = TestFixtures.testStepStartPart()
        assertFalse(renderable(part))
    }

    @Test
    fun `StepFinish is not renderable`() {
        val part = TestFixtures.testStepFinishPart()
        assertFalse(renderable(part))
    }

    @Test
    fun `Snapshot is not renderable`() {
        val part = TestFixtures.testSnapshotPart()
        assertFalse(renderable(part))
    }

    @Test
    fun `Patch is not renderable`() {
        val part = TestFixtures.testPatchPart()
        assertFalse(renderable(part))
    }

    @Test
    fun `File is renderable`() {
        val part = TestFixtures.testFilePart()
        assertTrue(renderable(part))
    }

    @Test
    fun `Subtask is renderable`() {
        val part = TestFixtures.testSubtaskPart()
        assertTrue(renderable(part))
    }
}

// ==========================================================================
// groupTurnParts — pure function tests (plain JUnit 4)
// ==========================================================================

class GroupTurnPartsTest {

    private fun readRef(msgId: String, partId: String) =
        PartRef(msgId, partId) to TestFixtures.testToolPart(
            id = partId,
            messageId = msgId,
            tool = "read",
            state = TestFixtures.testToolState(status = "completed"),
        )

    private fun bashRef(msgId: String, partId: String) =
        PartRef(msgId, partId) to TestFixtures.testToolPart(
            id = partId,
            messageId = msgId,
            tool = "bash",
            state = TestFixtures.testToolState(status = "completed"),
        )

    private fun grepRef(msgId: String, partId: String) =
        PartRef(msgId, partId) to TestFixtures.testToolPart(
            id = partId,
            messageId = msgId,
            tool = "grep",
            state = TestFixtures.testToolState(status = "completed"),
        )

    @Test
    fun `two consecutive reads across messages merge into ContextGroup`() {
        val parts = listOf(
            readRef("m1", "p1"),
            readRef("m1", "p2"),
        )
        val groups = groupTurnParts(parts)

        assertEquals(1, groups.size)
        assertTrue(groups[0] is TurnPartGroup.ContextGroup)
        val cg = groups[0] as TurnPartGroup.ContextGroup
        assertEquals(2, cg.refs.size)
        assertEquals(PartRef("m1", "p1"), cg.refs[0])
        assertEquals(PartRef("m1", "p2"), cg.refs[1])
    }

    @Test
    fun `read + bash + read produces three Singles (bash breaks context group)`() {
        val parts = listOf(
            readRef("m1", "p1"),
            bashRef("m1", "p2"),
            readRef("m1", "p3"),
        )
        val groups = groupTurnParts(parts)

        assertEquals(3, groups.size)
        assertTrue(groups[0] is TurnPartGroup.Single)
        assertEquals(PartRef("m1", "p1"), (groups[0] as TurnPartGroup.Single).ref)

        assertTrue(groups[1] is TurnPartGroup.Single)
        assertEquals(PartRef("m1", "p2"), (groups[1] as TurnPartGroup.Single).ref)

        assertTrue(groups[2] is TurnPartGroup.Single)
        assertEquals(PartRef("m1", "p3"), (groups[2] as TurnPartGroup.Single).ref)
    }

    @Test
    fun `single read produces Single (threshold less than 2)`() {
        val parts = listOf(readRef("m1", "p1"))
        val groups = groupTurnParts(parts)

        assertEquals(1, groups.size)
        assertTrue(groups[0] is TurnPartGroup.Single)
        assertEquals(PartRef("m1", "p1"), (groups[0] as TurnPartGroup.Single).ref)
    }

    @Test
    fun `three consecutive reads produce ContextGroup with 3 refs`() {
        val parts = listOf(
            readRef("m1", "p1"),
            readRef("m2", "p2"),
            readRef("m3", "p3"),
        )
        val groups = groupTurnParts(parts)

        assertEquals(1, groups.size)
        assertTrue(groups[0] is TurnPartGroup.ContextGroup)
        val cg = groups[0] as TurnPartGroup.ContextGroup
        assertEquals(3, cg.refs.size)
        assertEquals(PartRef("m1", "p1"), cg.refs[0])
        assertEquals(PartRef("m2", "p2"), cg.refs[1])
        assertEquals(PartRef("m3", "p3"), cg.refs[2])
    }

    @Test
    fun `read + grep consecutive produce ContextGroup`() {
        val parts = listOf(
            readRef("m1", "p1"),
            grepRef("m1", "p2"),
        )
        val groups = groupTurnParts(parts)

        assertEquals(1, groups.size)
        assertTrue(groups[0] is TurnPartGroup.ContextGroup)
        val cg = groups[0] as TurnPartGroup.ContextGroup
        assertEquals(2, cg.refs.size)
    }

    @Test
    fun `bash between context tools prevents grouping`() {
        val parts = listOf(
            readRef("m1", "p1"),
            readRef("m1", "p2"),
            bashRef("m1", "p3"),
            readRef("m1", "p4"),
            readRef("m1", "p5"),
        )
        val groups = groupTurnParts(parts)

        // Expected: ContextGroup(p1,p2), Single(p3), ContextGroup(p4,p5)
        assertEquals(3, groups.size)
        assertTrue(groups[0] is TurnPartGroup.ContextGroup)
        assertEquals(2, (groups[0] as TurnPartGroup.ContextGroup).refs.size)

        assertTrue(groups[1] is TurnPartGroup.Single)
        assertEquals(PartRef("m1", "p3"), (groups[1] as TurnPartGroup.Single).ref)

        assertTrue(groups[2] is TurnPartGroup.ContextGroup)
        assertEquals(2, (groups[2] as TurnPartGroup.ContextGroup).refs.size)
    }

    @Test
    fun `empty parts list produces empty groups`() {
        val groups = groupTurnParts(emptyList())
        assertTrue(groups.isEmpty())
    }
}
