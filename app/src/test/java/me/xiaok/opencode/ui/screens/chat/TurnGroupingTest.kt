package me.xiaok.opencode.ui.screens.chat

import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.ui.screens.chat.PartRef
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ==========================================================================
// groupMessagesIntoTurns — tests for top-level function in TurnGroupingUtils
// ==========================================================================

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupMessagesIntoTurnsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
    }

    private val testScope get() = coroutineRule.testScope

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
        val turns = groupMessagesIntoTurns(emptyList())
        assertTrue(turns.isEmpty())
    }

    @Test
    fun `single user message produces 1 turn with 0 assistant messages`() = testScope.runTest {
        val turns = groupMessagesIntoTurns(listOf(userMsg("u1")))
        assertEquals(1, turns.size)
        assertEquals("u1", turns[0].userMessage.id)
        assertTrue(turns[0].assistantMessages.isEmpty())
    }

    @Test
    fun `user with 3 assistants produces 1 turn with 3 assistant messages`() = testScope.runTest {
        val messages = listOf(
            userMsg("u1"),
            assistantMsg("a1", "u1"),
            assistantMsg("a2", "u1"),
            assistantMsg("a3", "u1"),
        )
        val turns = groupMessagesIntoTurns(messages)

        assertEquals(1, turns.size)
        assertEquals("u1", turns[0].userMessage.id)
        assertEquals(3, turns[0].assistantMessages.size)
        assertEquals(listOf("a1", "a2", "a3"), turns[0].assistantMessages.map { it.id })
    }

    @Test
    fun `2 conversation rounds produce 2 turns`() = testScope.runTest {
        val messages = listOf(
            userMsg("u1"),
            assistantMsg("a1", "u1"),
            userMsg("u2"),
            assistantMsg("a2", "u2"),
        )
        val turns = groupMessagesIntoTurns(messages)

        assertEquals(2, turns.size)
        assertEquals("u1", turns[0].userMessage.id)
        assertEquals(1, turns[0].assistantMessages.size)
        assertEquals("u2", turns[1].userMessage.id)
        assertEquals(1, turns[1].assistantMessages.size)
    }

    @Test
    fun `compaction-only user message produces 1 turn with empty assistants`() = testScope.runTest {
        val turns = groupMessagesIntoTurns(listOf(compactionUserMsg("u_compact")))
        assertEquals(1, turns.size)
        assertEquals("u_compact", turns[0].userMessage.id)
        assertTrue(turns[0].assistantMessages.isEmpty())
    }

    @Test
    fun `orphan assistant with null parentID is silently dropped when no current turn exists`() = testScope.runTest {
        val messages = listOf(
            assistantMsgOrphan("a_orphan"),
        )
        val turns = groupMessagesIntoTurns(messages)

        // parentID=null matches currentTurn?.userMessage?.id (both null) but currentTurn is null,
        // so currentTurn?.copy() returns null and the assistant is silently dropped.
        assertTrue(turns.isEmpty())
    }

    @Test
    fun `orphan assistant with mismatched parentID is silently dropped`() = testScope.runTest {
        val messages = listOf(
            userMsg("u1"),
            assistantMsg("a1", "u1"),
            // This assistant's parentID doesn't match u1 — orphan, dropped
            assistantMsg("a_stray", "u_nonexistent"),
        )
        val turns = groupMessagesIntoTurns(messages)

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

// ==========================================================================
// extractTurnCopyText — pure function tests (plain JUnit 4)
// ==========================================================================

class ExtractTurnCopyTextTest {

    @Test
    fun `turn with no text parts returns empty string`() {
        val turn = ChatTurn(
            userMessage = TestFixtures.testMessage(
                info = TestFixtures.testUserMessageInfo(id = "u1"),
            ),
            userParts = emptyList(),
            partLookup = emptyMap(),
        )
        assertEquals("", extractTurnCopyText(turn))
    }

    @Test
    fun `turn with only user text returns user text`() {
        val turn = ChatTurn(
            userMessage = TestFixtures.testMessage(
                info = TestFixtures.testUserMessageInfo(id = "u1"),
            ),
            userParts = listOf(
                TestFixtures.testTextPart(id = "p1", messageId = "u1", text = "Hello"),
            ),
            partLookup = emptyMap(),
        )
        assertEquals("Hello", extractTurnCopyText(turn))
    }

    @Test
    fun `turn with only assistant text returns assistant text`() {
        val turn = ChatTurn(
            userMessage = TestFixtures.testMessage(
                info = TestFixtures.testUserMessageInfo(id = "u1"),
            ),
            userParts = emptyList(),
            partLookup = mapOf(
                PartRef("a1", "p1") to TestFixtures.testTextPart(
                    id = "p1", messageId = "a1", text = "Hi there",
                ),
            ),
        )
        assertEquals("Hi there", extractTurnCopyText(turn))
    }

    @Test
    fun `turn with both user and assistant text joins with double newline`() {
        val turn = ChatTurn(
            userMessage = TestFixtures.testMessage(
                info = TestFixtures.testUserMessageInfo(id = "u1"),
            ),
            userParts = listOf(
                TestFixtures.testTextPart(id = "p1", messageId = "u1", text = "Hello"),
            ),
            partLookup = mapOf(
                PartRef("a1", "p2") to TestFixtures.testTextPart(
                    id = "p2", messageId = "a1", text = "Hi there",
                ),
            ),
        )
        assertEquals("Hello\n\nHi there", extractTurnCopyText(turn))
    }

    @Test
    fun `multiple user and assistant text parts are joined`() {
        val turn = ChatTurn(
            userMessage = TestFixtures.testMessage(
                info = TestFixtures.testUserMessageInfo(id = "u1"),
            ),
            userParts = listOf(
                TestFixtures.testTextPart(id = "p1", messageId = "u1", text = "Line 1"),
                TestFixtures.testTextPart(id = "p2", messageId = "u1", text = "Line 2"),
            ),
            partLookup = mapOf(
                PartRef("a1", "p3") to TestFixtures.testTextPart(
                    id = "p3", messageId = "a1", text = "Reply 1",
                ),
                PartRef("a1", "p4") to TestFixtures.testTextPart(
                    id = "p4", messageId = "a1", text = "Reply 2",
                ),
            ),
        )
        assertEquals("Line 1\nLine 2\n\nReply 1\nReply 2", extractTurnCopyText(turn))
    }

    @Test
    fun `non-text parts in userParts are ignored`() {
        val turn = ChatTurn(
            userMessage = TestFixtures.testMessage(
                info = TestFixtures.testUserMessageInfo(id = "u1"),
            ),
            userParts = listOf(
                TestFixtures.testFilePart(),
                TestFixtures.testTextPart(id = "p1", messageId = "u1", text = "User text"),
            ),
            partLookup = emptyMap(),
        )
        assertEquals("User text", extractTurnCopyText(turn))
    }
}
