package me.xiaok.opencode.data.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement
import me.xiaok.opencode.domain.model.*

/**
 * Experimental API methods.
 * Extension functions on [OpenCodeApi].
 */

suspend fun OpenCodeApi.listWorkspaces(conn: ServerConnection): JsonElement {
    return client.get(conn.buildUrl("/experimental/workspace")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.createWorkspace(
    conn: ServerConnection,
    request: WorkspaceCreateRequest,
): JsonElement {
    return client.post(conn.buildUrl("/experimental/workspace")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}

suspend fun OpenCodeApi.deleteWorkspace(
    conn: ServerConnection,
    workspaceId: String,
): Boolean {
    return client.delete(conn.buildUrl("/experimental/workspace/$workspaceId")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.listWorktrees(conn: ServerConnection): List<String> {
    return client.get(conn.buildUrl("/experimental/worktree")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.createWorktree(
    conn: ServerConnection,
    request: WorktreeCreateRequest,
): JsonElement {
    return client.post(conn.buildUrl("/experimental/worktree")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}

suspend fun OpenCodeApi.deleteWorktree(
    conn: ServerConnection,
    request: WorktreeDeleteRequest,
): Boolean {
    return client.delete(conn.buildUrl("/experimental/worktree")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}

suspend fun OpenCodeApi.resetWorktree(
    conn: ServerConnection,
    request: WorktreeResetRequest,
) {
    client.post(conn.buildUrl("/experimental/worktree/reset")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(request)
    }
}

suspend fun OpenCodeApi.getExperimentalResources(conn: ServerConnection): JsonElement {
    return client.get(conn.buildUrl("/experimental/resource")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.getExperimentalTools(
    conn: ServerConnection,
    provider: String,
    model: String,
): JsonElement {
    return client.get(conn.buildUrl("/experimental/tool")) {
        withAuth(conn)
        parameter("provider", provider)
        parameter("model", model)
    }.body()
}

suspend fun OpenCodeApi.getExperimentalToolIds(conn: ServerConnection): List<String> {
    return client.get(conn.buildUrl("/experimental/tool/ids")) {
        withAuth(conn)
    }.body()
}
