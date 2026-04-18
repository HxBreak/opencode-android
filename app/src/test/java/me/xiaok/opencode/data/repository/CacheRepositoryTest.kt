package me.xiaok.opencode.data.repository

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.MessagesPage
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.local.db.dao.MessageDao
import me.xiaok.opencode.data.local.db.dao.SessionDao
import me.xiaok.opencode.data.local.db.dao.SessionViewLogDao
import me.xiaok.opencode.data.local.db.entity.MessageEntity
import me.xiaok.opencode.data.local.db.entity.SessionEntity
import me.xiaok.opencode.data.local.db.entity.SessionViewLog
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val sessionViewLogDao = mockk<SessionViewLogDao>(relaxed = true)

    private lateinit var repository: CacheRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0

        repository = CacheRepository(sessionDao, messageDao, sessionViewLogDao)
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    // ====================================================================
    // Session Cache
    // ====================================================================

    @Test
    fun `syncSessions calls sessionDao upsertAll`() = runTest {
        val sessions = listOf(TestFixtures.testSession())
        repository.syncSessions("server1", sessions)

        coVerify {
            sessionDao.upsertAll(match { entities ->
                entities.size == 1 &&
                    entities[0].id == "ses_test123" &&
                    entities[0].serverId == "server1"
            })
        }
    }

    @Test
    fun `syncSessions maps all sessions to entities`() = runTest {
        val sessions = listOf(
            TestFixtures.testSession(id = "ses_1"),
            TestFixtures.testSession(id = "ses_2"),
        )
        repository.syncSessions("server1", sessions)

        coVerify {
            sessionDao.upsertAll(match { it.size == 2 })
        }
    }

    @Test
    fun `upsertSession calls sessionDao upsert`() = runTest {
        val session = TestFixtures.testSession()
        repository.upsertSession("server1", session)

        coVerify {
            sessionDao.upsert(match { entity ->
                entity.id == "ses_test123" &&
                    entity.serverId == "server1" &&
                    entity.title == "Test Session"
            })
        }
    }

    @Test
    fun `deleteSession calls sessionDao delete`() = runTest {
        repository.deleteSession("ses_123")

        coVerify { sessionDao.delete("ses_123") }
    }

    @Test
    fun `deleteSessionsForServer calls sessionDao deleteForServer`() = runTest {
        repository.deleteSessionsForServer("server1")

        coVerify { sessionDao.deleteForServer("server1") }
    }

    // ====================================================================
    // Message Cache
    // ====================================================================

    @Test
    fun `syncMessages calls messageDao upsertAll`() = runTest {
        val messages = listOf(TestFixtures.testMessage())
        repository.syncMessages("server1", "ses_1", messages)

        coVerify {
            messageDao.upsertAll(match { entities ->
                entities.size == 1 &&
                    entities[0].serverId == "server1"
            })
        }
    }

    @Test
    fun `upsertMessage calls messageDao upsert`() = runTest {
        val message = TestFixtures.testMessage()
        repository.upsertMessage("server1", message)

        coVerify {
            messageDao.upsert(match { entity ->
                entity.serverId == "server1" &&
                    entity.role == "assistant"
            })
        }
    }

    @Test
    fun `deleteMessagesForSession calls messageDao deleteForSession`() = runTest {
        repository.deleteMessagesForSession("ses_123")

        coVerify { messageDao.deleteForSession("ses_123") }
    }

    @Test
    fun `deleteMessagesForServer calls messageDao deleteForServer`() = runTest {
        repository.deleteMessagesForServer("server1")

        coVerify { messageDao.deleteForServer("server1") }
    }

    // ====================================================================
    // Flow Delegation
    // ====================================================================

    @Test
    fun `getCachedSessions returns sessionDao flow`() = runTest {
        val entities = listOf(createSessionEntity())
        every { sessionDao.getSessionsForServer("server1") } returns flowOf(entities)

        val result = repository.getCachedSessions("server1").first()

        assertEquals(1, result.size)
        assertEquals("ses_test123", result[0].id)
    }

    @Test
    fun `getCachedMessages returns messageDao flow`() = runTest {
        val entities = listOf(createMessageEntity())
        every { messageDao.getMessagesForSession("ses_1") } returns flowOf(entities)

        val result = repository.getCachedMessages("ses_1").first()

        assertEquals(1, result.size)
        assertEquals("msg_test123", result[0].id)
    }

    // ====================================================================
    // Entity-to-Model Mapping
    // ====================================================================

    @Test
    fun `getCachedSessionsAsModels maps entities to domain models`() = runTest {
        val session = TestFixtures.testSession()
        val entity = createSessionEntityFromModel(session)
        every { sessionDao.getSessionsForServer("server1") } returns flowOf(listOf(entity))

        val result = repository.getCachedSessionsAsModels("server1")

        assertEquals(1, result.size)
        assertEquals("ses_test123", result[0].id)
        assertEquals("Test Session", result[0].title)
    }

    @Test
    fun `getCachedSessionsAsModels returns empty list on error`() = runTest {
        every { sessionDao.getSessionsForServer("server1") } returns flowOf(
            listOf(
                SessionEntity(
                    id = "bad",
                    serverId = "server1",
                    timeJson = "invalid json",
                )
            )
        )

        val result = repository.getCachedSessionsAsModels("server1")

        // mapNotNull filters out nulls from deserialization failures
        assertEquals(0, result.size)
    }

    @Test
    fun `getCachedMessagesAsModels maps entities to domain models`() = runTest {
        val message = TestFixtures.testMessage()
        val entity = createMessageEntityFromModel(message)
        every { messageDao.getMessagesForSession("ses_1") } returns flowOf(listOf(entity))

        val result = repository.getCachedMessagesAsModels("ses_1")

        assertEquals(1, result.size)
        assertEquals("msg_test123", result[0].id)
        assertEquals("assistant", result[0].role)
    }

    @Test
    fun `getCachedMessagesAsModels returns empty list on error`() = runTest {
        every { messageDao.getMessagesForSession("ses_1") } returns flowOf(
            listOf(
                MessageEntity(
                    id = "bad",
                    sessionId = "ses_1",
                    serverId = "server1",
                    messageJson = "invalid json",
                )
            )
        )

        val result = repository.getCachedMessagesAsModels("ses_1")

        assertEquals(0, result.size)
    }

    // ====================================================================
    // Stale-While-Revalidate
    // ====================================================================

    @Test
    fun `getSessionsWithRefresh returns cached data`() = runTest {
        val session = TestFixtures.testSession()
        val entity = createSessionEntityFromModel(session)
        every { sessionDao.getSessionsForServer("server1") } returns flowOf(listOf(entity))
        val api = mockk<OpenCodeApi>(relaxed = true)

        val result = repository.getSessionsWithRefresh(
            "server1",
            api,
            TestFixtures.testServerConnection(),
        )

        assertEquals(1, result.size)
        assertEquals("ses_test123", result[0].id)
    }

    @Test
    fun `getSessionsWithRefresh triggers background refresh`() = runTest {
        val session = TestFixtures.testSession()
        val entity = createSessionEntityFromModel(session)
        every { sessionDao.getSessionsForServer("server1") } returns flowOf(listOf(entity))
        val api = mockk<OpenCodeApi>()
        val server = TestFixtures.testServerConnection()
        coEvery { api.listSessions(server, roots = true) } returns listOf(session)

        repository.getSessionsWithRefresh("server1", api, server)

        // Background coroutine uses Dispatchers.IO, not test dispatcher
        Thread.sleep(200)

        coVerify { api.listSessions(server, roots = true) }
    }

    @Test
    fun `getMessagesWithRefresh returns cached data`() = runTest {
        val message = TestFixtures.testMessage()
        val entity = createMessageEntityFromModel(message)
        every { messageDao.getMessagesForSession("ses_1") } returns flowOf(listOf(entity))
        val api = mockk<OpenCodeApi>(relaxed = true)

        val result = repository.getMessagesWithRefresh(
            "server1",
            "ses_1",
            api,
            TestFixtures.testServerConnection(),
        )

        assertEquals(1, result.size)
        assertEquals("msg_test123", result[0].id)
    }

    @Test
    fun `getMessagesWithRefresh triggers background refresh`() = runTest {
        val message = TestFixtures.testMessage()
        val entity = createMessageEntityFromModel(message)
        every { messageDao.getMessagesForSession("ses_1") } returns flowOf(listOf(entity))
        val api = mockk<OpenCodeApi>()
        val server = TestFixtures.testServerConnection()
        coEvery { api.listMessages(server, "ses_1", limit = null) } returns MessagesPage(listOf(message), null)

        repository.getMessagesWithRefresh("server1", "ses_1", api, server)

        Thread.sleep(200)

        coVerify { api.listMessages(server, "ses_1", limit = null) }
    }

    @Test
    fun `getMessagesWithRefresh passes limit parameter`() = runTest {
        val message = TestFixtures.testMessage()
        val entity = createMessageEntityFromModel(message)
        every { messageDao.getMessagesForSession("ses_1") } returns flowOf(listOf(entity))
        val api = mockk<OpenCodeApi>()
        val server = TestFixtures.testServerConnection()
        coEvery { api.listMessages(server, "ses_1", limit = 50) } returns MessagesPage(listOf(message), null)

        repository.getMessagesWithRefresh("server1", "ses_1", api, server, limit = 50)

        Thread.sleep(200)

        coVerify { api.listMessages(server, "ses_1", limit = 50) }
    }

    // ====================================================================
    // SSE Cache Invalidation
    // ====================================================================

    @Test
    fun `onSseEvent SessionCreated upserts session`() = runTest {
        val session = TestFixtures.testSession()
        val event = SseEvent.SessionCreated(session = session)

        repository.onSseEvent("server1", event)

        Thread.sleep(200)

        coVerify {
            sessionDao.upsert(match { entity ->
                entity.id == "ses_test123" &&
                    entity.serverId == "server1"
            })
        }
    }

    @Test
    fun `onSseEvent SessionUpdated upserts session`() = runTest {
        val session = TestFixtures.testSession()
        val event = SseEvent.SessionUpdated(session = session)

        repository.onSseEvent("server1", event)

        Thread.sleep(200)

        coVerify {
            sessionDao.upsert(match { entity ->
                entity.id == "ses_test123" &&
                    entity.serverId == "server1"
            })
        }
    }

    @Test
    fun `onSseEvent SessionDeleted deletes session and messages`() = runTest {
        val session = TestFixtures.testSession(id = "ses_del")
        val event = SseEvent.SessionDeleted(session = session)

        repository.onSseEvent("server1", event)

        Thread.sleep(200)

        coVerify { sessionDao.delete("ses_del") }
        coVerify { messageDao.deleteForSession("ses_del") }
    }

    @Test
    fun `onSseEvent MessageUpdated upserts message`() = runTest {
        val message = TestFixtures.testMessage()
        val event = SseEvent.MessageUpdated(message = message)

        repository.onSseEvent("server1", event)

        Thread.sleep(200)

        coVerify {
            messageDao.upsert(match { entity ->
                entity.id == "msg_test123" &&
                    entity.serverId == "server1"
            })
        }
    }

    @Test
    fun `onSseEvent MessageRemoved deletes message`() = runTest {
        val event = SseEvent.MessageRemoved(
            sessionId = "ses_1",
            messageId = "msg_del",
        )

        repository.onSseEvent("server1", event)

        Thread.sleep(200)

        coVerify { messageDao.delete("msg_del") }
    }

    @Test
    fun `onSseEvent ServerInstanceDisposed deletes all server data`() = runTest {
        val event = SseEvent.ServerInstanceDisposed

        repository.onSseEvent("server1", event)

        Thread.sleep(200)

        coVerify { sessionDao.deleteForServer("server1") }
        coVerify { messageDao.deleteForServer("server1") }
    }

    // ====================================================================
    // Session View Log
    // ====================================================================

    @Test
    fun `markSessionViewed upserts view log`() = runTest {
        repository.markSessionViewed("server1", "ses_1")

        coVerify {
            sessionViewLogDao.upsert(match { log ->
                log.serverId == "server1" &&
                    log.sessionId == "ses_1" &&
                    log.lastViewedAt > 0L
            })
        }
    }

    @Test
    fun `getSessionViewLogs returns map of sessionId to lastViewedAt`() = runTest {
        val logs = listOf(
            SessionViewLog("server1", "ses_1", 1000L),
            SessionViewLog("server1", "ses_2", 2000L),
        )
        coEvery { sessionViewLogDao.getAllForServer("server1") } returns logs

        val result = repository.getSessionViewLogs("server1")

        assertEquals(mapOf("ses_1" to 1000L, "ses_2" to 2000L), result)
    }

    @Test
    fun `getSessionViewLogs returns empty map when no logs`() = runTest {
        coEvery { sessionViewLogDao.getAllForServer("server1") } returns emptyList()

        val result = repository.getSessionViewLogs("server1")

        assertEquals(emptyMap<String, Long>(), result)
    }

    @Test
    fun `deleteSessionViewLog calls dao delete`() = runTest {
        repository.deleteSessionViewLog("server1", "ses_1")

        coVerify { sessionViewLogDao.delete("server1", "ses_1") }
    }

    @Test
    fun `deleteSessionViewLogsForServer calls dao deleteForServer`() = runTest {
        repository.deleteSessionViewLogsForServer("server1")

        coVerify { sessionViewLogDao.deleteForServer("server1") }
    }

    // ====================================================================
    // Entity Mapping Edge Cases
    // ====================================================================

    @Test
    fun `syncSessions maps session with all optional fields`() = runTest {
        val session = TestFixtures.testSession(
            summary = TestFixtures.testSessionSummary(),
            share = TestFixtures.testSessionShare(),
            permission = listOf(TestFixtures.testPermissionRule()),
            revert = TestFixtures.testRevertInfo(),
            workspaceID = "ws_1",
            parentID = "parent_1",
        )
        repository.syncSessions("server1", listOf(session))

        coVerify {
            sessionDao.upsertAll(match { entities ->
                val e = entities[0]
                e.serverId == "server1" &&
                    e.workspaceID == "ws_1" &&
                    e.parentID == "parent_1" &&
                    e.summaryJson != null &&
                    e.shareJson != null &&
                    e.permissionJson != null &&
                    e.revertJson != null
            })
        }
    }

    @Test
    fun `syncSessions maps session with null optional fields`() = runTest {
        val session = TestFixtures.testSession(
            summary = null,
            share = null,
            permission = emptyList(),
            revert = null,
        )
        repository.syncSessions("server1", listOf(session))

        coVerify {
            sessionDao.upsertAll(match { entities ->
                val e = entities[0]
                e.summaryJson == null &&
                    e.shareJson == null &&
                    e.permissionJson == null &&
                    e.revertJson == null
            })
        }
    }

    @Test
    fun `getCachedSessionsAsModels maps session with optional fields`() = runTest {
        val session = TestFixtures.testSession(
            summary = TestFixtures.testSessionSummary(),
            share = TestFixtures.testSessionShare(),
            permission = listOf(TestFixtures.testPermissionRule()),
        )
        val entity = createSessionEntityFromModel(session)
        every { sessionDao.getSessionsForServer("server1") } returns flowOf(listOf(entity))

        val result = repository.getCachedSessionsAsModels("server1")

        assertEquals(1, result.size)
        assertNotNull(result[0].summary)
        assertNotNull(result[0].share)
        assertEquals(1, result[0].permission.size)
    }

    @Test
    fun `getCachedMessagesAsModels preserves message parts`() = runTest {
        val message = TestFixtures.testMessage(
            parts = listOf(
                TestFixtures.testTextPart(),
            )
        )
        val entity = createMessageEntityFromModel(message)
        every { messageDao.getMessagesForSession("ses_1") } returns flowOf(listOf(entity))

        val result = repository.getCachedMessagesAsModels("ses_1")

        assertEquals(1, result.size)
        assertEquals(1, result[0].parts.size)
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private fun createSessionEntity(
        id: String = "ses_test123",
        serverId: String = "server1",
    ): SessionEntity {
        val session = TestFixtures.testSession(id = id)
        return createSessionEntityFromModel(session, serverId)
    }

    private fun createSessionEntityFromModel(
        session: Session,
        serverId: String = "server1",
    ): SessionEntity {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        return SessionEntity(
            id = session.id,
            serverId = serverId,
            slug = session.slug,
            projectID = session.projectID,
            workspaceID = session.workspaceID,
            directory = session.directory,
            parentID = session.parentID,
            title = session.title,
            version = session.version,
            summaryJson = session.summary?.let { json.encodeToString(SessionSummary.serializer(), it) },
            shareJson = session.share?.let { json.encodeToString(SessionShare.serializer(), it) },
            permissionJson = if (session.permission.isNotEmpty()) json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(PermissionRule.serializer()),
                session.permission
            ) else null,
            revertJson = session.revert?.let { json.encodeToString(RevertInfo.serializer(), it) },
            timeJson = json.encodeToString(SessionTime.serializer(), session.time),
            updatedAt = session.time.updated,
        )
    }

    private fun createMessageEntity(
        id: String = "msg_test123",
        sessionId: String = "ses_test123",
        serverId: String = "server1",
    ): MessageEntity {
        val message = TestFixtures.testMessage()
        return createMessageEntityFromModel(message, serverId)
    }

    private fun createMessageEntityFromModel(
        message: Message,
        serverId: String = "server1",
    ): MessageEntity {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        return MessageEntity(
            id = message.id,
            sessionId = message.sessionId,
            serverId = serverId,
            role = message.role,
            messageJson = json.encodeToString(Message.serializer(), message),
            createdAt = message.time.created,
            updatedAt = message.time.updated,
        )
    }
}
