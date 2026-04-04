package me.xiaok.opencode.data.local.db.dao

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.local.db.AppDatabase
import me.xiaok.opencode.data.local.db.entity.SessionEntity
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
class SessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // --- Test fixtures ---

    private fun session(
        id: String = "ses_001",
        serverId: String = "server_A",
        title: String = "Test Session",
        updatedAt: Long = 1000L,
    ) = SessionEntity(
        id = id,
        serverId = serverId,
        slug = "slug-$id",
        projectID = "proj-1",
        directory = "/tmp",
        title = title,
        updatedAt = updatedAt,
    )

    // --- getSessionsForServer ---

    @Test
    fun `getSessionsForServer returns sessions for given server ordered by updatedAt desc`() = runTest {
        val s1 = session(id = "s1", serverId = "srv1", updatedAt = 1000L)
        val s2 = session(id = "s2", serverId = "srv1", updatedAt = 3000L)
        val s3 = session(id = "s3", serverId = "srv1", updatedAt = 2000L)
        val s4 = session(id = "s4", serverId = "srv2", updatedAt = 5000L)

        dao.upsertAll(listOf(s1, s2, s3, s4))

        val items = dao.getSessionsForServer("srv1").first()
        assertEquals(3, items.size)
        // Ordered by updatedAt DESC
        assertEquals("s2", items[0].id)
        assertEquals("s3", items[1].id)
        assertEquals("s1", items[2].id)
    }

    @Test
    fun `getSessionsForServer returns empty list when no sessions exist`() = runTest {
        val items = dao.getSessionsForServer("nonexistent").first()
        assertTrue(items.isEmpty())
    }

    // --- getSession ---

    @Test
    fun `getSession returns session by id`() = runTest {
        val s = session(id = "ses_123")
        dao.upsert(s)

        val result = dao.getSession("ses_123")
        assertNotNull(result)
        assertEquals("ses_123", result!!.id)
    }

    @Test
    fun `getSession returns null for non-existent id`() = runTest {
        val result = dao.getSession("nonexistent")
        assertNull(result)
    }

    // --- upsertAll ---

    @Test
    fun `upsertAll inserts multiple sessions`() = runTest {
        val sessions = listOf(
            session(id = "s1", serverId = "srv1"),
            session(id = "s2", serverId = "srv1"),
        )
        dao.upsertAll(sessions)

        val items = dao.getSessionsForServer("srv1").first()
        assertEquals(2, items.size)
    }

    @Test
    fun `upsertAll replaces existing sessions on conflict`() = runTest {
        dao.upsert(session(id = "s1", serverId = "srv1", title = "Original"))
        dao.upsertAll(listOf(session(id = "s1", serverId = "srv1", title = "Updated")))

        val result = dao.getSession("s1")
        assertEquals("Updated", result!!.title)
    }

    // --- upsert ---

    @Test
    fun `upsert inserts a new session`() = runTest {
        val s = session(id = "new_ses", serverId = "srv1", title = "New Session")
        dao.upsert(s)

        val result = dao.getSession("new_ses")
        assertNotNull(result)
        assertEquals("New Session", result!!.title)
    }

    @Test
    fun `upsert updates an existing session`() = runTest {
        dao.upsert(session(id = "s1", serverId = "srv1", title = "Old"))
        dao.upsert(session(id = "s1", serverId = "srv1", title = "New"))

        val result = dao.getSession("s1")
        assertEquals("New", result!!.title)
    }

    // --- delete ---

    @Test
    fun `delete removes session by id`() = runTest {
        val s = session(id = "to_delete", serverId = "srv1")
        dao.upsert(s)
        assertNotNull(dao.getSession("to_delete"))

        dao.delete("to_delete")
        assertNull(dao.getSession("to_delete"))
    }

    @Test
    fun `delete is no-op for non-existent id`() = runTest {
        // Should not throw
        dao.delete("nonexistent")
    }

    // --- deleteForServer ---

    @Test
    fun `deleteForServer removes all sessions for given server`() = runTest {
        dao.upsertAll(listOf(
            session(id = "s1", serverId = "srv1"),
            session(id = "s2", serverId = "srv1"),
            session(id = "s3", serverId = "srv2"),
        ))

        dao.deleteForServer("srv1")

        val srv1Items = dao.getSessionsForServer("srv1").first()
        assertTrue(srv1Items.isEmpty())

        val srv2Items = dao.getSessionsForServer("srv2").first()
        assertEquals(1, srv2Items.size)
    }

    // --- Flow reactivity ---

    @Test
    fun `getSessionsForServer emits updated list after upsert`() = runTest {
        val initial = dao.getSessionsForServer("srv1").first()
        assertTrue(initial.isEmpty())

        dao.upsert(session(id = "s1", serverId = "srv1"))

        val updated = dao.getSessionsForServer("srv1").first()
        assertEquals(1, updated.size)
        assertEquals("s1", updated[0].id)
    }
}
