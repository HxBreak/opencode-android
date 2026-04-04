package me.xiaok.opencode.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.xiaok.opencode.domain.model.ServerConnection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Layer-by-layer test to isolate the Ktor POST hang.
 *
 * Test hierarchy (each adds one layer):
 *   1. OkHttp raw POST           → baseline: does the server accept messages?
 *   2. Ktor bare client POST     → does Ktor + OkHttp engine work?
 *   3. Ktor + ContentNegotiation  → does ContentNegotiation plugin cause hang?
 *   4. Ktor + ContentNeg + Timeout → does HttpTimeout plugin cause hang?
 *   5. Full OpenCodeApi.promptAsync → exact production code path
 *
 * If test N fails but test N-1 passes, the hang is in the layer added at step N.
 *
 * NOTE: These tests hit a real server. They require network access.
 * Server IP and session must be valid for tests to pass.
 */
class SendMessageTest {

    private val serverUrl = "http://192.168.31.203:4096"
    private val testSessionId = "ses_2af454008ffegTvpxhpTsidgPj"

    private lateinit var json: Json

    @Before
    fun setup() {
        json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            classDiscriminator = "type"
        }
    }

    // ============================================================
    // Layer 1: OkHttp raw POST — baseline
    // ============================================================

    @Test
    fun `layer1 - OkHttp raw POST returns 200`() {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val jsonBody = """{"parts":[{"type":"text","text":"你好"}]}"""

        val request = Request.Builder()
            .url("$serverUrl/session/$testSessionId/message")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        println(">>> [Layer1] OkHttp POST 你好 to session=$testSessionId")

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            println("<<< [Layer1] code=${response.code}, body=${body?.take(200)}")
            assertEquals("OkHttp POST should return 200", 200, response.code)
        }
    }

    @Test
    fun `layer1 - OkHttp GET health returns 200`() {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("$serverUrl/global/health")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            println("<<< [Layer1] GET /global/health code=${response.code}")
            assertEquals(200, response.code)
        }
    }

    // ============================================================
    // Layer 2: Ktor bare client (no plugins) with OkHttp engine
    // ============================================================

    @Test
    fun `layer2 - Ktor bare client POST returns 200`() = runBlocking {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            // NO plugins — bare Ktor + OkHttp engine
        }

        try {
            println(">>> [Layer2] Ktor bare POST 你好 to session=$testSessionId")

            val response = client.post("$serverUrl/session/$testSessionId/message") {
                contentType(ContentType.Application.Json)
                setBody("""{"parts":[{"type":"text","text":"你好"}]}""")
            }

            println("<<< [Layer2] Ktor bare POST status=${response.status.value}")
            assertEquals("Ktor bare POST should return 200", 200, response.status.value)
        } finally {
            client.close()
        }
    }

    // ============================================================
    // Layer 3: Ktor + ContentNegotiation plugin
    // ============================================================

    @Test
    fun `layer3 - Ktor with ContentNegotiation POST returns 200`() = runBlocking {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json(json)
            }
        }

        try {
            println(">>> [Layer3] Ktor+ContentNeg POST 你好 to session=$testSessionId")

            val response = client.post("$serverUrl/session/$testSessionId/message") {
                contentType(ContentType.Application.Json)
                setBody("""{"parts":[{"type":"text","text":"你好"}]}""")
            }

            println("<<< [Layer3] Ktor+ContentNeg POST status=${response.status.value}")
            assertEquals("Ktor+ContentNeg POST should return 200", 200, response.status.value)
        } finally {
            client.close()
        }
    }

    // ============================================================
    // Layer 4: Ktor + ContentNegotiation + HttpTimeout (production config)
    // ============================================================

    @Test
    fun `layer4 - Ktor full production config POST returns 200`() = runBlocking {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
        }

        try {
            println(">>> [Layer4] Ktor full config POST 你好 to session=$testSessionId")

            val response = client.post("$serverUrl/session/$testSessionId/message") {
                contentType(ContentType.Application.Json)
                setBody("""{"parts":[{"type":"text","text":"你好"}]}""")
            }

            println("<<< [Layer4] Ktor full config POST status=${response.status.value}")
            assertEquals("Ktor full config POST should return 200", 200, response.status.value)
        } finally {
            client.close()
        }
    }

    // ============================================================
    // Layer 5: Full OpenCodeApi.promptAsync() — exact production path
    // ============================================================

    @Test
    fun `layer5 - OpenCodeApi sendMessage returns successfully`() = runBlocking {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val ktorClient = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
        }

        val api = OpenCodeApi(ktorClient, json)

        // ServerConnection with no auth (authHeader will be null since username/password are empty)
        // Note: authHeader uses android.util.Base64 which is NOT available in JVM unit tests,
        // so we create a connection with empty credentials. The test server doesn't require auth.
        val conn = ServerConnection(
            id = "test-server",
            name = "test",
            baseUrl = serverUrl,
            // username and password are empty → authHeader will be null
        )

        try {
            println(">>> [Layer5] OpenCodeApi.promptAsync to session=$testSessionId")
            println(">>> [Layer5] conn.authHeader will be computed (may fail on JVM without android.util.Base64)")

            api.promptAsync(
                conn = conn,
                sessionId = testSessionId,
                parts = listOf(mapOf("type" to "text", "text" to "你好")),
            )

            println("<<< [Layer5] OpenCodeApi.promptAsync returned successfully (no exception)")
            // promptAsync returns Unit (void), so no return value to assert
            // If we reach here without hanging or exception, the test passes
        } finally {
            ktorClient.close()
        }
    }

    // ============================================================
    // Layer 6: Investigate response body handling
    // ============================================================

    @Test
    fun `layer6 - examine promptAsync response body via OkHttp`() {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val jsonBody = """{"parts":[{"type":"text","text":"你好"}]}"""

        val request = Request.Builder()
            .url("$serverUrl/session/$testSessionId/message")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        println(">>> [Layer6] Inspecting response details for 你好")

        client.newCall(request).execute().use { response ->
            println("<<< [Layer6] code=${response.code}")
            println("<<< [Layer6] headers=${response.headers}")
            println("<<< [Layer6] content-type=${response.header("content-type")}")
            println("<<< [Layer6] content-length=${response.header("content-length")}")
            println("<<< [Layer6] transfer-encoding=${response.header("transfer-encoding")}")
            println("<<< [Layer6] connection=${response.header("connection")}")

            val body = response.body
            println("<<< [Layer6] body content-length=${body?.contentLength()}")
            println("<<< [Layer6] body content-type=${body?.contentType()}")

            val bodyString = body?.string()
            println("<<< [Layer6] body length=${bodyString?.length}")
            println("<<< [Layer6] body content=${bodyString?.take(500)}")

            assertEquals(200, response.code)
            assertNotNull("Response body should not be null", bodyString)
        }
    }

    // ============================================================
    // Layer 7: Test with empty body (no parts) — minimal POST
    // ============================================================

    @Test
    fun `layer7 - Ktor POST with empty JSON body`() = runBlocking {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json(json)
            }
        }

        try {
            println(">>> [Layer7] Ktor POST with empty body {}")

            val response = client.post("$serverUrl/session/$testSessionId/message") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }

            println("<<< [Layer7] status=${response.status.value}")
            // Even empty body should get a response (server may reject, but should NOT hang)
            assertTrue("Should get some response, not hang", response.status.value in 200..499)
        } finally {
            client.close()
        }
    }
}
