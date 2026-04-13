package me.xiaok.opencode.data.api

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.domain.model.*
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * SSE client for OpenCode server events.
 * Connects to GET /global/event for real-time updates.
 *
 * NOT a singleton — created per-server connection, held by ServerRepository.
 * Heartbeat: 10s interval, 15s timeout → auto-reconnect
 * Exponential backoff: aggressive 5s / normal 30s / conservative 60s
 */
class SseClient(
    private val server: ServerConnection,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val eventReducer: EventReducer,
    private val directory: String? = null,
    private val workspace: String? = null,
    private val initialReconnectDelayMs: Long = 5_000L,
    private val maxReconnectDelayMs: Long = 60_000L,
) {
    private var eventSource: EventSource? = null
    private var reconnectAttempts = 0

    // SSE already receives its own dedicated OkHttpClient from DI (@Named("sse"))
    // so we can use it directly — no need to create another derived client.

    /**
     * Connect to the SSE stream and dispatch events to EventReducer.
     * Returns a Flow that emits connection state changes.
     * Automatically reconnects with exponential backoff on failure/close.
     */
    fun connect(): Flow<ConnectionState> = channelFlow {
        val url = buildUrl()

        while (currentCoroutineContext().isActive && !isClosedForSend) {
            val request = Request.Builder()
                .url(url)
                .apply {
                    server.authHeader?.let { header("Authorization", it) }
                    directory?.let { header("x-opencode-directory", it) }
                    workspace?.let { header("x-opencode-workspace", it) }
                    header("Accept", "text/event-stream")
                    header("Cache-Control", "no-cache")
                }
                .build()

            val factory = EventSources.createFactory(okHttpClient)
            val reconnectSignal = CompletableDeferred<Unit>()

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.d(TAG, "SSE connected to ${server.name}")
                    reconnectAttempts = 0
                    eventReducer.processEvent(server.id, SseEvent.ServerConnected)
                    trySend(ConnectionState.CONNECTED)
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val event = parseEvent(data)
                        if (event != null) {
                            Log.d(TAG, "onEvent: type=${event::class.simpleName}, server=${server.name}")
                            eventReducer.processEvent(server.id, event)
                        } else {
                            Log.w(TAG, "onEvent: PARSE NULL for raw type=$type, data=${data.take(300)}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing SSE event: ${data.take(300)}", e)
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    Log.d(TAG, "SSE connection closed for ${server.name}")
                    trySend(ConnectionState.DISCONNECTED)
                    reconnectSignal.complete(Unit)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    Log.e(TAG, "SSE connection failed for ${server.name}: ${t?.message}")
                    trySend(ConnectionState.ERROR(t?.message ?: "Connection failed"))
                    reconnectSignal.complete(Unit)
                }
            }

            eventSource = factory.newEventSource(request, listener)

            // Suspend until the connection closes/fails or the flow is cancelled
            try {
                reconnectSignal.await()
            } catch (_: CancellationException) {
                break
            }

            if (!currentCoroutineContext().isActive) break

            // Exponential backoff before reconnecting
            reconnectAttempts++
            val delayMs = calculateBackoffDelay(reconnectAttempts)
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts)")
            trySend(ConnectionState.RECONNECTING(delayMs))
            delay(delayMs)
        }

        awaitClose {
            disconnect()
        }
    }

    /** Disconnect from the SSE stream */
    fun disconnect() {
        eventSource?.cancel()
        eventSource = null
        reconnectAttempts = 0
    }

    /**
     * Calculate exponential backoff delay.
     * Formula: baseDelay * 2^(attempts-1), capped at maxReconnectDelayMs.
     */
    private fun calculateBackoffDelay(attempts: Int): Long {
        val exponent = minOf(attempts - 1, 5) // Cap exponent at 5 (max 32x base)
        val delay = initialReconnectDelayMs * (1L shl exponent)
        return minOf(delay, maxReconnectDelayMs).coerceAtLeast(initialReconnectDelayMs)
    }

    /**
     * Parse SSE event data into SseEvent.
     *
     * Data format from /global/event:
     * ```json
     * {
     *   "directory": "global",
     *   "payload": {
     *     "type": "session.created",
     *     "properties": { ... }
     *   }
     * }
     * ```
     */
    private fun parseEvent(data: String): SseEvent? {
        if (data.isBlank()) return null

        return try {
            val envelope = json.decodeFromString<SseEnvelope>(data)
            val payload = envelope.payload
            val eventType = payload.type
            val properties = payload.properties

            if (eventType.isBlank() || properties == null) return null

            dispatchEvent(eventType, properties)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSE data: $data", e)
            null
        }
    }

    /**
     * Dispatch event type string to the appropriate SseEvent subclass.
     * Maps server event type strings to sealed class constructors.
     */
    private fun dispatchEvent(type: String, properties: JsonObject): SseEvent? {
        return try {
            when (type) {
                // Server events
                "server.connected" -> SseEvent.ServerConnected
                "server.heartbeat" -> SseEvent.ServerHeartbeat
                "server.instance_disposed", "global.disposed" -> SseEvent.ServerInstanceDisposed

                // Session events
                // Server wraps session data in "info" for created/updated, or "session" for others
                "session.created" -> SseEvent.SessionCreated(
                    json.decodeFromJsonElement<Session>(properties["session"] ?: properties["info"] ?: return null)
                )
                "session.updated" -> SseEvent.SessionUpdated(
                    json.decodeFromJsonElement<Session>(properties["session"] ?: properties["info"] ?: return null)
                )
                "session.deleted" -> SseEvent.SessionDeleted(
                    json.decodeFromJsonElement<Session>(properties["session"] ?: properties["info"] ?: return null)
                )
                "session.status" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val statusObj = properties["status"]?.jsonObject
                    val typeStr = statusObj?.get("type")?.jsonPrimitive?.content
                        ?: properties["status"]?.jsonPrimitive?.content
                        ?: return null
                    val status: SessionStatus = when (typeStr.lowercase()) {
                        "idle" -> SessionStatus.Idle
                        "busy" -> SessionStatus.Busy
                        "retry" -> SessionStatus.Retry(
                            attempt = statusObj?.get("attempt")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            message = statusObj?.get("message")?.jsonPrimitive?.content ?: "",
                            next = statusObj?.get("next")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        )
                        else -> {
                            Log.w(TAG, "Unknown session status: $typeStr, defaulting to Idle")
                            SessionStatus.Idle
                        }
                    }
                    SseEvent.SessionStatusChanged(sessionId, status)
                }
                "session.idle" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    SseEvent.SessionIdle(sessionId)
                }
                "session.diff" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    // Server uses "diff" key, not "diffs"
                    val diffKey = properties["diffs"] ?: properties["diff"] ?: return null
                    val diffs = json.decodeFromJsonElement<List<FileDiff>>(diffKey)
                    SseEvent.SessionDiff(sessionId, diffs)
                }
                "session.error" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content
                    val errorElement = properties["error"]
                    val error = if (errorElement != null) {
                        json.decodeFromJsonElement<ErrorInfo>(errorElement)
                    } else {
                        null
                    }
                    SseEvent.SessionError(sessionId, error)
                }

                // Message events
                // Server sends message data directly in properties: {info:{...}, parts:[...]}
                // NOT nested under a "message" key
                "message.updated" -> SseEvent.MessageUpdated(
                    json.decodeFromJsonElement<Message>(properties)
                )
                "message.removed" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val messageId = properties["messageID"]?.jsonPrimitive?.content ?: return null
                    SseEvent.MessageRemoved(sessionId, messageId)
                }
                "message.part.updated" -> {
                    // Server sends part data flattened in properties alongside metadata fields.
                    // Try properties["part"] first (nested), then fall back to properties itself (flat).
                    val partElement = properties["part"]
                    val part = if (partElement != null) {
                        json.decodeFromJsonElement<Part>(partElement)
                    } else {
                        json.decodeFromJsonElement<Part>(properties)
                    }
                    SseEvent.MessagePartUpdated(part)
                }
                "message.part.delta" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val messageId = properties["messageID"]?.jsonPrimitive?.content ?: return null
                    val partId = properties["partID"]?.jsonPrimitive?.content ?: return null
                    val field = properties["field"]?.jsonPrimitive?.content ?: ""
                    val delta = properties["delta"]?.jsonPrimitive?.content ?: ""
                    SseEvent.MessagePartDelta(sessionId, messageId, partId, field, delta)
                }
                "message.part.removed" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val messageId = properties["messageID"]?.jsonPrimitive?.content ?: return null
                    val partId = properties["partID"]?.jsonPrimitive?.content ?: return null
                    SseEvent.MessagePartRemoved(sessionId, messageId, partId)
                }

                // Interaction events
                "permission.asked" -> SseEvent.PermissionAsked(
                    json.decodeFromJsonElement<PermissionRequest>(properties)
                )
                "permission.replied" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val requestId = properties["requestID"]?.jsonPrimitive?.content ?: return null
                    SseEvent.PermissionReplied(sessionId, requestId)
                }
                "question.asked" -> {
                    val q = json.decodeFromJsonElement<QuestionRequest>(properties)
                    Log.d("SseClient", "question.asked parsed: id=${q.id}, sessionID=${q.sessionID}, questions=${q.questions.size}")
                    SseEvent.QuestionAsked(q)
                }
                "question.replied" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val requestId = properties["requestID"]?.jsonPrimitive?.content ?: return null
                    Log.d(TAG, "question.replied: sessionID=$sessionId, requestID=$requestId")
                    SseEvent.QuestionReplied(sessionId, requestId)
                }
                "question.rejected" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val requestId = properties["requestID"]?.jsonPrimitive?.content ?: return null
                    Log.d(TAG, "question.rejected: sessionID=$sessionId, requestID=$requestId")
                    SseEvent.QuestionRejected(sessionId, requestId)
                }

                // Other events
                "todo.updated" -> {
                    val sessionId = properties["sessionID"]?.jsonPrimitive?.content ?: return null
                    val todos = json.decodeFromJsonElement<List<Todo>>(properties["todos"]!!)
                    SseEvent.TodoUpdated(sessionId, todos)
                }
                "vcs.branch.updated" -> {
                    val branch = properties["branch"]?.jsonPrimitive?.content ?: return null
                    SseEvent.VcsBranchUpdated(branch)
                }
                "lsp.updated" -> SseEvent.LspUpdated
                "project.updated" -> SseEvent.ProjectUpdated(
                    json.decodeFromJsonElement<Project>(properties["project"]!!)
                )

                // PTY events
                "pty.created" -> {
                    val infoElement = properties["info"] ?: return null
                    SseEvent.PtyCreated(json.decodeFromJsonElement<PtyInfo>(infoElement))
                }
                "pty.updated" -> {
                    val infoElement = properties["info"] ?: return null
                    SseEvent.PtyUpdated(json.decodeFromJsonElement<PtyInfo>(infoElement))
                }
                "pty.exited" -> {
                    val id = properties["id"]?.jsonPrimitive?.content ?: return null
                    val exitCode = properties["exitCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    SseEvent.PtyExited(id, exitCode)
                }
                "pty.deleted" -> {
                    val id = properties["id"]?.jsonPrimitive?.content ?: return null
                    SseEvent.PtyDeleted(id)
                }

                // MCP events
                "mcp.browser.open.failed" -> {
                    val mcpName = properties["mcpName"]?.jsonPrimitive?.content ?: return null
                    val url = properties["url"]?.jsonPrimitive?.content ?: return null
                    SseEvent.McpBrowserOpenFailed(mcpName, url)
                }
                "mcp.tools.changed" -> {
                    val server = properties["server"]?.jsonPrimitive?.content ?: return null
                    SseEvent.McpToolsChanged(server)
                }

                // File events
                "file.edited" -> {
                    val file = properties["file"]?.jsonPrimitive?.content ?: return null
                    SseEvent.FileEdited(file)
                }

                // Installation events
                "installation.updated" -> {
                    val version = properties["version"]?.jsonPrimitive?.content ?: return null
                    SseEvent.InstallationUpdated(version)
                }
                "installation.update-available" -> {
                    val version = properties["version"]?.jsonPrimitive?.content ?: return null
                    SseEvent.InstallationUpdateAvailable(version)
                }

                else -> {
                    Log.w(TAG, "Unknown SSE event type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching event type=$type, properties=${properties.toString().take(300)}", e)
            null
        }
    }

    private fun buildUrl(): String {
        val baseUrl = server.baseUrl.trimEnd('/')
        val builder = StringBuilder("$baseUrl/global/event")
        val params = mutableListOf<String>()
        directory?.let { params.add("directory=$it") }
        workspace?.let { params.add("workspace=$it") }
        if (params.isNotEmpty()) {
            builder.append("?")
            builder.append(params.joinToString("&"))
        }
        return builder.toString()
    }

    /** Connection state for SSE stream */
    sealed class ConnectionState {
        data object CONNECTED : ConnectionState()
        data object DISCONNECTED : ConnectionState()
        data class ERROR(val message: String) : ConnectionState()
        data class RECONNECTING(val retryInMs: Long) : ConnectionState()
    }

    companion object {
        private const val TAG = "SseClient"
    }
}
