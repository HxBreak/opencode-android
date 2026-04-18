package me.xiaok.opencode.data.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import me.xiaok.opencode.domain.model.*

/**
 * PTY (terminal) API methods.
 * Extension functions on [OpenCodeApi].
 */

suspend fun OpenCodeApi.listPtys(
    conn: ServerConnection,
    directory: String? = null,
): List<PtyInfo> {
    return client.get(conn.buildUrl("/pty")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.createPty(
    conn: ServerConnection,
    request: PtyCreateRequest = PtyCreateRequest(),
    directory: String? = null,
): PtyInfo {
    return client.post(conn.buildUrl("/pty")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        // Filter out null fields to avoid server validation errors (e.g. cwd:null → 400)
        setBody(buildMap<String, Any?> {
            request.command?.let { put("command", it) }
            request.args?.let { put("args", it) }
            request.cwd?.let { put("cwd", it) }
            request.title?.let { put("title", it) }
            request.env?.let { put("env", it) }
        })
    }.body()
}

suspend fun OpenCodeApi.getPty(
    conn: ServerConnection,
    ptyId: String,
    directory: String? = null,
): PtyInfo {
    return client.get(conn.buildUrl("/pty/$ptyId")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

suspend fun OpenCodeApi.updatePty(
    conn: ServerConnection,
    ptyId: String,
    request: PtyUpdateRequest,
    directory: String? = null,
): PtyInfo {
    return client.put(conn.buildUrl("/pty/$ptyId")) {
        withAuth(conn)
        withDirectory(directory)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}

suspend fun OpenCodeApi.deletePty(
    conn: ServerConnection,
    ptyId: String,
    directory: String? = null,
): Boolean {
    return client.delete(conn.buildUrl("/pty/$ptyId")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}
