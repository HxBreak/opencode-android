package me.xiaok.opencode.data.api

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import me.xiaok.opencode.domain.model.ServerConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for PTY terminal connections.
 * Connects to /pty/{id}/connect for bidirectional terminal I/O.
 *
 * Protocol:
 * - Client → Server: plain text strings written to PTY stdin
 * - Server → Client: history buffer chunks, control frame 0x00 + {"cursor": N}, then real-time output
 *
 * NOT a singleton — one instance per active terminal connection.
 */
class WsClient(
    private val okHttpClient: OkHttpClient,
) {
    /**
     * Connect to a PTY session and return a connection that can send data.
     * Keeps the WebSocket reference for bidirectional communication.
     *
     * @param conn Server connection info (baseUrl, authHeader)
     * @param ptyId PTY session ID from createPty API
     * @param cursor History cursor: 0 = all history, -1 = new data only
     * @param directory Optional directory for server-side routing (query param + header)
     * @param workspace Optional workspace header for server-side routing
     */
    fun connectInteractive(
        conn: ServerConnection,
        ptyId: String,
        cursor: Int = -1,
        directory: String? = null,
        workspace: String? = null,
    ): InteractiveTerminalConnection {
        val wsUrl = buildWsUrl(conn.baseUrl, ptyId, cursor, directory)
        val isConnected = MutableStateFlow(false)
        val cursorFlow = MutableStateFlow<Int?>(null)

        var webSocketRef: WebSocket? = null

        val outputFlow = callbackFlow {
            val request = Request.Builder()
                .url(wsUrl)
                .apply {
                    conn.authHeader?.let { header("Authorization", it) }
                    directory?.let { header("x-opencode-directory", it) }
                    workspace?.let { header("x-opencode-workspace", it) }
                }
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected to pty/$ptyId")
                    webSocketRef = webSocket
                    isConnected.value = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    trySend(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    if (bytes.size > 0 && bytes[0] == 0x00.toByte()) {
                        val jsonStr = bytes.substring(1).utf8()
                        Log.d(TAG, "Control frame: $jsonStr")
                        try {
                            val frame = Json.decodeFromString<ControlFrame>(jsonStr)
                            cursorFlow.value = frame.cursor
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse control frame: ${e.message}")
                        }
                    } else {
                        trySend(bytes.utf8())
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                    isConnected.value = false
                    webSocketRef = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    isConnected.value = false
                    webSocketRef = null
                    close(t)
                }
            }

            val wsClient = okHttpClient.newBuilder()
                .readTimeout(0, TimeUnit.MINUTES)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

            wsClient.newWebSocket(request, listener)

            awaitClose {
                webSocketRef?.close(1000, "Client disconnect")
                webSocketRef = null
                isConnected.value = false
            }
        }

        return InteractiveTerminalConnection(
            output = outputFlow,
            isConnected = isConnected,
            cursorFlow = cursorFlow,
            ptyId = ptyId,
            getWebSocket = { webSocketRef }
        )
    }

    private fun buildWsUrl(baseUrl: String, ptyId: String, cursor: Int, directory: String? = null): String {
        val wsBase = baseUrl
            .trimEnd('/')
            .replace(Regex("^http(?=s?://)"), "ws")
        val params = mutableListOf("cursor=$cursor")
        directory?.let { params.add("directory=${java.net.URLEncoder.encode(it, "UTF-8")}") }
        return "$wsBase/pty/$ptyId/connect?${params.joinToString("&")}"
    }

    /**
     * Interactive terminal connection with bidirectional communication.
     * Holds a reference to the underlying WebSocket for sending data.
     *
     * Note: Resize must be done via REST API PUT /pty/{ptyId} with {size: {rows, cols}}.
     * The WebSocket protocol only supports plain text stdin input.
     */
    class InteractiveTerminalConnection(
        val output: Flow<String>,
        val isConnected: StateFlow<Boolean>,
        val cursorFlow: StateFlow<Int?>,
        val ptyId: String,
        private val getWebSocket: () -> WebSocket?,
    ) {
        /**
         * Send keyboard input to the PTY stdin.
         * Per the PTY protocol, the server interprets all text frames as stdin input.
         * @return true if the message was enqueued, false if the WebSocket is not connected
         */
        fun send(input: String): Boolean {
            val ws = getWebSocket() ?: return false
            return ws.send(input)
        }

        /**
         * Close the WebSocket connection.
         * The output Flow will complete after this call.
         */
        fun disconnect() {
            getWebSocket()?.close(1000, "Client disconnect")
        }
    }

    /**
     * Control frame from server after history replay.
     * Format: 0x00 + {"cursor": N}
     */
    @kotlinx.serialization.Serializable
    private data class ControlFrame(
        val cursor: Int,
    )

    companion object {
        private const val TAG = "WsClient"
    }
}
