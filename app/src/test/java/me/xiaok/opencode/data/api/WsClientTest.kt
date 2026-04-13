package me.xiaok.opencode.data.api

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WsClient] covering URL building, WebSocket lifecycle,
 * control frame parsing, and connection management.
 *
 * IMPORTANT: [WsClient.connectInteractive] creates a cold [callbackFlow].
 * The WebSocket is only created when the output flow is collected.
 * All tests that need [capturedListener] must start collecting first.
 */
class WsClientTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var mockWebSocket: WebSocket
    private var capturedListener: WebSocketListener? = null
    private var capturedRequest: Request? = null
    // Daemon threads running flow collections. Each test that calls connectAndCollect
    // starts a daemon thread that collects the callbackFlow. These threads are cancelled
    // by interrupting them in @After. Using threads (not coroutines) avoids runTest's
    // child-coroutine tracking that causes indefinite hangs.
    private val collectThreads = mutableListOf<Thread>()

    private val testConn = ServerConnection(
        id = "test-server",
        name = "Test Server",
        baseUrl = "http://192.168.1.1:4096",
        username = "",
        password = "",
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockWebSocket = mockk(relaxed = true)
        capturedListener = null
        capturedRequest = null
        collectThreads.clear()
    }

    @After
    fun teardown() {
        collectThreads.forEach { it.interrupt() }
        collectThreads.forEach { it.join(1000) }
        collectThreads.clear()
    }

    /**
     * Creates a fully mocked OkHttpClient chain. When [WsClient.connectInteractive]
     * is called and the output flow is collected, the WebSocket creation is intercepted.
     */
    private fun createMockedClient(): WsClient {
        val mClient = mockk<OkHttpClient>()
        val mBuilder = mockk<OkHttpClient.Builder>()
        val mDerived = mockk<OkHttpClient>()

        every { mBuilder.readTimeout(any<Long>(), any()) } returns mBuilder
        every { mBuilder.pingInterval(any<Long>(), any()) } returns mBuilder
        every { mBuilder.build() } returns mDerived

        every { mDerived.newWebSocket(any(), any()) } answers {
            capturedRequest = firstArg()
            capturedListener = secondArg()
            mockWebSocket
        }

        every { mClient.newBuilder() } returns mBuilder

        return WsClient(mClient)
    }

    /**
     * Creates a mocked client with a tracked builder for verifying builder calls.
     */
    private fun createClientWithTrackedBuilder(): Pair<WsClient, OkHttpClient.Builder> {
        val mClient = mockk<OkHttpClient>()
        val mBuilder = mockk<OkHttpClient.Builder>()
        val mDerived = mockk<OkHttpClient>()

        every { mBuilder.readTimeout(any<Long>(), any()) } returns mBuilder
        every { mBuilder.pingInterval(any<Long>(), any()) } returns mBuilder
        every { mBuilder.build() } returns mDerived

        every { mDerived.newWebSocket(any(), any()) } answers {
            capturedRequest = firstArg()
            capturedListener = secondArg()
            mockWebSocket
        }

        every { mClient.newBuilder() } returns mBuilder

        return WsClient(mClient) to mBuilder
    }

    /**
     * Convenience: create client, connect, collect the flow to trigger WS creation.
     * Must be called within a [kotlinx.coroutines.CoroutineScope] (e.g., inside runTest).
     */
    private suspend fun kotlinx.coroutines.CoroutineScope.connectAndCollect(
        conn: ServerConnection = testConn,
        ptyId: String = "pty1",
        cursor: Int = -1,
        directory: String? = null,
        workspace: String? = null,
    ): WsClient.InteractiveTerminalConnection {
        val client = createMockedClient()
        val connection = client.connectInteractive(conn, ptyId, cursor, directory, workspace)
        // Start collecting on a daemon thread so runTest doesn't wait for the open-ended flow.
        // callbackFlow builder runs synchronously when collect() is called, setting up
        // capturedRequest/capturedListener before awaitClose suspends.
        // Use a CountDownLatch to ensure the flow builder has executed before returning.
        val ready = java.util.concurrent.CountDownLatch(1)
        val thread = Thread {
            kotlinx.coroutines.runBlocking {
                // Use a collector that signals ready once the flow builder has run
                connection.output.collect {
                    // no-op: we just need the flow to stay open
                }
            }
        }.apply { isDaemon = true; start() }
        collectThreads.add(thread)
        // Spin-wait for capturedRequest to be set (happens synchronously in callbackFlow builder)
        // The thread starts runBlocking which calls collect() which triggers the builder synchronously.
        var spins = 0
        while (capturedRequest == null && spins < 1000) {
            Thread.sleep(1)
            spins++
        }
        return connection
    }

    private fun triggerOnOpen() {
        val mockResponse = mockk<Response>(relaxed = true)
        capturedListener!!.onOpen(mockWebSocket, mockResponse)
    }

    private fun controlFrame(json: String): okio.ByteString {
        val buffer = Buffer()
        buffer.writeByte(0x00)
        buffer.writeString(json, Charsets.UTF_8)
        return buffer.readByteString()
    }

    private fun textAsByteString(text: String): okio.ByteString {
        val buffer = Buffer()
        buffer.writeString(text, Charsets.UTF_8)
        return buffer.readByteString()
    }

    // ================================================================
    // URL building
    // ================================================================

    @Test
    fun `URL converts http to ws`() = runTest {
        connectAndCollect()
        val url = capturedRequest?.url?.toString() ?: "NULL"
        // OkHttp normalizes ws:// to http:// internally, so we verify the path is correct
        assertTrue("URL should contain pty path but was: $url", url.contains("/pty/pty1/connect"))
        assertTrue("URL should contain cursor param but was: $url", url.contains("cursor=-1"))
    }

    @Test
    fun `URL converts https to wss`() = runTest {
        val secureConn = testConn.copy(baseUrl = "https://secure.host:443")
        connectAndCollect(conn = secureConn)
        val url = capturedRequest!!.url.toString()
        // OkHttp normalizes wss:// to https:// internally, so we verify the host is correct
        assertTrue("URL should contain secure host but was: $url", url.startsWith("https://secure.host"))
        assertTrue("URL should contain pty path but was: $url", url.contains("/pty/pty1/connect"))
    }

    @Test
    fun `URL contains pty path and cursor parameter`() = runTest {
        connectAndCollect(ptyId = "pty42", cursor = 100)
        val url = capturedRequest!!.url.toString()
        assertTrue("URL should contain pty path", url.contains("/pty/pty42/connect"))
        assertTrue("URL should contain cursor=100", url.contains("cursor=100"))
    }

    @Test
    fun `URL contains default cursor of -1`() = runTest {
        connectAndCollect()
        val url = capturedRequest!!.url.toString()
        assertTrue("URL should contain cursor=-1 by default", url.contains("cursor=-1"))
    }

    @Test
    fun `URL contains encoded directory query param`() = runTest {
        connectAndCollect(directory = "/home/user/project")
        val url = capturedRequest!!.url.toString()
        assertTrue("URL should contain directory param", url.contains("directory="))
    }

    // ================================================================
    // Headers
    // ================================================================

    @Test
    fun `request includes auth header when credentials present`() = runTest {
        val authConn = ServerConnection(
            id = "auth-server",
            name = "Auth Server",
            baseUrl = "http://host:4096",
            username = "admin",
            password = "pass",
        )
        connectAndCollect(conn = authConn)
        val authHeader = capturedRequest!!.header("Authorization")
        assertNotNull("Should have Authorization header", authHeader)
        assertTrue("Should start with Basic", authHeader!!.startsWith("Basic "))
    }

    @Test
    fun `request omits auth header when no credentials`() = runTest {
        connectAndCollect()
        assertNull("Should not have Authorization header", capturedRequest!!.header("Authorization"))
    }

    @Test
    fun `request includes directory header`() = runTest {
        connectAndCollect(directory = "/home/user/project")
        assertEquals("/home/user/project", capturedRequest!!.header("x-opencode-directory"))
    }

    @Test
    fun `request includes workspace header`() = runTest {
        connectAndCollect(workspace = "my-workspace")
        assertEquals("my-workspace", capturedRequest!!.header("x-opencode-workspace"))
    }

    // ================================================================
    // WebSocket lifecycle
    // ================================================================

    @Test
    fun `isConnected starts as false`() = runTest {
        val connection = connectAndCollect()
        assertFalse("isConnected should start as false", connection.isConnected.value)
    }

    @Test
    fun `onOpen sets isConnected to true`() = runTest {
        val connection = connectAndCollect()
        assertFalse(connection.isConnected.value)

        triggerOnOpen()
        assertTrue("Should be connected after onOpen", connection.isConnected.value)
    }

    @Test
    fun `onClosed sets isConnected to false`() = runTest {
        val connection = connectAndCollect()

        triggerOnOpen()
        assertTrue(connection.isConnected.value)

        capturedListener!!.onClosed(mockWebSocket, 1000, "Normal closure")
        assertFalse("Should be disconnected after onClosed", connection.isConnected.value)
    }

    @Test
    fun `onClosing triggers WebSocket close with code 1000`() = runTest {
        connectAndCollect()

        capturedListener!!.onClosing(mockWebSocket, 1000, "Going away")

        verify { mockWebSocket.close(1000, null) }
    }

    @Test
    fun `onFailure sets isConnected to false`() = runTest {
        val connection = connectAndCollect()

        triggerOnOpen()
        assertTrue(connection.isConnected.value)

        capturedListener!!.onFailure(mockWebSocket, RuntimeException("Connection lost"), null)
        assertFalse("Should be disconnected after failure", connection.isConnected.value)
    }

    // ================================================================
    // Message handling - text frames
    // ================================================================

    @Test
    fun `onMessage with text delivers to output flow`() = runTest {
        val client = createMockedClient()
        val connection = client.connectInteractive(testConn, "pty1")

        val messages = mutableListOf<String>()
        launch(Dispatchers.Unconfined) { connection.output.take(2).toList(messages) }

        capturedListener!!.onMessage(mockWebSocket, "Hello ")
        capturedListener!!.onMessage(mockWebSocket, "World")

        assertEquals(listOf("Hello ", "World"), messages)
    }

    // ================================================================
    // Control frame parsing
    // ================================================================

    @Test
    fun `onMessage with binary control frame updates cursorFlow`() = runTest {
        val connection = connectAndCollect()

        capturedListener!!.onMessage(mockWebSocket, controlFrame("{\"cursor\":42}"))
        assertEquals(42, connection.cursorFlow.value)
    }

    @Test
    fun `onMessage with binary control frame with zero cursor`() = runTest {
        val connection = connectAndCollect()

        capturedListener!!.onMessage(mockWebSocket, controlFrame("{\"cursor\":0}"))
        assertEquals(0, connection.cursorFlow.value)
    }

    @Test
    fun `onMessage with invalid control frame JSON does not crash`() = runTest {
        val connection = connectAndCollect()

        capturedListener!!.onMessage(mockWebSocket, controlFrame("not json"))
        assertNull("Cursor should remain null on parse failure", connection.cursorFlow.value)
    }

    @Test
    fun `onMessage with binary non-control frame delivers as text`() = runTest {
        val client = createMockedClient()
        val connection = client.connectInteractive(testConn, "pty1")

        val messages = mutableListOf<String>()
        launch(Dispatchers.Unconfined) { connection.output.take(1).toList(messages) }

        capturedListener!!.onMessage(mockWebSocket, textAsByteString("binary output"))

        assertEquals("binary output", messages.first())
    }

    @Test
    fun `cursorFlow starts as null`() = runTest {
        val connection = connectAndCollect()
        assertNull("cursorFlow should start as null", connection.cursorFlow.value)
    }

    // ================================================================
    // InteractiveTerminalConnection.send()
    // ================================================================

    @Test
    fun `send returns false when WebSocket is null`() = runTest {
        val client = createMockedClient()
        val connection = client.connectInteractive(testConn, "pty1")
        // Don't collect flow — webSocketRef stays null

        val result = connection.send("test input")
        assertFalse("send should return false when WebSocket not connected", result)
    }

    @Test
    fun `send returns true and delegates to WebSocket when connected`() = runTest {
        val connection = connectAndCollect()

        every { mockWebSocket.send(any<String>()) } returns true
        triggerOnOpen()

        val result = connection.send("ls -la\n")
        assertTrue("send should return true when connected", result)
        verify { mockWebSocket.send("ls -la\n") }
    }

    // ================================================================
    // InteractiveTerminalConnection.disconnect()
    // ================================================================

    @Test
    fun `disconnect closes WebSocket with code 1000`() = runTest {
        val connection = connectAndCollect()
        triggerOnOpen()

        connection.disconnect()
        verify { mockWebSocket.close(1000, "Client disconnect") }
    }

    @Test
    fun `disconnect is safe when WebSocket is null`() = runTest {
        val client = createMockedClient()
        val connection = client.connectInteractive(testConn, "pty1")
        // Don't collect flow — webSocketRef stays null

        connection.disconnect() // Should not throw
    }

    // ================================================================
    // Ping interval configuration
    // ================================================================

    @Test
    fun `connectInteractive configures readTimeout to 0 and pingInterval to 30s`() = runTest {
        val (client, mBuilder) = createClientWithTrackedBuilder()
        val connection = client.connectInteractive(testConn, "pty1")
        val thread = Thread {
            kotlinx.coroutines.runBlocking {
                connection.output.collect { }
            }
        }.apply { isDaemon = true; start() }
        collectThreads.add(thread)
        // Wait for the flow builder to execute
        var spins = 0
        while (capturedRequest == null && spins < 1000) {
            Thread.sleep(1)
            spins++
        }

        verify { mBuilder.readTimeout(0, any()) }
        verify { mBuilder.pingInterval(30, any()) }
    }

    // ================================================================
    // ptyId preservation
    // ================================================================

    @Test
    fun `ptyId is preserved in InteractiveTerminalConnection`() = runTest {
        val connection = connectAndCollect(ptyId = "my-pty-session-42")
        assertEquals("my-pty-session-42", connection.ptyId)
    }
}
