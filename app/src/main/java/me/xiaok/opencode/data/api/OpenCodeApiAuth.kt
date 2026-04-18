package me.xiaok.opencode.data.api

import android.util.Log
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.xiaok.opencode.domain.model.*

/**
 * Provider auth, permission, and question API methods.
 * Extension functions on [OpenCodeApi].
 */

// === Provider Auth ===

suspend fun OpenCodeApi.getProviderAuthMethods(conn: ServerConnection): JsonElement {
    return client.get(conn.buildUrl("/provider/auth")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.authorizeOAuth(
    conn: ServerConnection,
    providerId: String,
    method: Int,
    inputs: Map<String, String>? = null,
): JsonElement {
    return client.post(conn.buildUrl("/provider/$providerId/oauth/authorize")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(buildMap {
            put("method", method)
            inputs?.let { put("inputs", it) }
        })
    }.body()
}

suspend fun OpenCodeApi.completeOAuth(
    conn: ServerConnection,
    providerId: String,
    method: Int,
    code: String? = null,
): Boolean {
    return client.post(conn.buildUrl("/provider/$providerId/oauth/callback")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(buildMap {
            put("method", method)
            code?.let { put("code", it) }
        })
    }.body()
}

// === Auth ===

suspend fun OpenCodeApi.setAuth(
    conn: ServerConnection,
    providerId: String,
    credentials: JsonElement,
): Boolean {
    return client.put(conn.buildUrl("/auth/$providerId")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(credentials)
    }.body()
}

suspend fun OpenCodeApi.removeAuth(
    conn: ServerConnection,
    providerId: String,
): Boolean {
    return client.delete(conn.buildUrl("/auth/$providerId")) {
        withAuth(conn)
    }.body()
}

// === Permission ===

suspend fun OpenCodeApi.replyPermission(
    conn: ServerConnection,
    permissionId: String,
    reply: PermissionReply,
    directory: String? = null,
) {
    client.post(conn.buildUrl("/permission/$permissionId/reply")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(reply)
    }
}

// === Question ===

suspend fun OpenCodeApi.listQuestions(
    conn: ServerConnection,
    directory: String? = null,
): List<QuestionRequest> {
    return client.get(conn.buildUrl("/question")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.replyQuestion(
    conn: ServerConnection,
    questionId: String,
    answers: List<List<String>>,
    directory: String? = null,
): Boolean {
    val response = client.post(conn.buildUrl("/question/$questionId/reply")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(mapOf("answers" to answers))
    }
    Log.d("OpenCodeApi", "replyQuestion: id=$questionId status=${response.status.value} body=${response.bodyAsText()}")
    return response.status.value in 200..299
}

suspend fun OpenCodeApi.rejectQuestion(
    conn: ServerConnection,
    questionId: String,
    directory: String? = null,
): Boolean {
    val response = client.post(conn.buildUrl("/question/$questionId/reject")) {
        withAuth(conn)
        withDirectory(directory)
    }
    Log.d("OpenCodeApi", "rejectQuestion: id=$questionId status=${response.status.value} body=${response.bodyAsText()}")
    return response.status.value in 200..299
}
