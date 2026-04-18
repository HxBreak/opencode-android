package me.xiaok.opencode.data.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for [OpenCodeApi] using Ktor [MockEngine].
 * Verifies request construction (method, path, query params, body) and
 * response deserialization for every endpoint group.
 *
 * NOTE: ServerConnection.authHeader uses android.util.Base64 which is
 * unavailable on JVM. Tests use empty credentials (authHeader = null on JVM)
 * or verify auth header presence when credentials are non-empty (may throw
 * on JVM — that's acceptable, we test the no-auth path).
 */
class OpenCodeApiTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var testJson: Json
    private val testBaseUrl = "http://test-server:4096"

    @Before
    fun setup() {
        testJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            classDiscriminator = "type"
        }
    }

    // === Helpers ===

    private fun testConn(
        baseUrl: String = testBaseUrl,
        username: String = "",
        password: String = "",
    ) = ServerConnection(
        id = "test-server",
        name = "Test Server",
        baseUrl = baseUrl,
        username = username,
        password = password,
    )

    private fun createApi(engine: MockEngine): OpenCodeApi {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(testJson) }
        }
        return OpenCodeApi(client, testJson)
    }

    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    // ================================================================
    // Global
    // ================================================================

    @Test
    fun `health sends GET to global health endpoint`() = runTest {
        val json = """{"healthy":true,"version":"1.3.10"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/global/health", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.health(testConn())
        assertTrue(result.healthy)
        assertEquals("1.3.10", result.version)
    }

    @Test
    fun `getConfig sends GET to global config endpoint`() = runTest {
        val json = """{"key":"value","nested":{"a":1}}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/global/config", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getConfig(testConn())
        assertNotNull(result)
    }

    // ================================================================
    // Session
    // ================================================================

    @Test
    fun `listSessions sends GET to session endpoint`() = runTest {
        val json = """[{"id":"ses_1","title":"Session 1"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listSessions(testConn())
        assertEquals(1, result.size)
        assertEquals("ses_1", result[0].id)
    }

    @Test
    fun `listSessions includes query parameters`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/session", request.url.encodedPath)
            val params = request.url.parameters
            assertEquals("/home/user/project", params["directory"])
            assertEquals("ws-1", params["workspace"])
            assertEquals("10", params["limit"])
            assertEquals("test", params["search"])
            assertEquals("true", params["roots"])
            respondJson("[]")
        }
        val api = createApi(engine)
        api.listSessions(
            testConn(),
            directory = "/home/user/project",
            workspace = "ws-1",
            limit = 10,
            search = "test",
            roots = true,
        )
    }

    @Test
    fun `createSession sends POST with title body`() = runTest {
        val json = """{"id":"ses_new","title":"My Session"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("My Session"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.createSession(testConn(), title = "My Session")
        assertEquals("ses_new", result.id)
        assertEquals("My Session", result.title)
    }

    @Test
    fun `createSession sends empty body when no title`() = runTest {
        val json = """{"id":"ses_new"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.createSession(testConn())
        assertEquals("ses_new", result.id)
    }

    @Test
    fun `getSession sends GET to session by id`() = runTest {
        val json = """{"id":"ses_123","title":"Test"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session/ses_123", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getSession(testConn(), "ses_123")
        assertEquals("ses_123", result.id)
    }

    @Test
    fun `deleteSession sends DELETE to session endpoint`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/session/ses_123", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.deleteSession(testConn(), "ses_123")
        assertTrue(result)
    }

    @Test
    fun `updateSession sends PATCH with title`() = runTest {
        val json = """{"id":"ses_123","title":"Updated"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/session/ses_123", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("Updated"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.updateSession(testConn(), "ses_123", title = "Updated")
        assertEquals("Updated", result.title)
    }

    @Test
    fun `abortSession sends POST to session abort`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_123/abort", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.abortSession(testConn(), "ses_123")
        assertTrue(result)
    }

    // ================================================================
    // Messages
    // ================================================================

    @Test
    fun `listMessages sends GET to session message endpoint`() = runTest {
        val json = """[{"info":{"role":"user","id":"msg_1","sessionID":"ses_1"},"parts":[]}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session/ses_1/message", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listMessages(testConn(), "ses_1")
        assertEquals(1, result.messages.size)
        assertEquals("msg_1", result.messages[0].id)
    }

    @Test
    fun `listMessages includes limit and before query params`() = runTest {
        val engine = MockEngine { request ->
            val params = request.url.parameters
            assertEquals("20", params["limit"])
            assertEquals("msg_50", params["before"])
            respondJson("[]")
        }
        val api = createApi(engine)
        api.listMessages(testConn(), "ses_1", limit = 20, before = "msg_50")
    }

    @Test
    fun `sendMessage sends POST with JSON body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/prompt_async", request.url.encodedPath)
            // Content-Type is set by ContentNegotiation during body serialization, not as a direct header
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("text"))
            assertTrue(body.contains("Hello"))
            respond("{}", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        api.promptAsync(
            conn = testConn(), sessionId = "ses_1",
            parts = listOf(mapOf("type" to "text", "text" to "Hello")),
        )
    }

    @Test
    fun `sendMessage includes agent and model in body`() = runTest {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("code"))
            assertTrue(body.contains("claude-3"))
            respond("{}", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        api.promptAsync(
            conn = testConn(), sessionId = "ses_1",
            agent = "code",
            model = ModelRef(providerID = "anthropic", modelID = "claude-3"),
        )
    }

    // ================================================================
    // Session Status
    // ================================================================

    @Test
    fun `getSessionStatuses sends GET to session status endpoint`() = runTest {
        val json = """{"ses_1":{"type":"idle"},"ses_2":{"type":"busy"}}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session/status", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getSessionStatuses(testConn())
        assertEquals(2, result.size)
        assertEquals(SessionStatus.Idle, result["ses_1"])
        assertEquals(SessionStatus.Busy, result["ses_2"])
    }

    // ================================================================
    // Project
    // ================================================================

    @Test
    fun `listProjects sends GET to project endpoint`() = runTest {
        val json = """[{"id":"prj_1","worktree":"/home/user/project"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/project", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listProjects(testConn())
        assertEquals(1, result.size)
        assertEquals("prj_1", result[0].id)
    }

    @Test
    fun `getCurrentProject sends GET to project current`() = runTest {
        val json = """{"id":"prj_1","worktree":"/home/user/project"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/project/current", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getCurrentProject(testConn())
        assertEquals("prj_1", result.id)
    }

    // ================================================================
    // Provider
    // ================================================================

    @Test
    fun `getProviders sends GET to provider endpoint`() = runTest {
        val json = """{"all":[],"default":{},"connected":[]}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/provider", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getProviders(testConn())
        assertNotNull(result)
        assertTrue(result.all.isEmpty())
    }

    // ================================================================
    // Provider Auth
    // ================================================================

    @Test
    fun `getProviderAuthMethods sends GET to provider auth`() = runTest {
        val json = """{"methods":[]}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/provider/auth", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getProviderAuthMethods(testConn())
        assertNotNull(result)
    }

    @Test
    fun `authorizeOAuth sends POST to provider oauth authorize`() = runTest {
        val json = """{"url":"https://auth.example.com"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/provider/anthropic/oauth/authorize", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("1"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.authorizeOAuth(testConn(), "anthropic", method = 1)
        assertNotNull(result)
    }

    // ================================================================
    // Auth
    // ================================================================

    @Test
    fun `setAuth sends PUT to auth provider endpoint`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/auth/anthropic", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("sk-test"))
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.setAuth(testConn(), "anthropic", JsonPrimitive("sk-test"))
        assertTrue(result)
    }

    @Test
    fun `removeAuth sends DELETE to auth provider endpoint`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/auth/anthropic", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.removeAuth(testConn(), "anthropic")
        assertTrue(result)
    }

    // ================================================================
    // Permission
    // ================================================================

    @Test
    fun `replyPermission sends POST to permission reply`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/permission/perm_123/reply", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("once"))
            respond("{}", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        api.replyPermission(testConn(), "perm_123", PermissionReply(reply = "once"))
    }

    // ================================================================
    // Question
    // ================================================================

    @Test
    fun `replyQuestion sends POST with answers`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/question/q_123/reply", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            // API expects answers as array of arrays: [["React"], ["Vue"]]
            assertTrue(body.contains("[["))
            assertTrue(body.contains("React"))
            assertTrue(body.contains("Vue"))
            respond("true", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        val result = api.replyQuestion(testConn(), "q_123", answers = listOf(listOf("React"), listOf("Vue")))
        assertTrue(result)
    }

    @Test
    fun `rejectQuestion sends POST to question reject`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/question/q_123/reject", request.url.encodedPath)
            respond("true", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        val result = api.rejectQuestion(testConn(), "q_123")
        assertTrue(result)
    }

    @Test
    fun `replyQuestion returns false on server error`() = runTest {
        val engine = MockEngine { request ->
            respond(
                """{"success":false,"error":[{"code":"invalid_type","message":"expected array"}]}""",
                HttpStatusCode.BadRequest,
            )
        }
        val api = createApi(engine)
        val result = api.replyQuestion(testConn(), "q_123", answers = listOf(listOf("React")))
        assertFalse(result)
    }

    // ================================================================
    // File
    // ================================================================

    @Test
    fun `listFiles sends GET with path query param`() = runTest {
        val json = """[{"name":"src","path":"src","absolute":"/project/src","type":"directory"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/file", request.url.encodedPath)
            assertEquals("src/main", request.url.parameters["path"])
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listFiles(testConn(), path = "src/main")
        assertEquals(1, result.size)
        assertEquals("src", result[0].name)
    }

    @Test
    fun `listFiles uses default path dot`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(".", request.url.parameters["path"])
            respondJson("[]")
        }
        val api = createApi(engine)
        api.listFiles(testConn())
    }

    @Test
    fun `getFileContent sends GET with path query param`() = runTest {
        val content = "fun main() {}"
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/file/content", request.url.encodedPath)
            assertEquals("src/Main.kt", request.url.parameters["path"])
            respondJson("""{"content":"$content"}""")
        }
        val api = createApi(engine)
        val result = api.getFileContent(testConn(), "src/Main.kt")
        assertTrue(result.content.contains("fun main"))
    }

    @Test
    fun `getFileStatuses sends GET to file status endpoint`() = runTest {
        val json = """[{"path":"Main.kt","added":5,"removed":1,"status":"modified"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/file/status", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getFileStatuses(testConn())
        assertEquals(1, result.size)
        assertEquals("Main.kt", result[0].path)
    }

    // ================================================================
    // Find
    // ================================================================

    @Test
    fun `textSearch sends GET with pattern query param`() = runTest {
        val json = """[{"file":"App.kt","line":10}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/find", request.url.encodedPath)
            assertEquals("TODO", request.url.parameters["pattern"])
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.textSearch(testConn(), "TODO")
        assertEquals(1, result.size)
    }

    @Test
    fun `fileSearch sends GET to find file endpoint`() = runTest {
        val json = """["src/App.kt","src/Utils.kt"]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/find/file", request.url.encodedPath)
            assertEquals("App", request.url.parameters["query"])
            assertEquals("src", request.url.parameters["dirs"])
            assertEquals("kt", request.url.parameters["type"])
            assertEquals("10", request.url.parameters["limit"])
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.fileSearch(testConn(), "App", dirs = "src", type = "kt", limit = 10)
        assertEquals(2, result.size)
    }

    @Test
    fun `symbolSearch sends GET to find symbol endpoint`() = runTest {
        val json = """[{"name":"main","kind":"function"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/find/symbol", request.url.encodedPath)
            assertEquals("main", request.url.parameters["query"])
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.symbolSearch(testConn(), "main")
        assertEquals(1, result.size)
    }

    // ================================================================
    // VCS
    // ================================================================

    @Test
    fun `getVcsInfo sends GET to vcs endpoint`() = runTest {
        val json = """{"branch":"main"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/vcs", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getVcsInfo(testConn())
        assertEquals("main", result.branch)
    }

    @Test
    fun `getVcsBranch sends GET to vcs endpoint`() = runTest {
        val json = """{"branch":"feature/test"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/vcs", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getVcsBranch(testConn())
        assertEquals("feature/test", result.branch)
    }

    // ================================================================
    // Session Operations
    // ================================================================

    @Test
    fun `forkSession sends POST with messageID body`() = runTest {
        val json = """{"id":"ses_forked","title":"Forked"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/fork", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("msg_10"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.forkSession(testConn(), "ses_1", "msg_10")
        assertEquals("ses_forked", result.id)
    }

    @Test
    fun `shareSession sends POST to session share`() = runTest {
        val json = """{"url":"https://share.example.com/abc"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/share", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.shareSession(testConn(), "ses_1")
        assertEquals("https://share.example.com/abc", result.url)
    }

    @Test
    fun `unshareSession sends DELETE to session share`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/session/ses_1/share", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.unshareSession(testConn(), "ses_1")
        assertTrue(result)
    }

    @Test
    fun `revertSession sends POST with messageID body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/revert", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("msg_5"))
            respond("{}", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        api.revertSession(testConn(), "ses_1", "msg_5")
    }

    @Test
    fun `summarizeSession sends POST with provider and model`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/summarize", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("anthropic"))
            assertTrue(body.contains("claude-3"))
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.summarizeSession(testConn(), "ses_1", providerId = "anthropic", modelId = "claude-3")
        assertTrue(result)
    }

    @Test
    fun `unrevertSession sends POST to session unrevert`() = runTest {
        val json = """{"id":"ses_1","title":"Restored"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/unrevert", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.unrevertSession(testConn(), "ses_1")
        assertEquals("ses_1", result.id)
    }

    @Test
    fun `initSession sends POST to session init`() = runTest {
        val json = """{"id":"ses_1","title":"Init"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/init", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.initSession(testConn(), "ses_1")
        assertEquals("ses_1", result.id)
    }

    @Test
    fun `getSessionChildren sends GET to session children`() = runTest {
        val json = """[{"id":"ses_c1"},{"id":"ses_c2"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session/ses_parent/children", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getSessionChildren(testConn(), "ses_parent")
        assertEquals(2, result.size)
    }

    // ================================================================
    // Message Operations
    // ================================================================

    @Test
    fun `deleteMessage sends DELETE to message endpoint`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/session/ses_1/message/msg_1", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.deleteMessage(testConn(), "ses_1", "msg_1")
        assertTrue(result)
    }

    @Test
    fun `patchMessagePart sends PATCH with update body`() = runTest {
        val json = """{"type":"text","text":"edited","id":"prt_1","sessionID":"ses_1","messageID":"msg_1"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/session/ses_1/message/msg_1/part/prt_1", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.patchMessagePart(
            testConn(), "ses_1", "msg_1", "prt_1",
            mapOf("text" to JsonPrimitive("edited")),
        )
        assertNotNull(result)
    }

    @Test
    fun `deleteMessagePart sends DELETE to part endpoint`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/session/ses_1/message/msg_1/part/prt_1", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.deleteMessagePart(testConn(), "ses_1", "msg_1", "prt_1")
        assertTrue(result)
    }

    // ================================================================
    // Diff
    // ================================================================

    @Test
    fun `getSessionDiff sends GET to session diff endpoint`() = runTest {
        val json = """[{"file":"App.kt","additions":5,"deletions":1,"before":"","after":""}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session/ses_1/diff", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getSessionDiff(testConn(), "ses_1")
        assertEquals(1, result.size)
        assertEquals("App.kt", result[0].path)
    }

    @Test
    fun `getSessionDiff includes messageID query param`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("msg_5", request.url.parameters["messageID"])
            respondJson("[]")
        }
        val api = createApi(engine)
        api.getSessionDiff(testConn(), "ses_1", messageId = "msg_5")
    }

    // ================================================================
    // Todo
    // ================================================================

    @Test
    fun `getSessionTodos sends GET to session todo endpoint`() = runTest {
        val json = """[{"id":"todo_1","content":"Fix bug","status":"pending","priority":"high"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session/ses_1/todo", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getSessionTodos(testConn(), "ses_1")
        assertEquals(1, result.size)
        assertEquals("Fix bug", result[0].content)
        assertEquals("high", result[0].priority)
    }

    // ================================================================
    // Shell & Command
    // ================================================================

    @Test
    fun `runShell sends POST with command body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/shell", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("ls"))
            assertTrue(body.contains("-la"))
            respond("{}", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        api.runShell(testConn(), "ses_1", command = "ls", agent = "default")
    }

    @Test
    fun `sendCommand sends POST with command body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/session/ses_1/command", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("commit"))
            assertTrue(body.contains("fix bug"))
            respond("{}", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        api.sendCommand(testConn(), "ses_1", command = "commit", arguments = "fix bug")
    }

    // ================================================================
    // PTY
    // ================================================================

    @Test
    fun `listPtys sends GET to pty endpoint`() = runTest {
        val json = """[{"id":"pty_1","title":"Terminal","command":"bash","args":[],"cwd":"/project","status":"running","pid":123}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/pty", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listPtys(testConn())
        assertEquals(1, result.size)
        assertEquals("pty_1", result[0].id)
    }

    @Test
    fun `listPtys includes directory header`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/home/user/project", request.headers["x-opencode-directory"])
            respondJson("[]")
        }
        val api = createApi(engine)
        api.listPtys(testConn(), directory = "/home/user/project")
    }

    @Test
    fun `createPty sends POST with filtered body`() = runTest {
        val json = """{"id":"pty_new","title":"Build","command":"bash","args":[],"cwd":"/project","status":"running","pid":456}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/pty", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("bash"))
            assertTrue(body.contains("New Terminal"))
            assertFalse(body.contains("\"args\":null"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.createPty(
            testConn(),
            PtyCreateRequest(command = "bash", title = "New Terminal"),
        )
        assertEquals("pty_new", result.id)
    }

    @Test
    fun `createPty includes directory header`() = runTest {
        val json = """{"id":"pty_1","title":"","command":"","args":[],"cwd":"","status":"","pid":0}"""
        val engine = MockEngine { request ->
            assertEquals("/project", request.headers["x-opencode-directory"])
            respondJson(json)
        }
        val api = createApi(engine)
        api.createPty(testConn(), directory = "/project")
    }

    @Test
    fun `getPty sends GET to pty by id`() = runTest {
        val json = """{"id":"pty_1","title":"Term","command":"bash","args":[],"cwd":"/","status":"running","pid":1}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/pty/pty_1", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getPty(testConn(), "pty_1")
        assertEquals("pty_1", result.id)
    }

    @Test
    fun `updatePty sends PUT with update body`() = runTest {
        val json = """{"id":"pty_1","title":"Updated","command":"","args":[],"cwd":"","status":"running","pid":1}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/pty/pty_1", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.updatePty(testConn(), "pty_1", PtyUpdateRequest(title = "Updated"))
        assertEquals("Updated", result.title)
    }

    @Test
    fun `deletePty sends DELETE to pty endpoint`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/pty/pty_1", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.deletePty(testConn(), "pty_1")
        assertTrue(result)
    }

    // ================================================================
    // Agent
    // ================================================================

    @Test
    fun `getAgents sends GET to agent endpoint`() = runTest {
        val json = """[{"name":"code","description":"Main agent","mode":"primary"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/agent", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getAgents(testConn())
        assertEquals(1, result.size)
        assertEquals("code", result[0].name)
    }

    // ================================================================
    // Commands
    // ================================================================

    @Test
    fun `getCommands sends GET to command endpoint`() = runTest {
        val json = """[{"name":"commit","description":"Create commit","source":"command"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/command", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getCommands(testConn())
        assertEquals(1, result.size)
        assertEquals("commit", result[0].name)
    }

    // ================================================================
    // Config
    // ================================================================

    @Test
    fun `getProjectConfig sends GET to config endpoint`() = runTest {
        val json = """{"providers":{}}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/config", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getProjectConfig(testConn())
        assertNotNull(result)
    }

    @Test
    fun `patchProjectConfig sends PATCH with config body`() = runTest {
        val json = """{"updated":true}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/config", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.patchProjectConfig(testConn(), JsonObject(emptyMap()))
        assertNotNull(result)
    }

    @Test
    fun `getConfigProviders sends GET to config providers`() = runTest {
        val json = """{"providers":[],"default":{}}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/config/providers", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getConfigProviders(testConn())
        assertNotNull(result)
    }

    // ================================================================
    // Skill
    // ================================================================

    @Test
    fun `getSkills sends GET to skill endpoint`() = runTest {
        val json = """[{"name":"code-review","description":"Review code"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/skill", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getSkills(testConn())
        assertEquals(1, result.size)
        assertEquals("code-review", result[0].name)
    }

    // ================================================================
    // MCP
    // ================================================================

    @Test
    fun `listMcpServers sends GET to mcp endpoint`() = runTest {
        val json = """{"filesystem":{"status":"connected"}}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/mcp", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listMcpServers(testConn())
        assertEquals(1, result.size)
        assertEquals("connected", result["filesystem"]?.status)
    }

    @Test
    fun `addMcpServer sends POST with request body`() = runTest {
        val json = """{"newserver":{"status":"connected"}}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/mcp", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("filesystem"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.addMcpServer(testConn(), McpServerCreateRequest(name = "filesystem", config = McpServerConfig()))
        assertNotNull(result)
    }

    @Test
    fun `connectMcpServer sends POST to mcp connect`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/mcp/filesystem/connect", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.connectMcpServer(testConn(), "filesystem")
        assertTrue(result)
    }

    @Test
    fun `disconnectMcpServer sends POST to mcp disconnect`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/mcp/filesystem/disconnect", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.disconnectMcpServer(testConn(), "filesystem")
        assertTrue(result)
    }

    @Test
    fun `startMcpAuth sends POST to mcp auth`() = runTest {
        val json = """{"authorizationUrl":"https://auth.example.com"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/mcp/filesystem/auth", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.startMcpAuth(testConn(), "filesystem")
        assertEquals("https://auth.example.com", result.authorizationUrl)
    }

    @Test
    fun `completeMcpAuth sends POST to mcp auth callback`() = runTest {
        val json = """{"status":"connected"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/mcp/filesystem/auth/callback", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("auth_code"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.completeMcpAuth(testConn(), "filesystem", "auth_code")
        assertEquals("connected", result.status)
    }

    @Test
    fun `authenticateMcp sends POST to mcp authenticate`() = runTest {
        val json = """{"status":"connected"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/mcp/filesystem/auth/authenticate", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.authenticateMcp(testConn(), "filesystem")
        assertEquals("connected", result.status)
    }

    @Test
    fun `removeMcpAuth sends DELETE to mcp auth`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/mcp/filesystem/auth", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.removeMcpAuth(testConn(), "filesystem")
        assertTrue(result)
    }

    // ================================================================
    // Path
    // ================================================================

    @Test
    fun `getPathInfo sends GET to path endpoint`() = runTest {
        val json = """{"home":"/home/user","state":"/state","config":"/config","worktree":"/project","directory":"/project"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/path", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getPathInfo(testConn())
        assertEquals("/home/user", result.home)
        assertEquals("/project", result.worktree)
    }

    // ================================================================
    // Formatter
    // ================================================================

    @Test
    fun `getFormatters sends GET to formatter endpoint`() = runTest {
        val json = """[{"name":"ktfmt","extensions":[".kt"],"enabled":true}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/formatter", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getFormatters(testConn())
        assertEquals(1, result.size)
        assertEquals("ktfmt", result[0].name)
        assertTrue(result[0].enabled)
    }

    // ================================================================
    // Instance
    // ================================================================

    @Test
    fun `disposeInstance sends POST to instance dispose`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/instance/dispose", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.disposeInstance(testConn())
        assertTrue(result)
    }

    @Test
    fun `disposeInstance includes directory header`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/home/user/project", request.headers["x-opencode-directory"])
            respondJson("true")
        }
        val api = createApi(engine)
        api.disposeInstance(testConn(), directory = "/home/user/project")
    }

    // ================================================================
    // Log
    // ================================================================

    @Test
    fun `sendLog sends POST with log entry body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/log", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("opencode"))
            assertTrue(body.contains("Session created"))
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.sendLog(testConn(), LogEntry(service = "opencode", level = "info", message = "Session created"))
        assertTrue(result)
    }

    // ================================================================
    // LSP
    // ================================================================

    @Test
    fun `getLspServers sends GET to lsp endpoint`() = runTest {
        val json = """[{"id":"kotlin","name":"Kotlin LS","root":"/project","status":"connected"}]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/lsp", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getLspServers(testConn())
        assertEquals(1, result.size)
        assertEquals("kotlin", result[0].id)
    }

    // ================================================================
    // Experimental - Workspaces
    // ================================================================

    @Test
    fun `listWorkspaces sends GET to experimental workspace`() = runTest {
        val json = """{"workspaces":[]}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/experimental/workspace", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listWorkspaces(testConn())
        assertNotNull(result)
    }

    @Test
    fun `createWorkspace sends POST with request body`() = runTest {
        val json = """{"id":"ws-1"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/experimental/workspace", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("worktree"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.createWorkspace(testConn(), WorkspaceCreateRequest(type = "worktree"))
        assertNotNull(result)
    }

    @Test
    fun `deleteWorkspace sends DELETE to experimental workspace by id`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/experimental/workspace/ws-1", request.url.encodedPath)
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.deleteWorkspace(testConn(), "ws-1")
        assertTrue(result)
    }

    // ================================================================
    // Experimental - Worktrees
    // ================================================================

    @Test
    fun `listWorktrees sends GET to experimental worktree`() = runTest {
        val json = """["main","feature/test"]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/experimental/worktree", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.listWorktrees(testConn())
        assertEquals(2, result.size)
    }

    @Test
    fun `createWorktree sends POST with request body`() = runTest {
        val json = """{"path":"/worktrees/feature"}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/experimental/worktree", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("feature-branch"))
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.createWorktree(testConn(), WorktreeCreateRequest(name = "feature-branch"))
        assertNotNull(result)
    }

    @Test
    fun `deleteWorktree sends DELETE with request body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/experimental/worktree", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("/worktrees/feature"))
            respondJson("true")
        }
        val api = createApi(engine)
        val result = api.deleteWorktree(testConn(), WorktreeDeleteRequest(directory = "/worktrees/feature"))
        assertTrue(result)
    }

    @Test
    fun `resetWorktree sends POST to experimental worktree reset`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/experimental/worktree/reset", request.url.encodedPath)
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("/worktrees/feature"))
            respond("{}", HttpStatusCode.OK)
        }
        val api = createApi(engine)
        api.resetWorktree(testConn(), WorktreeResetRequest(directory = "/worktrees/feature"))
    }

    // ================================================================
    // Experimental - Resources & Tools
    // ================================================================

    @Test
    fun `getExperimentalResources sends GET to experimental resource`() = runTest {
        val json = """{"resources":[]}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/experimental/resource", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getExperimentalResources(testConn())
        assertNotNull(result)
    }

    @Test
    fun `getExperimentalTools sends GET with provider and model params`() = runTest {
        val json = """{"tools":[]}"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/experimental/tool", request.url.encodedPath)
            assertEquals("anthropic", request.url.parameters["provider"])
            assertEquals("claude-3", request.url.parameters["model"])
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getExperimentalTools(testConn(), "anthropic", "claude-3")
        assertNotNull(result)
    }

    @Test
    fun `getExperimentalToolIds sends GET to experimental tool ids`() = runTest {
        val json = """["bash","read","write"]"""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/experimental/tool/ids", request.url.encodedPath)
            respondJson(json)
        }
        val api = createApi(engine)
        val result = api.getExperimentalToolIds(testConn())
        assertEquals(3, result.size)
    }
}
