package me.xiaok.opencode.data.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonElement
import me.xiaok.opencode.domain.model.*

/**
 * File, search, and VCS API methods.
 * Extension functions on [OpenCodeApi].
 */

// === File ===

suspend fun OpenCodeApi.listFiles(
    conn: ServerConnection,
    path: String = ".",
    workspace: String? = null,
    directory: String? = null,
): List<FileNode> {
    return client.get(conn.buildUrl("/file")) {
        withAuth(conn)
        withDirectory(directory)
        withWorkspace(workspace)
        parameter("path", path)
    }.body()
}

suspend fun OpenCodeApi.getFileContent(
    conn: ServerConnection,
    path: String,
    workspace: String? = null,
    directory: String? = null,
): FileContent {
    return client.get(conn.buildUrl("/file/content")) {
        withAuth(conn)
        withDirectory(directory)
        withWorkspace(workspace)
        parameter("path", path)
    }.body()
}

suspend fun OpenCodeApi.getFileStatuses(
    conn: ServerConnection,
    workspace: String? = null,
    directory: String? = null,
): List<FileStatus> {
    return client.get(conn.buildUrl("/file/status")) {
        withAuth(conn)
        withDirectory(directory)
        withWorkspace(workspace)
    }.body()
}

// === Find ===

suspend fun OpenCodeApi.textSearch(
    conn: ServerConnection,
    pattern: String,
    workspace: String? = null,
    directory: String? = null,
): List<JsonElement> {
    return client.get(conn.buildUrl("/find")) {
        withAuth(conn)
        withDirectory(directory)
        withWorkspace(workspace)
        parameter("pattern", pattern)
    }.body()
}

suspend fun OpenCodeApi.fileSearch(
    conn: ServerConnection,
    query: String,
    dirs: String? = null,
    type: String? = null,
    limit: Int? = null,
    workspace: String? = null,
    directory: String? = null,
): List<String> {
    return client.get(conn.buildUrl("/find/file")) {
        withAuth(conn)
        withDirectory(directory)
        withWorkspace(workspace)
        parameter("query", query)
        dirs?.let { parameter("dirs", it) }
        type?.let { parameter("type", it) }
        limit?.let { parameter("limit", it) }
    }.body()
}

suspend fun OpenCodeApi.symbolSearch(
    conn: ServerConnection,
    query: String,
): List<JsonElement> {
    return client.get(conn.buildUrl("/find/symbol")) {
        withAuth(conn)
        parameter("query", query)
    }.body()
}
