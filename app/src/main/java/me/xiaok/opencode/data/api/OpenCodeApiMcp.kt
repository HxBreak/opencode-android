package me.xiaok.opencode.data.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import me.xiaok.opencode.domain.model.*

/**
 * MCP server management API methods.
 * Extension functions on [OpenCodeApi].
 */

suspend fun OpenCodeApi.listMcpServers(conn: ServerConnection): Map<String, McpStatus> {
    return client.get(conn.buildUrl("/mcp")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.addMcpServer(
    conn: ServerConnection,
    request: McpServerCreateRequest,
): Map<String, McpStatus> {
    return client.post(conn.buildUrl("/mcp")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
}

suspend fun OpenCodeApi.connectMcpServer(
    conn: ServerConnection,
    name: String,
): Boolean {
    return client.post(conn.buildUrl("/mcp/$name/connect")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.disconnectMcpServer(
    conn: ServerConnection,
    name: String,
): Boolean {
    return client.post(conn.buildUrl("/mcp/$name/disconnect")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.startMcpAuth(
    conn: ServerConnection,
    name: String,
): McpAuthUrl {
    return client.post(conn.buildUrl("/mcp/$name/auth")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.completeMcpAuth(
    conn: ServerConnection,
    name: String,
    code: String,
): McpStatus {
    return client.post(conn.buildUrl("/mcp/$name/auth/callback")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(mapOf("code" to code))
    }.body()
}

suspend fun OpenCodeApi.authenticateMcp(
    conn: ServerConnection,
    name: String,
): McpStatus {
    return client.post(conn.buildUrl("/mcp/$name/auth/authenticate")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.removeMcpAuth(
    conn: ServerConnection,
    name: String,
): Boolean {
    return client.delete(conn.buildUrl("/mcp/$name/auth")) {
        withAuth(conn)
    }.body()
}
