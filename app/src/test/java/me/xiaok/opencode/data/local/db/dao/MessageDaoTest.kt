package me.xiaok.opencode.data.local.db.dao

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.local.db.AppDatabase
import me.xiaok.opencode.data.local.db.entity.MessageEntity
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MessageDaoTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.messageDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // --- Test fixtures ---

    private fun message(
        id: String = "msg_001",
        sessionId: String = "ses_001",
        serverId: String = "server_A",
        role: String = "user",
        createdAt: Long = 1000L,
    ) = MessageEntity(
        id = id,
        sessionId = sessionId,
        serverId = serverId,
        role = role,
        messageJson = """{"id":"$id"}""",
        createdAt = createdAt,
    )

    // --- getMessagesForSession ---

    @Test
    fun `getMessagesForSession returns messages ordered by createdAt ASC`() = runTest {
        val m1 = message(id = "m1", sessionId = "ses1", createdAt = 3000L)
        val m2 = message(id = "m2", sessionId = "ses1", createdAt = 1000L)
        val m3 = message(id = "m3", sessionId = "ses1", createdAt = 2000L)
        val m4 = message(id = "m4", sessionId = "ses2", createdAt = 5000L)

        dao.upsertAll(listOf(m1, m2, m3, m4))

        val items = dao.getMessagesForSession("ses1").first()
        assertEquals(3, items.size)
        // Ordered by createdAt ASC
        assertEquals("m2", items[0].id)
        assertEquals("m3", items[1].id)
        assertEquals("m1", items[2].id)
    }

    @Test
    fun `getMessagesForSession returns empty list for non-existent session`() = runTest {
        val items = dao.getMessagesForSession("nonexistent").first()
        assertTrue(items.isEmpty())
    }

    // --- getMessagesForSessionPaged ---

    @Test
    fun `getMessagesForSessionPaged returns limited messages`() = runTest {
        val messages = (1..5).map { i ->
            message(id = "m$i", sessionId = "ses1", createdAt = i * 1000L)
        }
        dao.upsertAll(messages)

        val result = dao.getMessagesForSessionPaged("ses1", limit = 3)
        assertEquals(3, result.size)
        // Ordered by createdAt ASC
        assertEquals("m1", result[0].id)
        assertEquals("m2", result[1].id)
        assertEquals("m3", result[2].id)
    }

    // --- upsertAll ---

    @Test
    fun `upsertAll inserts multiple messages`() = runTest {
        val messages = listOf(
            message(id = "m1", sessionId = "ses1"),
            message(id = "m2", sessionId = "ses1"),
        )
        dao.upsertAll(messages)

        val items = dao.getMessagesForSession("ses1").first()
        assertEquals(2, items.size)
    }

    @Test
    fun `upsertAll replaces existing messages on conflict`() = runTest {
        dao.upsert(message(id = "m1", sessionId = "ses1", role = "user"))
        dao.upsertAll(listOf(message(id = "m1", sessionId = "ses1", role = "assistant")))

        val items = dao.getMessagesForSession("ses1").first()
        assertEquals(1, items.size)
        assertEquals("assistant", items[0].role)
    }

    // --- upsert ---

    @Test
    fun `upsert inserts a new message`() = runTest {
        dao.upsert(message(id = "new_msg", sessionId = "ses1", role = "assistant"))

        val items = dao.getMessagesForSession("ses1").first()
        assertEquals(1, items.size)
        assertEquals("new_msg", items[0].id)
    }

    @Test
    fun `upsert updates an existing message`() = runTest {
        dao.upsert(message(id = "m1", sessionId = "ses1", role = "user"))
        dao.upsert(message(id = "m1", sessionId = "ses1", role = "assistant"))

        val items = dao.getMessagesForSession("ses1").first()
        assertEquals(1, items.size)
        assertEquals("assistant", items[0].role)
    }

    // --- delete ---

    @Test
    fun `delete removes message by id`() = runTest {
        dao.upsert(message(id = "to_delete", sessionId = "ses1"))

        dao.delete("to_delete")

        val items = dao.getMessagesForSession("ses1").first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun `delete is no-op for non-existent id`() = runTest {
        // Should not throw
        dao.delete("nonexistent")
    }

    // --- deleteForSession ---

    @Test
    fun `deleteForSession removes all messages for given session`() = runTest {
        dao.upsertAll(listOf(
            message(id = "m1", sessionId = "ses1", serverId = "srv1"),
            message(id = "m2", sessionId = "ses1", serverId = "srv1"),
            message(id = "m3", sessionId = "ses2", serverId = "srv1"),
        ))

        dao.deleteForSession("ses1")

        val ses1Items = dao.getMessagesForSession("ses1").first()
        assertTrue(ses1Items.isEmpty())

        val ses2Items = dao.getMessagesForSession("ses2").first()
        assertEquals(1, ses2Items.size)
    }

    // --- deleteForServer ---

    @Test
    fun `deleteForServer removes all messages for given server`() = runTest {
        dao.upsertAll(listOf(
            message(id = "m1", sessionId = "ses1", serverId = "srv1"),
            message(id = "m2", sessionId = "ses2", serverId = "srv1"),
            message(id = "m3", sessionId = "ses3", serverId = "srv2"),
        ))

        dao.deleteForServer("srv1")

        // ses3 (srv2) should remain
        val ses3Items = dao.getMessagesForSession("ses3").first()
        assertEquals(1, ses3Items.size)

        // ses1 and ses2 (srv1) should be empty
        val ses1Items = dao.getMessagesForSession("ses1").first()
        assertTrue(ses1Items.isEmpty())

        val ses2Items = dao.getMessagesForSession("ses2").first()
        assertTrue(ses2Items.isEmpty())
    }

    // --- Flow reactivity ---

    @Test
    fun `getMessagesForSession emits updated list after upsert`() = runTest {
        val initial = dao.getMessagesForSession("ses1").first()
        assertTrue(initial.isEmpty())

        dao.upsert(message(id = "m1", sessionId = "ses1"))

        val updated = dao.getMessagesForSession("ses1").first()
        assertEquals(1, updated.size)
        assertEquals("m1", updated[0].id)
    }
}
