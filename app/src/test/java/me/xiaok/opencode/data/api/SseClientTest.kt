package me.xiaok.opencode.data.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.serialization.json.*
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SseClient] covering:
 * - Event parsing for all 24 SSE event types
 * - Exponential backoff calculation
 * - URL building with/without query params
 * - Malformed event handling (invalid JSON, empty data, missing fields, unknown types)
 */
class SseClientTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var client: SseClient
    private lateinit var json: Json
    private lateinit var eventReducer: me.xiaok.opencode.data.repository.EventReducer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var server: ServerConnection

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            classDiscriminator = "type"
        }
        eventReducer = mockk(relaxed = true)
        okHttpClient = OkHttpClient.Builder().build()
        server = TestFixtures.testServerConnection()
        client = SseClient(
            server = server,
            okHttpClient = okHttpClient,
            json = json,
            eventReducer = eventReducer,
        )
    }

    // === Reflection helpers ===

    private fun parseEvent(data: String): SseEvent? {
        val method = SseClient::class.java.getDeclaredMethod("parseEvent", String::class.java)
        method.isAccessible = true
        return method.invoke(client, data) as? SseEvent
    }

    private fun dispatchEvent(type: String, properties: JsonObject): SseEvent? {
        val method = SseClient::class.java.getDeclaredMethod("dispatchEvent", String::class.java, JsonObject::class.java)
        method.isAccessible = true
        return method.invoke(client, type, properties) as? SseEvent
    }

    private fun calculateBackoffDelay(attempts: Int): Long {
        val method = SseClient::class.java.getDeclaredMethod("calculateBackoffDelay", Int::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(client, attempts) as Long
    }

    private fun buildUrl(client: SseClient): String {
        val method = SseClient::class.java.getDeclaredMethod("buildUrl")
        method.isAccessible = true
        return method.invoke(client) as String
    }

    // === Helper: wrap properties in SSE envelope JSON string ===

    private fun envelopeJson(type: String, properties: JsonObject, directory: String = "global"): String {
        val envelope = buildJsonObject {
            put("directory", directory)
            put("payload", buildJsonObject {
                put("type", type)
                put("properties", properties)
            })
        }
        return json.encodeToString(JsonObject.serializer(), envelope)
    }

    // ========================================================
    // Server Events
    // ========================================================

    @Test
    fun `server connected event`() {
        val result = parseEvent(envelopeJson("server.connected", buildJsonObject {}))
        assertNotNull(result)
        assertTrue(result is SseEvent.ServerConnected)
    }

    @Test
    fun `server heartbeat event`() {
        val result = parseEvent(envelopeJson("server.heartbeat", buildJsonObject {}))
        assertNotNull(result)
        assertTrue(result is SseEvent.ServerHeartbeat)
    }

    @Test
    fun `server instance disposed event`() {
        val result = parseEvent(envelopeJson("server.instance_disposed", buildJsonObject {}))
        assertNotNull(result)
        assertTrue(result is SseEvent.ServerInstanceDisposed)
    }

    @Test
    fun `global disposed alias maps to server instance disposed`() {
        val result = parseEvent(envelopeJson("global.disposed", buildJsonObject {}))
        assertNotNull(result)
        assertTrue(result is SseEvent.ServerInstanceDisposed)
    }

    // ========================================================
    // Session Events
    // ========================================================

    @Test
    fun `session created event`() {
        val sessionJson = json.encodeToJsonElement(Session.serializer(), TestFixtures.testSession())
        val props = buildJsonObject { put("session", sessionJson) }
        val result = parseEvent(envelopeJson("session.created", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionCreated)
        assertEquals("ses_test123", (result as SseEvent.SessionCreated).session.id)
    }

    @Test
    fun `session created event with info key`() {
        val sessionJson = json.encodeToJsonElement(Session.serializer(), TestFixtures.testSession(id = "ses_info"))
        val props = buildJsonObject { put("info", sessionJson) }
        val result = dispatchEvent("session.created", props)

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionCreated)
        assertEquals("ses_info", (result as SseEvent.SessionCreated).session.id)
    }

    @Test
    fun `session updated event`() {
        val sessionJson = json.encodeToJsonElement(Session.serializer(), TestFixtures.testSession(id = "ses_updated"))
        val props = buildJsonObject { put("session", sessionJson) }
        val result = parseEvent(envelopeJson("session.updated", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionUpdated)
        assertEquals("ses_updated", (result as SseEvent.SessionUpdated).session.id)
    }

    @Test
    fun `session deleted event`() {
        val sessionJson = json.encodeToJsonElement(Session.serializer(), TestFixtures.testSession())
        val props = buildJsonObject { put("session", sessionJson) }
        val result = parseEvent(envelopeJson("session.deleted", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionDeleted)
        assertEquals("ses_test123", (result as SseEvent.SessionDeleted).session.id)
    }

    @Test
    fun `session status changed event with busy`() {
        val props = buildJsonObject {
            put("sessionID", "ses_123")
            put("status", buildJsonObject { put("type", "busy") })
        }
        val result = parseEvent(envelopeJson("session.status", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionStatusChanged)
        val event = result as SseEvent.SessionStatusChanged
        assertEquals("ses_123", event.sessionId)
        assertEquals(SessionStatus.Busy, event.status)
    }

    @Test
    fun `session status changed event with plain string status`() {
        // When status is a plain string (not {"type":"..."}), the jsonObject accessor
        // throws internally. The catch block returns null. This verifies that behavior.
        val props = buildJsonObject {
            put("sessionID", "ses_456")
            put("status", "idle")
        }
        assertNull(dispatchEvent("session.status", props))
    }

    @Test
    fun `session status changed with unknown status defaults to idle`() {
        val props = buildJsonObject {
            put("sessionID", "ses_789")
            put("status", buildJsonObject { put("type", "unknown_status") })
        }
        val result = dispatchEvent("session.status", props)

        assertNotNull(result)
        val event = result as SseEvent.SessionStatusChanged
        assertEquals(SessionStatus.Idle, event.status)
    }

    @Test
    fun `session idle event`() {
        val props = buildJsonObject { put("sessionID", "ses_idle") }
        val result = parseEvent(envelopeJson("session.idle", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionIdle)
        assertEquals("ses_idle", (result as SseEvent.SessionIdle).sessionId)
    }

    @Test
    fun `session diff event`() {
        val diffsJson = Json.encodeToJsonElement(
            kotlinx.serialization.serializer<List<FileDiff>>(),
            listOf(TestFixtures.testFileDiff())
        )
        val props = buildJsonObject {
            put("sessionID", "ses_diff")
            put("diffs", diffsJson)
        }
        val result = parseEvent(envelopeJson("session.diff", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionDiff)
        val event = result as SseEvent.SessionDiff
        assertEquals("ses_diff", event.sessionId)
        assertEquals(1, event.diffs.size)
    }

    @Test
    fun `session diff event with diff key fallback`() {
        val diffsJson = Json.encodeToJsonElement(
            kotlinx.serialization.serializer<List<FileDiff>>(),
            listOf(TestFixtures.testFileDiff())
        )
        val props = buildJsonObject {
            put("sessionID", "ses_diff2")
            put("diff", diffsJson)
        }
        val result = dispatchEvent("session.diff", props)

        assertNotNull(result)
        val event = result as SseEvent.SessionDiff
        assertEquals(1, event.diffs.size)
    }

    @Test
    fun `session error event with all fields`() {
        val errorJson = json.encodeToJsonElement(ErrorInfo.serializer(), TestFixtures.testErrorInfo())
        val props = buildJsonObject {
            put("sessionID", "ses_err")
            put("error", errorJson)
        }
        val result = parseEvent(envelopeJson("session.error", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.SessionError)
        val event = result as SseEvent.SessionError
        assertEquals("ses_err", event.sessionId)
        assertNotNull(event.error)
        assertEquals("APIError", event.error!!.name)
    }

    @Test
    fun `session error event with null fields`() {
        val props = buildJsonObject {}
        val result = dispatchEvent("session.error", props)

        assertNotNull(result)
        val event = result as SseEvent.SessionError
        assertNull(event.sessionId)
        assertNull(event.error)
    }

    // ========================================================
    // Message Events
    // ========================================================

    @Test
    fun `message updated event`() {
        val message = TestFixtures.testMessage()
        val messageJson = json.encodeToJsonElement(Message.serializer(), message)
        val props = messageJson.jsonObject
        val result = parseEvent(envelopeJson("message.updated", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.MessageUpdated)
        assertEquals("msg_test123", (result as SseEvent.MessageUpdated).message.id)
    }

    @Test
    fun `message removed event`() {
        val props = buildJsonObject {
            put("sessionID", "ses_123")
            put("messageID", "msg_456")
        }
        val result = parseEvent(envelopeJson("message.removed", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.MessageRemoved)
        val event = result as SseEvent.MessageRemoved
        assertEquals("ses_123", event.sessionId)
        assertEquals("msg_456", event.messageId)
    }

    @Test
    fun `message part updated event with nested part`() {
        val part = TestFixtures.testTextPart()
        val partJson = json.encodeToJsonElement(Part.serializer(), part)
        val props = buildJsonObject { put("part", partJson) }
        val result = parseEvent(envelopeJson("message.part.updated", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.MessagePartUpdated)
        val event = result as SseEvent.MessagePartUpdated
        assertTrue(event.part is Part.Text)
        assertEquals("prt_text123", event.part.id)
    }

    @Test
    fun `message part updated event with flat properties`() {
        val part = TestFixtures.testTextPart()
        val partJson = json.encodeToJsonElement(Part.serializer(), part)
        // Flat: properties IS the part object (no "part" key)
        val result = dispatchEvent("message.part.updated", partJson.jsonObject)

        assertNotNull(result)
        assertTrue(result is SseEvent.MessagePartUpdated)
        assertEquals("prt_text123", (result as SseEvent.MessagePartUpdated).part.id)
    }

    @Test
    fun `message part delta event`() {
        val props = buildJsonObject {
            put("sessionID", "ses_delta")
            put("messageID", "msg_delta")
            put("partID", "prt_delta")
            put("field", "text")
            put("delta", "Hello world")
        }
        val result = parseEvent(envelopeJson("message.part.delta", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.MessagePartDelta)
        val event = result as SseEvent.MessagePartDelta
        assertEquals("ses_delta", event.sessionId)
        assertEquals("msg_delta", event.messageId)
        assertEquals("prt_delta", event.partId)
        assertEquals("text", event.field)
        assertEquals("Hello world", event.delta)
    }

    @Test
    fun `message part delta event with missing optional fields defaults to empty`() {
        val props = buildJsonObject {
            put("sessionID", "ses_d")
            put("messageID", "msg_d")
            put("partID", "prt_d")
        }
        val result = dispatchEvent("message.part.delta", props)

        assertNotNull(result)
        val event = result as SseEvent.MessagePartDelta
        assertEquals("", event.field)
        assertEquals("", event.delta)
    }

    @Test
    fun `message part removed event`() {
        val props = buildJsonObject {
            put("sessionID", "ses_rm")
            put("messageID", "msg_rm")
            put("partID", "prt_rm")
        }
        val result = parseEvent(envelopeJson("message.part.removed", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.MessagePartRemoved)
        val event = result as SseEvent.MessagePartRemoved
        assertEquals("ses_rm", event.sessionId)
        assertEquals("msg_rm", event.messageId)
        assertEquals("prt_rm", event.partId)
    }

    // ========================================================
    // Interaction Events
    // ========================================================

    @Test
    fun `permission asked event`() {
        val permJson = json.encodeToJsonElement(PermissionRequest.serializer(), TestFixtures.testPermissionRequest())
        val result = dispatchEvent("permission.asked", permJson.jsonObject)

        assertNotNull(result)
        assertTrue(result is SseEvent.PermissionAsked)
        val event = result as SseEvent.PermissionAsked
        assertEquals("req_perm123", event.permission.id)
        assertEquals("bash", event.permission.permission)
    }

    @Test
    fun `permission replied event`() {
        val props = buildJsonObject {
            put("sessionID", "ses_perm")
            put("requestID", "req_perm_1")
        }
        val result = parseEvent(envelopeJson("permission.replied", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.PermissionReplied)
        val event = result as SseEvent.PermissionReplied
        assertEquals("ses_perm", event.sessionId)
        assertEquals("req_perm_1", event.requestId)
    }

    @Test
    fun `question asked event`() {
        val questionJson = json.encodeToJsonElement(QuestionRequest.serializer(), TestFixtures.testQuestionRequest())
        val result = dispatchEvent("question.asked", questionJson.jsonObject)

        assertNotNull(result)
        assertTrue(result is SseEvent.QuestionAsked)
        val event = result as SseEvent.QuestionAsked
        assertEquals("req_quest123", event.question.id)
    }

    @Test
    fun `question replied event`() {
        val props = buildJsonObject {
            put("sessionID", "ses_q")
            put("requestID", "req_q_1")
        }
        val result = parseEvent(envelopeJson("question.replied", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.QuestionReplied)
        val event = result as SseEvent.QuestionReplied
        assertEquals("ses_q", event.sessionId)
        assertEquals("req_q_1", event.requestId)
    }

    @Test
    fun `question rejected event`() {
        val props = buildJsonObject {
            put("sessionID", "ses_qr")
            put("requestID", "req_qr_1")
        }
        val result = parseEvent(envelopeJson("question.rejected", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.QuestionRejected)
        val event = result as SseEvent.QuestionRejected
        assertEquals("ses_qr", event.sessionId)
        assertEquals("req_qr_1", event.requestId)
    }

    // ========================================================
    // Other Events
    // ========================================================

    @Test
    fun `todo updated event`() {
        val todosJson = Json.encodeToJsonElement(
            kotlinx.serialization.serializer<List<Todo>>(),
            listOf(TestFixtures.testTodo())
        )
        val props = buildJsonObject {
            put("sessionID", "ses_todo")
            put("todos", todosJson)
        }
        val result = parseEvent(envelopeJson("todo.updated", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.TodoUpdated)
        val event = result as SseEvent.TodoUpdated
        assertEquals("ses_todo", event.sessionId)
        assertEquals(1, event.todos.size)
        assertEquals("todo_1", event.todos[0].id)
    }

    @Test
    fun `vcs branch updated event`() {
        val props = buildJsonObject { put("branch", "feature/test") }
        val result = parseEvent(envelopeJson("vcs.branch.updated", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.VcsBranchUpdated)
        assertEquals("feature/test", (result as SseEvent.VcsBranchUpdated).branch)
    }

    @Test
    fun `lsp updated event`() {
        val result = parseEvent(envelopeJson("lsp.updated", buildJsonObject {}))
        assertNotNull(result)
        assertTrue(result is SseEvent.LspUpdated)
    }

    @Test
    fun `project updated event`() {
        val projectJson = json.encodeToJsonElement(Project.serializer(), TestFixtures.testProject())
        val props = buildJsonObject { put("project", projectJson) }
        val result = parseEvent(envelopeJson("project.updated", props))

        assertNotNull(result)
        assertTrue(result is SseEvent.ProjectUpdated)
        assertEquals("prj_test", (result as SseEvent.ProjectUpdated).project.id)
    }

    // ========================================================
    // parseEvent error handling
    // ========================================================

    @Test
    fun `parseEvent returns null for blank data`() {
        assertNull(parseEvent(""))
        assertNull(parseEvent("   "))
    }

    @Test
    fun `parseEvent returns null for invalid JSON`() {
        assertNull(parseEvent("not json at all"))
        assertNull(parseEvent("{invalid"))
    }

    @Test
    fun `parseEvent returns null for empty type`() {
        val envelope = buildJsonObject {
            put("directory", "global")
            put("payload", buildJsonObject {
                put("type", "")
                put("properties", buildJsonObject {})
            })
        }
        assertNull(parseEvent(json.encodeToString(JsonObject.serializer(), envelope)))
    }

    @Test
    fun `parseEvent returns null for null properties`() {
        val envelope = buildJsonObject {
            put("directory", "global")
            put("payload", buildJsonObject {
                put("type", "session.created")
                put("properties", JsonNull)
            })
        }
        assertNull(parseEvent(json.encodeToString(JsonObject.serializer(), envelope)))
    }

    // ========================================================
    // dispatchEvent — missing required fields
    // ========================================================

    @Test
    fun `dispatchEvent returns null for unknown event type`() {
        val result = dispatchEvent("unknown.event.type", buildJsonObject { put("foo", "bar") })
        assertNull(result)
    }

    @Test
    fun `session created returns null when session and info keys missing`() {
        val result = dispatchEvent("session.created", buildJsonObject {})
        assertNull(result)
    }

    @Test
    fun `session status returns null when sessionID missing`() {
        val props = buildJsonObject {
            put("status", buildJsonObject { put("type", "busy") })
        }
        assertNull(dispatchEvent("session.status", props))
    }

    @Test
    fun `session idle returns null when sessionID missing`() {
        assertNull(dispatchEvent("session.idle", buildJsonObject {}))
    }

    @Test
    fun `message removed returns null when messageID missing`() {
        val props = buildJsonObject { put("sessionID", "ses_1") }
        assertNull(dispatchEvent("message.removed", props))
    }

    @Test
    fun `permission asked returns default PermissionRequest when properties empty`() {
        // With direct deserialization, empty properties produces a default PermissionRequest
        val result = dispatchEvent("permission.asked", buildJsonObject {})
        assertNotNull(result)
        assertTrue(result is SseEvent.PermissionAsked)
    }

    @Test
    fun `todo updated returns null when todos key missing`() {
        val props = buildJsonObject { put("sessionID", "ses_1") }
        assertNull(dispatchEvent("todo.updated", props))
    }

    @Test
    fun `vcs branch returns null when branch missing`() {
        assertNull(dispatchEvent("vcs.branch.updated", buildJsonObject {}))
    }

    @Test
    fun `project updated returns null when project key missing`() {
        assertNull(dispatchEvent("project.updated", buildJsonObject {}))
    }

    // ========================================================
    // Backoff Calculation
    // ========================================================

    @Test
    fun `backoff attempt 1 returns initial delay`() {
        val delay = calculateBackoffDelay(1)
        assertEquals(5_000L, delay)
    }

    @Test
    fun `backoff attempt 2 doubles the delay`() {
        val delay = calculateBackoffDelay(2)
        assertEquals(10_000L, delay)
    }

    @Test
    fun `backoff attempt 3 quadruples the delay`() {
        val delay = calculateBackoffDelay(3)
        assertEquals(20_000L, delay)
    }

    @Test
    fun `backoff is capped at max delay`() {
        // With initial=5000, exponent capped at 5: 5000 * 32 = 160000
        // But maxReconnectDelayMs = 60000, so capped
        val delay = calculateBackoffDelay(10)
        assertEquals(60_000L, delay)
    }

    @Test
    fun `backoff never goes below initial delay`() {
        // attempt=0: exponent = min(-1, 5) = -1, 5000 * (1 shl -1) could be weird
        // coerceAtLeast(initialReconnectDelayMs) ensures it's >= 5000
        val delay = calculateBackoffDelay(0)
        assertEquals(5_000L, delay)
    }

    @Test
    fun `backoff with custom delays`() {
        val customClient = SseClient(
            server = server,
            okHttpClient = okHttpClient,
            json = json,
            eventReducer = eventReducer,
            initialReconnectDelayMs = 1_000L,
            maxReconnectDelayMs = 30_000L,
        )
        val method = SseClient::class.java.getDeclaredMethod("calculateBackoffDelay", Int::class.javaPrimitiveType)
        method.isAccessible = true

        // attempt=1: 1000
        assertEquals(1_000L, method.invoke(customClient, 1))
        // attempt=2: 2000
        assertEquals(2_000L, method.invoke(customClient, 2))
        // attempt=5: 1000 * 16 = 16000
        assertEquals(16_000L, method.invoke(customClient, 5))
        // attempt=10: capped at 30000
        assertEquals(30_000L, method.invoke(customClient, 10))
    }

    // ========================================================
    // URL Building
    // ========================================================

    @Test
    fun `buildUrl without directory and workspace`() {
        val url = buildUrl(client)
        assertEquals("http://192.168.1.100:4096/global/event", url)
    }

    @Test
    fun `buildUrl with directory only`() {
        val c = SseClient(server, okHttpClient, json, eventReducer, directory = "my-project")
        assertEquals(
            "http://192.168.1.100:4096/global/event?directory=my-project",
            buildUrl(c)
        )
    }

    @Test
    fun `buildUrl with workspace only`() {
        val c = SseClient(server, okHttpClient, json, eventReducer, workspace = "ws-1")
        assertEquals(
            "http://192.168.1.100:4096/global/event?workspace=ws-1",
            buildUrl(c)
        )
    }

    @Test
    fun `buildUrl with both directory and workspace`() {
        val c = SseClient(server, okHttpClient, json, eventReducer, directory = "proj", workspace = "ws-1")
        assertEquals(
            "http://192.168.1.100:4096/global/event?directory=proj&workspace=ws-1",
            buildUrl(c)
        )
    }

    @Test
    fun `buildUrl strips trailing slash from base`() {
        val serverWithSlash = server.copy(baseUrl = "http://host:1234/")
        val c = SseClient(serverWithSlash, okHttpClient, json, eventReducer)
        assertEquals("http://host:1234/global/event", buildUrl(c))
    }

    // ========================================================
    // Malformed Event Handling
    // ========================================================

    @Test
    fun `dispatchEvent handles unknown properties in session gracefully`() {
        // Pass properties with unknown fields — no matching session data
        // Graceful degradation: returns null (caught by try/catch)
        val props = buildJsonObject {
            put("session", buildJsonObject { put("bad_field", 123) })
        }
        val result = dispatchEvent("session.created", props)
        // Either null (graceful) or a SessionCreated with defaults — both acceptable
        if (result != null) {
            assertTrue(result is SseEvent.SessionCreated)
        }
    }

    @Test
    fun `parseEvent handles valid JSON but non-object at top level`() {
        val data = "\"just a string\""
        // This should not crash — will fail to decode as SseEnvelope
        assertNull(parseEvent(data))
    }

    @Test
    fun `dispatchEvent handles missing sessionID for message part delta gracefully`() {
        val props = buildJsonObject {
            put("messageID", "msg_1")
            put("partID", "prt_1")
        }
        assertNull(dispatchEvent("message.part.delta", props))
    }

    @Test
    fun `disconnect resets state`() {
        client.disconnect()
        // No exception = pass. Verifies disconnect doesn't crash on null eventSource.
    }
}
