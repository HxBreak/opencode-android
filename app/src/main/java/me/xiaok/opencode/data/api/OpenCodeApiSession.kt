package me.xiaok.opencode.data.api

import android.util.Log
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.xiaok.opencode.domain.model.*

/**
 * Session-related API methods — CRUD, messages, status, operations.
 * Extension functions on [OpenCodeApi].
 */

// === Session CRUD ===

suspend fun OpenCodeApi.listSessions(
    conn: ServerConnection,
    directory: String? = null,
    workspace: String? = null,
    limit: Int? = null,
    search: String? = null,
    roots: Boolean? = null,
): List<Session> {
    return client.get(conn.buildUrl("/session")) {
        withAuth(conn)
        directory?.let { parameter("directory", it) }
        workspace?.let { parameter("workspace", it) }
        limit?.let { parameter("limit", it) }
        search?.let { parameter("search", it) }
        roots?.let { parameter("roots", it) }
    }.body()
}

suspend fun OpenCodeApi.createSession(
    conn: ServerConnection,
    directory: String? = null,
    workspace: String? = null,
    title: String? = null,
): Session {
    return client.post(conn.buildUrl("/session")) {
        withAuth(conn)
        directory?.let { parameter("directory", it) }
        workspace?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(buildMap {
            title?.let { put("title", it) }
        })
    }.body()
}

suspend fun OpenCodeApi.getSession(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): Session {
    return client.get(conn.buildUrl("/session/$sessionId")) {
        withAuth(conn)
        directory?.let { parameter("directory", it) }
    }.body()
}

suspend fun OpenCodeApi.deleteSession(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): Boolean {
    return client.delete(conn.buildUrl("/session/$sessionId")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.updateSession(
    conn: ServerConnection,
    sessionId: String,
    title: String? = null,
    archived: Long? = null,
    unarchive: Boolean = false,
    directory: String? = null,
): Session {
    val body = buildJsonObject {
        title?.let { put("title", it) }
        if (archived != null) {
            put("time", buildJsonObject { put("archived", archived) })
        } else if (unarchive) {
            put("time", buildJsonObject { put("archived", JsonNull) })
        }
    }
    return client.patch(conn.buildUrl("/session/$sessionId")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()
}

suspend fun OpenCodeApi.abortSession(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): Boolean {
    return client.post(conn.buildUrl("/session/$sessionId/abort")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

// === Messages ===

/**
 * List messages for a session.
 *
 * Handles three response shapes:
 * - 200 + JSON array → normal message list
 * - 200 + JSON object with "error" → server-side validation error (e.g. invalid cursor)
 * - non-2xx → HTTP error
 *
 * Returns [MessagesPage] with the decoded messages and the `X-Next-Cursor` header value
 * (if present) for pagination.
 */
suspend fun OpenCodeApi.listMessages(
    conn: ServerConnection,
    sessionId: String,
    limit: Int? = null,
    before: String? = null,
    directory: String? = null,
): MessagesPage {
    val response: HttpResponse = client.get(conn.buildUrl("/session/$sessionId/message")) {
        withAuth(conn)
        directory?.let { parameter("directory", it) }
        limit?.let { parameter("limit", it) }
        before?.let { parameter("before", it) }
    }

    if (!response.status.isSuccess()) {
        val body = response.bodyAsText()
        Log.e("OpenCodeApi", "listMessages: HTTP ${response.status.value} body=$body")
        throw IllegalStateException("listMessages failed: ${response.status.value} - $body")
    }

    val element = response.body<JsonElement>()
    if (element is JsonObject) {
        val errorMsg = try {
            val jsonObj = org.json.JSONObject(element.toString())
            val errors = jsonObj.optJSONArray("error")
            if (errors != null && errors.length() > 0) {
                errors.getJSONObject(0).optString("message", element.toString())
            } else {
                element.toString()
            }
        } catch (_: Exception) {
            element.toString()
        }
        Log.e("OpenCodeApi", "listMessages: server returned error object: $errorMsg")
        throw IllegalStateException("listMessages: $errorMsg")
    }

    val messages = json.decodeFromJsonElement<List<Message>>(element)
    val nextCursor = response.headers["X-Next-Cursor"]
    return MessagesPage(messages = messages, nextCursor = nextCursor)
}

suspend fun OpenCodeApi.promptAsync(
    conn: ServerConnection,
    sessionId: String,
    parts: List<Map<String, Any>>? = null,
    agent: String? = null,
    model: ModelRef? = null,
    variant: String? = null,
    directory: String? = null,
) {
    val url = conn.buildUrl("/session/$sessionId/prompt_async")

    val bodyJson = kotlinx.serialization.json.buildJsonObject {
        agent?.let { put("agent", it) }
        model?.let {
            put("model", kotlinx.serialization.json.buildJsonObject {
                put("providerID", it.providerID)
                put("modelID", it.modelID)
            })
        }
        variant?.let { put("variant", it) }
        parts?.let { partsList ->
            put("parts", kotlinx.serialization.json.JsonArray(partsList.map { partMap ->
                anyMapToJson(partMap)
            }))
        }
    }
    Log.d("OpenCodeApi", "promptAsync: POST $url body=$bodyJson")

    val response: HttpResponse = client.post(url) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(bodyJson)
    }

    if (!response.status.isSuccess()) {
        val errorBody = response.bodyAsText()
        Log.e("OpenCodeApi", "promptAsync: FAILED status=${response.status.value} body=$errorBody")
        throw IllegalStateException("promptAsync failed: ${response.status.value} - $errorBody")
    }

    Log.d("OpenCodeApi", "promptAsync: response received, status=${response.status.value}")
}

// === Session Status ===

/**
 * Retrieve the current status of all sessions.
 *
 * Server returns `Record<sessionId, SessionStatus.Info>` where each value is
 * a JSON object like `{"type":"busy"}` or `{"type":"retry","attempt":1,"message":"...","next":123}`.
 */
suspend fun OpenCodeApi.getSessionStatuses(
    conn: ServerConnection,
    directory: String? = null,
): Map<String, SessionStatus> {
    val response: JsonObject = client.get(conn.buildUrl("/session/status")) {
        withAuth(conn)
        directory?.let { parameter("directory", it) }
    }.body()
    return response.mapValues { (_, element) ->
        parseSessionStatus(element)
    }
}

// === Diff ===

suspend fun OpenCodeApi.getSessionDiff(
    conn: ServerConnection,
    sessionId: String,
    messageId: String? = null,
    directory: String? = null,
    workspace: String? = null,
): List<FileDiff> {
    return client.get(conn.buildUrl("/session/$sessionId/diff")) {
        withAuth(conn)
        withDirectory(directory)
        withWorkspace(workspace)
        messageId?.let { parameter("messageID", it) }
    }.body()
}

// === Todo ===

suspend fun OpenCodeApi.getSessionTodos(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): List<Todo> {
    return client.get(conn.buildUrl("/session/$sessionId/todo")) {
        withAuth(conn)
        directory?.let { parameter("directory", it) }
    }.body()
}

// === Session operations ===

suspend fun OpenCodeApi.forkSession(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    directory: String? = null,
): Session {
    return client.post(conn.buildUrl("/session/$sessionId/fork")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(mapOf("messageID" to messageId))
    }.body()
}

suspend fun OpenCodeApi.shareSession(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): SessionShare {
    return client.post(conn.buildUrl("/session/$sessionId/share")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.unshareSession(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): Boolean {
    return client.delete(conn.buildUrl("/session/$sessionId/share")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.revertSession(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    directory: String? = null,
) {
    client.post(conn.buildUrl("/session/$sessionId/revert")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(mapOf("messageID" to messageId))
    }
}

suspend fun OpenCodeApi.summarizeSession(
    conn: ServerConnection,
    sessionId: String,
    providerId: String,
    modelId: String,
    directory: String? = null,
): Boolean {
    Log.d("OpenCodeApi", "summarizeSession: POST /session/$sessionId/summarize provider=$providerId model=$modelId")
    return client.post(conn.buildUrl("/session/$sessionId/summarize")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(mapOf(
            "providerID" to providerId,
            "modelID" to modelId,
        ))
    }.body<Boolean>().also { result ->
        Log.d("OpenCodeApi", "summarizeSession: result=$result")
    }
}

suspend fun OpenCodeApi.unrevertSession(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): Session {
    return client.post(conn.buildUrl("/session/$sessionId/unrevert")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

// === Session Init & Children ===

suspend fun OpenCodeApi.initSession(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): Session {
    return client.post(conn.buildUrl("/session/$sessionId/init")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.getSessionChildren(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): List<Session> {
    return client.get(conn.buildUrl("/session/$sessionId/children")) {
        withAuth(conn)
        directory?.let { parameter("directory", it) }
    }.body()
}

// === Message Operations ===

suspend fun OpenCodeApi.deleteMessage(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    directory: String? = null,
): Boolean {
    return client.delete(conn.buildUrl("/session/$sessionId/message/$messageId")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.patchMessagePart(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    partId: String,
    update: Map<String, kotlinx.serialization.json.JsonElement>,
    directory: String? = null,
): Part {
    return client.patch(conn.buildUrl("/session/$sessionId/message/$messageId/part/$partId")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(update)
    }.body()
}

suspend fun OpenCodeApi.deleteMessagePart(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    partId: String,
    directory: String? = null,
): Boolean {
    return client.delete(conn.buildUrl("/session/$sessionId/message/$messageId/part/$partId")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

// === Shell & Command ===

suspend fun OpenCodeApi.runShell(
    conn: ServerConnection,
    sessionId: String,
    command: String,
    agent: String,
    directory: String? = null,
) {
    client.post(conn.buildUrl("/session/$sessionId/shell")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(buildMap {
            put("command", command)
            put("agent", agent)
        })
    }
}

suspend fun OpenCodeApi.sendCommand(
    conn: ServerConnection,
    sessionId: String,
    command: String,
    arguments: String? = null,
    directory: String? = null,
) {
    client.post(conn.buildUrl("/session/$sessionId/command")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(buildMap {
            put("command", command)
            arguments?.let { put("arguments", it) }
        })
    }
}
