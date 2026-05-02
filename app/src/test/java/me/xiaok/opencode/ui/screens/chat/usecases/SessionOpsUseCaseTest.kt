package me.xiaok.opencode.ui.screens.chat.usecases

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.ErrorCollector
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionOpsUseCaseTest {

    private val testServer = TestFixtures.testServerConnection()
    private lateinit var api: OpenCodeApi
    private lateinit var eventReducer: EventReducer
    private lateinit var serverRepository: ServerRepository
    private lateinit var errorCollector: ErrorCollector
    private lateinit var useCase: SessionOpsUseCase
    private val testJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminator = "type"
    }

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        val cacheRepository = mockk<CacheRepository>(relaxed = true)
        eventReducer = EventReducer(cacheRepository, kotlinx.coroutines.test.TestScope())
        api = createApi(MockEngine { respondJson("true") })
        serverRepository = mockk(relaxed = true)
        errorCollector = mockk(relaxed = true)
        useCase = SessionOpsUseCase(api, eventReducer, serverRepository, errorCollector)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createApi(engine: MockEngine): OpenCodeApi {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(testJson) }
        }
        return OpenCodeApi(client, testJson)
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    // ====================================================================
    // summarizeSession
    // ====================================================================

    @Test(expected = IllegalStateException::class)
    fun `summarizeSession throws when server not found`() = runTest {
        every { serverRepository.getServer("missing") } returns null

        useCase.summarizeSession(
            serverId = "missing",
            sessionId = "session1",
            selectedModel = null,
            sessionDirectory = null,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `summarizeSession throws when API returns false`() = runTest {
        every { serverRepository.getServer(any()) } returns testServer
        api = createApi(
            MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/session/session1/summarize", request.url.encodedPath)
                respondJson("false")
            }
        )
        useCase = SessionOpsUseCase(api, eventReducer, serverRepository, errorCollector)
        val selectedModel = ModelRef(providerID = "anthropic", modelID = "claude-3-sonnet")

        useCase.summarizeSession(
            serverId = testServer.id,
            sessionId = "session1",
            selectedModel = selectedModel,
            sessionDirectory = null,
        )
    }

    @Test
    fun `summarizeSession succeeds when API returns true`() = runTest {
        every { serverRepository.getServer(any()) } returns testServer
        api = createApi(
            MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/session/session1/summarize", request.url.encodedPath)
                val body = request.body.toByteArray().decodeToString()
                assertTrue(body.contains("anthropic"))
                assertTrue(body.contains("claude-3-sonnet"))
                respondJson("true")
            }
        )
        useCase = SessionOpsUseCase(api, eventReducer, serverRepository, errorCollector)
        val selectedModel = ModelRef(providerID = "anthropic", modelID = "claude-3-sonnet")

        useCase.summarizeSession(
            serverId = testServer.id,
            sessionId = "session1",
            selectedModel = selectedModel,
            sessionDirectory = null,
        )

    }

    @Test
    fun `summarizeSession falls back to last assistant message model when selection is null`() = runTest {
        every { serverRepository.getServer(any()) } returns testServer
        api = createApi(
            MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/session/session1/summarize", request.url.encodedPath)
                val body = request.body.toByteArray().decodeToString()
                assertTrue(body.contains("openai"))
                assertTrue(body.contains("gpt-4.1"))
                respondJson("true")
            }
        )
        useCase = SessionOpsUseCase(api, eventReducer, serverRepository, errorCollector)
        eventReducer.setMessages(
            "session1",
            listOf(
                TestFixtures.testMessage(
                    info = TestFixtures.testUserMessageInfo(id = "msg_user", sessionID = "session1"),
                ),
                TestFixtures.testMessage(
                    info = TestFixtures.testMessageInfo(
                        id = "msg_assistant",
                        sessionID = "session1",
                        providerID = "openai",
                        modelID = "gpt-4.1",
                    ),
                ),
            ),
        )

        useCase.summarizeSession(
            serverId = testServer.id,
            sessionId = "session1",
            selectedModel = null,
            sessionDirectory = null,
        )
    }
}
