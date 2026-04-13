package me.xiaok.opencode.data.repository

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive unit tests for EventReducer covering all 24 SSE event types,
 * merge logic, unread tracking, bulk operations, and multi-server isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventReducerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val cacheRepository = mockk<CacheRepository>(relaxed = true)
    private lateinit var testScope: TestScope
    private lateinit var reducer: EventReducer

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        testScope = TestScope()
        reducer = EventReducer(cacheRepository, testScope)
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    // ====================================================================
    // Server Events (3)
    // ====================================================================

    @Test
    fun `process ServerConnected adds server to activeServers`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.ServerConnected)

        reducer.activeServers.test {
            assertEquals(setOf("server1"), awaitItem())
        }
    }

    @Test
    fun `process ServerConnected for multiple servers tracks all`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.ServerConnected)
        reducer.processEvent("server2", SseEvent.ServerConnected)

        reducer.activeServers.test {
            assertEquals(setOf("server1", "server2"), awaitItem())
        }
    }

    @Test
    fun `process ServerHeartbeat is a no-op on state`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.ServerHeartbeat)

        reducer.activeServers.test {
            assertEquals(emptySet<String>(), awaitItem())
        }
        reducer.sessions.test {
            assertEquals(emptyMap<String, Session>(), awaitItem())
        }
    }

    @Test
    fun `process ServerInstanceDisposed clears all state for that server`() = testScope.runTest {
        val session = TestFixtures.testSession()
        reducer.processEvent("server1", SseEvent.SessionCreated(session))

        assertTrue(reducer.sessions.value.containsKey(session.id))
        assertTrue(reducer.activeServers.value.contains("server1"))

        reducer.processEvent("server1", SseEvent.ServerInstanceDisposed)

        assertFalse(reducer.sessions.value.containsKey(session.id))
        assertFalse(reducer.activeServers.value.contains("server1"))
        assertNull(reducer.serverSessions.value["server1"])
    }

    // ====================================================================
    // Session Events (7)
    // ====================================================================

    @Test
    fun `process SessionCreated adds session to sessions map`() = testScope.runTest {
        val session = TestFixtures.testSession(id = "ses_abc", title = "New Session")

        reducer.processEvent("server1", SseEvent.SessionCreated(session))

        assertEquals(session, reducer.sessions.value["ses_abc"])
    }

    @Test
    fun `process SessionCreated registers session under serverSessions`() = testScope.runTest {
        val session = TestFixtures.testSession(id = "ses_abc")

        reducer.processEvent("server1", SseEvent.SessionCreated(session))

        assertEquals(setOf("ses_abc"), reducer.serverSessions.value["server1"])
    }

    @Test
    fun `process SessionCreated adds server to activeServers`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession()))

        assertTrue(reducer.activeServers.value.contains("server1"))
    }

    @Test
    fun `process SessionUpdated upserts session in sessions map`() = testScope.runTest {
        val session = TestFixtures.testSession(id = "ses_abc", title = "Original")
        reducer.processEvent("server1", SseEvent.SessionCreated(session))

        val updated = session.copy(title = "Updated Title")
        reducer.processEvent("server1", SseEvent.SessionUpdated(updated))

        assertEquals("Updated Title", reducer.sessions.value["ses_abc"]?.title)
    }

    @Test
    fun `process SessionUpdated works without prior SessionCreated`() = testScope.runTest {
        val session = TestFixtures.testSession(id = "ses_xyz")

        reducer.processEvent("server1", SseEvent.SessionUpdated(session))

        assertEquals(session, reducer.sessions.value["ses_xyz"])
    }

    @Test
    fun `process SessionDeleted removes session from all state`() = testScope.runTest {
        val session = TestFixtures.testSession(id = "ses_abc")
        reducer.processEvent("server1", SseEvent.SessionCreated(session))

        reducer.processEvent("server1", SseEvent.SessionDeleted(session))

        assertNull(reducer.sessions.value["ses_abc"])
        assertFalse(reducer.serverSessions.value["server1"]?.contains("ses_abc") ?: true)
        assertNull(reducer.messages.value["ses_abc"])
    }

    @Test
    fun `process SessionStatusChanged updates session status`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_abc")))

        reducer.processEvent("server1", SseEvent.SessionStatusChanged("ses_abc", SessionStatus.Busy))

        assertEquals(SessionStatus.Busy, reducer.sessionStatuses.value["ses_abc"])
    }

    @Test
    fun `process SessionIdle sets status to IDLE`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_abc")))
        reducer.processEvent("server1", SseEvent.SessionStatusChanged("ses_abc", SessionStatus.Busy))

        reducer.processEvent("server1", SseEvent.SessionIdle("ses_abc"))

        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value["ses_abc"])
    }

    @Test
    fun `process SessionDiff updates diffs for session`() = testScope.runTest {
        val diffs = listOf(TestFixtures.testFileDiff(path = "src/App.kt"), TestFixtures.testFileDiff(path = "src/Utils.kt"))

        reducer.processEvent("server1", SseEvent.SessionDiff("ses_abc", diffs))

        assertEquals(diffs, reducer.sessionDiffs.value["ses_abc"])
    }

    @Test
    fun `process SessionError stores error message`() = testScope.runTest {
        val error = TestFixtures.testErrorInfo(name = "APIError", data = TestFixtures.testErrorData(message = "Rate limit exceeded"))

        reducer.processEvent("server1", SseEvent.SessionError("ses_abc", error))

        assertEquals("Rate limit exceeded", reducer.sessionErrors.value["ses_abc"])
    }

    @Test
    fun `process SessionError with null sessionId does not store error`() = testScope.runTest {
        val error = TestFixtures.testErrorInfo()

        reducer.processEvent("server1", SseEvent.SessionError(null, error))

        assertTrue(reducer.sessionErrors.value.isEmpty())
    }

    @Test
    fun `clearSessionError removes error for session`() = testScope.runTest {
        val error = TestFixtures.testErrorInfo(name = "APIError", data = TestFixtures.testErrorData(message = "Boom"))
        reducer.processEvent("server1", SseEvent.SessionError("ses_abc", error))
        assertEquals("Boom", reducer.sessionErrors.value["ses_abc"])

        reducer.clearSessionError("ses_abc")

        assertNull(reducer.sessionErrors.value["ses_abc"])
    }

    @Test
    fun `clearSessionError does nothing if session has no error`() = testScope.runTest {
        val error = TestFixtures.testErrorInfo(name = "APIError", data = TestFixtures.testErrorData(message = "Boom"))
        reducer.processEvent("server1", SseEvent.SessionError("ses_abc", error))

        reducer.clearSessionError("ses_other")

        assertEquals("Boom", reducer.sessionErrors.value["ses_abc"])
        assertTrue(reducer.sessionErrors.value["ses_other"] == null)
    }

    // ====================================================================
    // Message Events (5)
    // ====================================================================

    @Test
    fun `process MessageUpdated appends new message`() = testScope.runTest {
        val session = TestFixtures.testSession(id = "ses_abc")
        val message = TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"))
        reducer.processEvent("server1", SseEvent.SessionCreated(session))

        reducer.processEvent("server1", SseEvent.MessageUpdated(message))

        val messages = reducer.messages.value["ses_abc"]
        assertNotNull(messages)
        assertEquals(1, messages!!.size)
        assertEquals("msg_1", messages[0].info.id)
    }

    @Test
    fun `process MessageUpdated updates existing message in place`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_abc")))

        val msg1 = TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"))
        reducer.processEvent("server1", SseEvent.MessageUpdated(msg1))

        val msg1Updated = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc", finish = "stop"),
            parts = listOf(TestFixtures.testTextPart())
        )
        reducer.processEvent("server1", SseEvent.MessageUpdated(msg1Updated))

        val messages = reducer.messages.value["ses_abc"]!!
        assertEquals(1, messages.size)
        assertEquals("stop", messages[0].info.finish)
    }

    @Test
    fun `process MessageUpdated merges parts from message into parts StateFlow`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_abc")))

        val textPart = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1")
        val message = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"),
            parts = listOf(textPart)
        )
        reducer.processEvent("server1", SseEvent.MessageUpdated(message))

        val parts = reducer.parts.value["msg_1"]
        assertNotNull(parts)
        assertEquals(1, parts!!.size)
        assertEquals("prt_1", parts[0].id)
    }

    @Test
    fun `process MessageRemoved removes message from list`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_abc")))
        val msg = TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"))
        reducer.processEvent("server1", SseEvent.MessageUpdated(msg))

        reducer.processEvent("server1", SseEvent.MessageRemoved("ses_abc", "msg_1"))

        assertTrue(reducer.messages.value["ses_abc"].isNullOrEmpty())
    }

    @Test
    fun `process MessageRemoved also removes parts for that message`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_abc")))
        val msg = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"),
            parts = listOf(TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1"))
        )
        reducer.processEvent("server1", SseEvent.MessageUpdated(msg))

        reducer.processEvent("server1", SseEvent.MessageRemoved("ses_abc", "msg_1"))

        assertNull(reducer.parts.value["msg_1"])
    }

    @Test
    fun `process MessagePartUpdated adds new part`() = testScope.runTest {
        val part = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1")

        reducer.processEvent("server1", SseEvent.MessagePartUpdated(part))

        val parts = reducer.parts.value["msg_1"]
        assertNotNull(parts)
        assertEquals(1, parts!!.size)
        assertEquals("prt_1", parts[0].id)
    }

    @Test
    fun `process MessagePartUpdated updates existing part in place`() = testScope.runTest {
        val part1 = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1", text = "Hello")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(part1))

        val part1Updated = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1", text = "Hello World")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(part1Updated))

        val parts = reducer.parts.value["msg_1"]!!
        assertEquals(1, parts.size)
        assertEquals("Hello World", (parts[0] as Part.Text).text)
    }

    @Test
    fun `process MessagePartDelta appends text to existing Text part`() = testScope.runTest {
        val part = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1", text = "Hello")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(part))

        reducer.processEvent("server1", SseEvent.MessagePartDelta(
            sessionId = "ses_abc",
            messageId = "msg_1",
            partId = "prt_1",
            field = "text",
            delta = " World"
        ))
        advanceUntilIdle()
        Thread.sleep(100)

        val parts = reducer.parts.value["msg_1"]!!
        assertEquals("Hello World", (parts[0] as Part.Text).text)
    }

    @Test
    fun `process MessagePartDelta creates stub Text part when parts not initialized`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.MessagePartDelta(
            sessionId = "ses_abc",
            messageId = "msg_1",
            partId = "prt_new",
            field = "text",
            delta = "Streaming text"
        ))

        val parts = reducer.parts.value["msg_1"]
        assertNotNull(parts)
        assertEquals(1, parts!!.size)
        assertEquals("Streaming text", (parts[0] as Part.Text).text)
        assertEquals("prt_new", parts[0].id)
    }

    @Test
    fun `process MessagePartDelta appends to Reasoning part`() = testScope.runTest {
        val part = TestFixtures.testReasoningPart(id = "prt_r1", messageId = "msg_1", text = "Step 1")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(part))

        reducer.processEvent("server1", SseEvent.MessagePartDelta(
            sessionId = "ses_abc",
            messageId = "msg_1",
            partId = "prt_r1",
            field = "text",
            delta = " Step 2"
        ))
        advanceUntilIdle()
        Thread.sleep(100)

        val parts = reducer.parts.value["msg_1"]!!
        assertEquals("Step 1 Step 2", (parts[0] as Part.Reasoning).text)
    }

    @Test
    fun `process MessagePartRemoved removes specific part`() = testScope.runTest {
        val part1 = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1")
        val part2 = TestFixtures.testTextPart(id = "prt_2", messageId = "msg_1")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(part1))
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(part2))

        reducer.processEvent("server1", SseEvent.MessagePartRemoved(
            sessionId = "ses_abc", messageId = "msg_1", partId = "prt_1"
        ))

        val parts = reducer.parts.value["msg_1"]!!
        assertEquals(1, parts.size)
        assertEquals("prt_2", parts[0].id)
    }

    // ====================================================================
    // Interaction Events (5)
    // ====================================================================

    @Test
    fun `process PermissionAsked adds permission request`() = testScope.runTest {
        val perm = TestFixtures.testPermissionRequest(id = "req_1", sessionID = "ses_abc")

        reducer.processEvent("server1", SseEvent.PermissionAsked(perm))

        val perms = reducer.permissions.value["ses_abc"]
        assertNotNull(perms)
        assertEquals(1, perms!!.size)
        assertEquals("req_1", perms[0].id)
    }

    @Test
    fun `process PermissionAsked accumulates multiple requests`() = testScope.runTest {
        val perm1 = TestFixtures.testPermissionRequest(id = "req_1", sessionID = "ses_abc")
        val perm2 = TestFixtures.testPermissionRequest(id = "req_2", sessionID = "ses_abc")

        reducer.processEvent("server1", SseEvent.PermissionAsked(perm1))
        reducer.processEvent("server1", SseEvent.PermissionAsked(perm2))

        assertEquals(2, reducer.permissions.value["ses_abc"]!!.size)
    }

    @Test
    fun `process PermissionReplied removes matching permission`() = testScope.runTest {
        val perm = TestFixtures.testPermissionRequest(id = "req_1", sessionID = "ses_abc")
        reducer.processEvent("server1", SseEvent.PermissionAsked(perm))

        reducer.processEvent("server1", SseEvent.PermissionReplied("ses_abc", "req_1"))

        assertTrue(reducer.permissions.value["ses_abc"].isNullOrEmpty())
    }

    @Test
    fun `process QuestionAsked adds question request`() = testScope.runTest {
        val question = TestFixtures.testQuestionRequest(id = "req_q1", sessionID = "ses_abc")

        reducer.processEvent("server1", SseEvent.QuestionAsked(question))

        val questions = reducer.questions.value["ses_abc"]
        assertNotNull(questions)
        assertEquals(1, questions!!.size)
        assertEquals("req_q1", questions[0].id)
    }

    @Test
    fun `process QuestionReplied removes matching question`() = testScope.runTest {
        val question = TestFixtures.testQuestionRequest(id = "req_q1", sessionID = "ses_abc")
        reducer.processEvent("server1", SseEvent.QuestionAsked(question))

        reducer.processEvent("server1", SseEvent.QuestionReplied("ses_abc", "req_q1"))

        assertTrue(reducer.questions.value["ses_abc"].isNullOrEmpty())
    }

    @Test
    fun `process QuestionRejected removes matching question`() = testScope.runTest {
        val question = TestFixtures.testQuestionRequest(id = "req_q1", sessionID = "ses_abc")
        reducer.processEvent("server1", SseEvent.QuestionAsked(question))

        reducer.processEvent("server1", SseEvent.QuestionRejected("ses_abc", "req_q1"))

        assertTrue(reducer.questions.value["ses_abc"].isNullOrEmpty())
    }

    // ====================================================================
    // Other Events (4)
    // ====================================================================

    @Test
    fun `process TodoUpdated stores todos for session`() = testScope.runTest {
        val todos = listOf(TestFixtures.testTodo(id = "todo_1"), TestFixtures.testTodo(id = "todo_2"))

        reducer.processEvent("server1", SseEvent.TodoUpdated("ses_abc", todos))

        assertEquals(todos, reducer.todos.value["ses_abc"])
    }

    @Test
    fun `process VcsBranchUpdated updates branch`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.VcsBranchUpdated("feature/test"))

        assertEquals("feature/test", reducer.vcsBranch.value)
    }

    @Test
    fun `process LspUpdated is a no-op on state`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.LspUpdated)

        assertTrue(reducer.sessions.value.isEmpty())
        assertTrue(reducer.messages.value.isEmpty())
    }

    @Test
    fun `process ProjectUpdated updates project info`() = testScope.runTest {
        val project = TestFixtures.testProject(id = "prj_1", name = "opencode-android")

        reducer.processEvent("server1", SseEvent.ProjectUpdated(project))

        assertEquals(project, reducer.projectInfo.value)
    }

    // ====================================================================
    // Bulk Operations
    // ====================================================================

    @Test
    fun `setSessions bulk loads sessions and registers server`() = testScope.runTest {
        val sessions = listOf(
            TestFixtures.testSession(id = "ses_1"),
            TestFixtures.testSession(id = "ses_2"),
        )

        reducer.setSessions("server1", sessions)

        assertEquals(2, reducer.sessions.value.size)
        assertEquals(setOf("ses_1", "ses_2"), reducer.serverSessions.value["server1"])
        assertTrue(reducer.activeServers.value.contains("server1"))
    }

    @Test
    fun `setMessages bulk sets messages for session`() = testScope.runTest {
        val messages = listOf(
            TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_1")),
            TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_2")),
        )

        reducer.setMessages("ses_abc", messages)

        assertEquals(2, reducer.messages.value["ses_abc"]!!.size)
    }

    @Test
    fun `setMessages merges with existing SSE messages`() = testScope.runTest {
        val sseMsg = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_sse", sessionID = "ses_abc",
                time = TestFixtures.testMessageTime(created = 100))
        )
        reducer.processEvent("server1", SseEvent.MessageUpdated(sseMsg))

        val restMsgs = listOf(
            TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_rest", sessionID = "ses_abc",
                time = TestFixtures.testMessageTime(created = 50))),
        )
        reducer.setMessages("ses_abc", restMsgs)

        val messages = reducer.messages.value["ses_abc"]!!
        assertEquals(2, messages.size)
        assertEquals("msg_rest", messages[0].info.id)
        assertEquals("msg_sse", messages[1].info.id)
    }

    @Test
    fun `prependMessages adds older messages to the front`() = testScope.runTest {
        val msg2 = TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_2", sessionID = "ses_abc"))
        reducer.setMessages("ses_abc", listOf(msg2))

        val msg1 = TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"))
        reducer.prependMessages("ses_abc", listOf(msg1))

        val messages = reducer.messages.value["ses_abc"]!!
        assertEquals(2, messages.size)
        assertEquals("msg_1", messages[0].info.id)
        assertEquals("msg_2", messages[1].info.id)
    }

    @Test
    fun `prependMessages deduplicates existing messages`() = testScope.runTest {
        val msg1 = TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"))
        reducer.setMessages("ses_abc", listOf(msg1))

        val msg0 = TestFixtures.testMessage(info = TestFixtures.testMessageInfo(id = "msg_0", sessionID = "ses_abc"))
        reducer.prependMessages("ses_abc", listOf(msg0, msg1))

        val messages = reducer.messages.value["ses_abc"]!!
        assertEquals(2, messages.size)
    }

    @Test
    fun `setParts bulk sets parts for message`() = testScope.runTest {
        val parts = listOf(TestFixtures.testTextPart(id = "prt_1"), TestFixtures.testToolPart(id = "prt_2"))

        reducer.setParts("msg_1", parts)

        assertEquals(2, reducer.parts.value["msg_1"]!!.size)
    }

    // ====================================================================
    // Merge Logic
    // ====================================================================

    @Test
    fun `mergeParts preserves longer streaming Text content`() = testScope.runTest {
        val streamingPart = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1", text = "Hello World Foo")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(streamingPart))

        val restMsg = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"),
            parts = listOf(TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1", text = "Hello World"))
        )
        reducer.processEvent("server1", SseEvent.MessageUpdated(restMsg))

        val parts = reducer.parts.value["msg_1"]!!
        assertEquals("Hello World Foo", (parts[0] as Part.Text).text)
    }

    @Test
    fun `mergeParts preserves longer streaming Reasoning content`() = testScope.runTest {
        val streamingPart = TestFixtures.testReasoningPart(id = "prt_r1", messageId = "msg_1", text = "Step 1 Step 2")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(streamingPart))

        val restMsg = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"),
            parts = listOf(TestFixtures.testReasoningPart(id = "prt_r1", messageId = "msg_1", text = "Step 1"))
        )
        reducer.processEvent("server1", SseEvent.MessageUpdated(restMsg))

        val parts = reducer.parts.value["msg_1"]!!
        assertEquals("Step 1 Step 2", (parts[0] as Part.Reasoning).text)
    }

    @Test
    fun `mergeParts prefers incoming for non-streaming parts`() = testScope.runTest {
        val existingPart = TestFixtures.testToolPart(id = "prt_t1", messageId = "msg_1", tool = "bash")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(existingPart))

        val updatedPart = TestFixtures.testToolPart(
            id = "prt_t1", messageId = "msg_1", tool = "bash",
            state = TestFixtures.testToolState(status = "completed")
        )
        val restMsg = TestFixtures.testMessage(
            info = TestFixtures.testMessageInfo(id = "msg_1", sessionID = "ses_abc"),
            parts = listOf(updatedPart)
        )
        reducer.processEvent("server1", SseEvent.MessageUpdated(restMsg))

        val parts = reducer.parts.value["msg_1"]!!
        val toolPart = parts[0] as Part.Tool
        assertEquals("completed", toolPart.state.status)
    }

    // ====================================================================
    // Cleanup
    // ====================================================================

    @Test
    fun `clearForServer removes only that servers state`() = testScope.runTest {
        val session1 = TestFixtures.testSession(id = "ses_s1")
        val session2 = TestFixtures.testSession(id = "ses_s2")
        reducer.processEvent("server1", SseEvent.SessionCreated(session1))
        reducer.processEvent("server2", SseEvent.SessionCreated(session2))

        reducer.clearForServer("server1")

        assertFalse(reducer.sessions.value.containsKey("ses_s1"))
        assertFalse(reducer.activeServers.value.contains("server1"))
        assertTrue(reducer.sessions.value.containsKey("ses_s2"))
        assertTrue(reducer.activeServers.value.contains("server2"))
    }

    @Test
    fun `clearAll resets everything`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.ServerConnected)
        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession()))
        reducer.processEvent("server1", SseEvent.VcsBranchUpdated("main"))
        reducer.processEvent("server1", SseEvent.ProjectUpdated(TestFixtures.testProject()))
        reducer.processEvent("server1", SseEvent.TodoUpdated("ses_test123", listOf(TestFixtures.testTodo())))

        reducer.clearAll()

        assertTrue(reducer.activeServers.value.isEmpty())
        assertTrue(reducer.sessions.value.isEmpty())
        assertTrue(reducer.messages.value.isEmpty())
        assertTrue(reducer.parts.value.isEmpty())
        assertNull(reducer.vcsBranch.value)
        assertNull(reducer.projectInfo.value)
        assertTrue(reducer.todos.value.isEmpty())
        assertTrue(reducer.unreadSessions.value.isEmpty())
        assertTrue(reducer.sessionErrors.value.isEmpty())
        assertTrue(reducer.sessionDiffs.value.isEmpty())
        assertTrue(reducer.permissions.value.isEmpty())
        assertTrue(reducer.questions.value.isEmpty())
        assertTrue(reducer.serverSessions.value.isEmpty())
        assertTrue(reducer.sessionStatuses.value.isEmpty())
    }

    // ====================================================================
    // Unread Tracking
    // ====================================================================

    @Test
    fun `markSessionViewed removes session from unread set`() = testScope.runTest {
        coEvery { cacheRepository.getSessionViewLogs("server1") } returns emptyMap()

        val session = TestFixtures.testSession(id = "ses_abc")
        reducer.setSessions("server1", listOf(session))
        testScope.testScheduler.advanceUntilIdle()

        // Session should be unread initially
        assertTrue(reducer.unreadSessions.value["server1"]?.contains("ses_abc") == true)

        reducer.markSessionViewed("server1", "ses_abc")
        testScope.testScheduler.advanceUntilIdle()

        // Should be cleared from unread
        assertFalse(reducer.unreadSessions.value["server1"]?.contains("ses_abc") == true)
    }

    @Test
    fun `computeUnreadSessions marks sessions as unread when never viewed`() = testScope.runTest {
        coEvery { cacheRepository.getSessionViewLogs("server1") } returns emptyMap()

        val session = TestFixtures.testSession(id = "ses_abc")
        reducer.setSessions("server1", listOf(session))
        testScope.testScheduler.advanceUntilIdle()

        val unread = reducer.unreadSessions.value["server1"]
        assertNotNull(unread)
        assertTrue(unread!!.contains("ses_abc"))
    }

    @Test
    fun `computeUnreadSessions marks session unread when updated after last view`() = testScope.runTest {
        coEvery { cacheRepository.getSessionViewLogs("server1") } returns mapOf("ses_abc" to 100L)

        val session = TestFixtures.testSession(id = "ses_abc", time = TestFixtures.testSessionTime(updated = 200L))
        reducer.setSessions("server1", listOf(session))
        testScope.testScheduler.advanceUntilIdle()

        val unread = reducer.unreadSessions.value["server1"]
        assertNotNull(unread)
        assertTrue(unread!!.contains("ses_abc"))
    }

    @Test
    fun `computeUnreadSessions skips session when viewed after update`() = testScope.runTest {
        coEvery { cacheRepository.getSessionViewLogs("server1") } returns mapOf("ses_abc" to 300L)

        val session = TestFixtures.testSession(id = "ses_abc", time = TestFixtures.testSessionTime(updated = 200L))
        reducer.setSessions("server1", listOf(session))
        testScope.testScheduler.advanceUntilIdle()

        val unread = reducer.unreadSessions.value["server1"]
        assertTrue(unread == null || !unread.contains("ses_abc"))
    }

    // ====================================================================
    // Multi-Server Isolation
    // ====================================================================

    @Test
    fun `sessions from different servers are isolated`() = testScope.runTest {
        val session1 = TestFixtures.testSession(id = "ses_s1", title = "Server 1 Session")
        val session2 = TestFixtures.testSession(id = "ses_s2", title = "Server 2 Session")

        reducer.processEvent("server1", SseEvent.SessionCreated(session1))
        reducer.processEvent("server2", SseEvent.SessionCreated(session2))

        assertEquals("Server 1 Session", reducer.sessions.value["ses_s1"]?.title)
        assertEquals("Server 2 Session", reducer.sessions.value["ses_s2"]?.title)
        assertEquals(setOf("ses_s1"), reducer.serverSessions.value["server1"])
        assertEquals(setOf("ses_s2"), reducer.serverSessions.value["server2"])
    }

    @Test
    fun `clearing one server does not affect another servers permissions`() = testScope.runTest {
        val perm1 = TestFixtures.testPermissionRequest(id = "req_1", sessionID = "ses_s1")
        val perm2 = TestFixtures.testPermissionRequest(id = "req_2", sessionID = "ses_s2")

        reducer.processEvent("server1", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_s1")))
        reducer.processEvent("server2", SseEvent.SessionCreated(TestFixtures.testSession(id = "ses_s2")))
        reducer.processEvent("server1", SseEvent.PermissionAsked(perm1))
        reducer.processEvent("server2", SseEvent.PermissionAsked(perm2))

        reducer.clearForServer("server1")

        assertTrue(reducer.permissions.value["ses_s1"].isNullOrEmpty())
        assertEquals(1, reducer.permissions.value["ses_s2"]!!.size)
    }

    // ====================================================================
    // Optimistic Updates
    // ====================================================================

    @Test
    fun `updateSessionStatus directly updates status`() = testScope.runTest {
        reducer.updateSessionStatus("ses_abc", SessionStatus.Retry())

        assertEquals(SessionStatus.Retry(), reducer.sessionStatuses.value["ses_abc"])
    }

    @Test
    fun `removeQuestion directly removes question by requestId`() = testScope.runTest {
        val q1 = TestFixtures.testQuestionRequest(id = "req_q1", sessionID = "ses_abc")
        val q2 = TestFixtures.testQuestionRequest(id = "req_q2", sessionID = "ses_abc")
        reducer.processEvent("server1", SseEvent.QuestionAsked(q1))
        reducer.processEvent("server1", SseEvent.QuestionAsked(q2))

        reducer.removeQuestion("ses_abc", "req_q1")

        val remaining = reducer.questions.value["ses_abc"]!!
        assertEquals(1, remaining.size)
        assertEquals("req_q2", remaining[0].id)
    }

    @Test
    fun `removePermission directly removes permission by requestId`() = testScope.runTest {
        val p1 = TestFixtures.testPermissionRequest(id = "req_p1", sessionID = "ses_abc")
        val p2 = TestFixtures.testPermissionRequest(id = "req_p2", sessionID = "ses_abc")
        reducer.processEvent("server1", SseEvent.PermissionAsked(p1))
        reducer.processEvent("server1", SseEvent.PermissionAsked(p2))

        reducer.removePermission("ses_abc", "req_p1")

        val remaining = reducer.permissions.value["ses_abc"]!!
        assertEquals(1, remaining.size)
        assertEquals("req_p2", remaining[0].id)
    }

    // ====================================================================
    // Edge Cases
    // ====================================================================

    @Test
    fun `multiple SessionDiff events replace rather than accumulate`() = testScope.runTest {
        val diffs1 = listOf(TestFixtures.testFileDiff(path = "file1.kt"))
        val diffs2 = listOf(TestFixtures.testFileDiff(path = "file2.kt"), TestFixtures.testFileDiff(path = "file3.kt"))

        reducer.processEvent("server1", SseEvent.SessionDiff("ses_abc", diffs1))
        reducer.processEvent("server1", SseEvent.SessionDiff("ses_abc", diffs2))

        val result = reducer.sessionDiffs.value["ses_abc"]!!
        assertEquals(2, result.size)
        assertEquals("file2.kt", result[0].path)
        assertEquals("file3.kt", result[1].path)
    }

    @Test
    fun `VcsBranchUpdated overwrites previous branch`() = testScope.runTest {
        reducer.processEvent("server1", SseEvent.VcsBranchUpdated("main"))
        reducer.processEvent("server1", SseEvent.VcsBranchUpdated("feature/new"))

        assertEquals("feature/new", reducer.vcsBranch.value)
    }

    @Test
    fun `TodoUpdated replaces todos for same session`() = testScope.runTest {
        val todos1 = listOf(TestFixtures.testTodo(id = "t1", status = "pending"))
        val todos2 = listOf(TestFixtures.testTodo(id = "t1", status = "completed"), TestFixtures.testTodo(id = "t2", status = "pending"))

        reducer.processEvent("server1", SseEvent.TodoUpdated("ses_abc", todos1))
        reducer.processEvent("server1", SseEvent.TodoUpdated("ses_abc", todos2))

        val result = reducer.todos.value["ses_abc"]!!
        assertEquals(2, result.size)
        assertEquals("completed", result[0].status)
    }

    @Test
    fun `ProjectUpdated overwrites previous project`() = testScope.runTest {
        val project1 = TestFixtures.testProject(id = "prj_1", name = "Old Project")
        val project2 = TestFixtures.testProject(id = "prj_2", name = "New Project")

        reducer.processEvent("server1", SseEvent.ProjectUpdated(project1))
        reducer.processEvent("server1", SseEvent.ProjectUpdated(project2))

        assertEquals("New Project", reducer.projectInfo.value?.name)
    }

    @Test
    fun `setParts merges with existing SSE parts`() = testScope.runTest {
        // SSE delivers a streaming text part
        val ssePart = TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1", text = "Streamed")
        reducer.processEvent("server1", SseEvent.MessagePartUpdated(ssePart))

        // REST arrives with the same part but shorter text
        val restParts = listOf(TestFixtures.testTextPart(id = "prt_1", messageId = "msg_1", text = "Short"))
        reducer.setParts("msg_1", restParts)

        // Streaming content (longer) should be preserved
        val parts = reducer.parts.value["msg_1"]!!
        assertEquals("Streamed", (parts[0] as Part.Text).text)
    }
}
