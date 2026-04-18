package me.xiaok.opencode.data.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement
import me.xiaok.opencode.domain.model.*

/**
 * Global/singleton API methods — health, config, misc queries.
 * Extension functions on [OpenCodeApi].
 */

// === Global ===

suspend fun OpenCodeApi.health(conn: ServerConnection): HealthResponse {
    return client.get(conn.buildUrl("/global/health")) {
        withAuth(conn)
    }.body<HealthResponse>()
}

suspend fun OpenCodeApi.getConfig(conn: ServerConnection): JsonElement {
    return client.get(conn.buildUrl("/global/config")) {
        withAuth(conn)
    }.body()
}

// === Project ===

suspend fun OpenCodeApi.listProjects(conn: ServerConnection): List<Project> {
    return client.get(conn.buildUrl("/project")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.getCurrentProject(conn: ServerConnection): Project {
    return client.get(conn.buildUrl("/project/current")) {
        withAuth(conn)
    }.body()
}

// === Provider ===

suspend fun OpenCodeApi.getProviders(conn: ServerConnection): ProviderList {
    return client.get(conn.buildUrl("/provider")) {
        withAuth(conn)
    }.body()
}

// === VCS ===

suspend fun OpenCodeApi.getVcsInfo(conn: ServerConnection): VcsInfo {
    return client.get(conn.buildUrl("/vcs")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.getVcsBranch(conn: ServerConnection): VcsInfo {
    return client.get(conn.buildUrl("/vcs")) {
        withAuth(conn)
    }.body()
}

// === Agent ===

suspend fun OpenCodeApi.getAgents(conn: ServerConnection): List<AgentConfig> {
    return client.get(conn.buildUrl("/agent")) {
        withAuth(conn)
    }.body()
}

// === Commands ===

suspend fun OpenCodeApi.getCommands(conn: ServerConnection): List<CommandInfo> {
    return client.get(conn.buildUrl("/command")) {
        withAuth(conn)
    }.body()
}

// === Config (per-project) ===

suspend fun OpenCodeApi.getProjectConfig(conn: ServerConnection): JsonElement {
    return client.get(conn.buildUrl("/config")) {
        withAuth(conn)
    }.body()
}

suspend fun OpenCodeApi.patchProjectConfig(
    conn: ServerConnection,
    config: JsonElement,
): JsonElement {
    return client.patch(conn.buildUrl("/config")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(config)
    }.body()
}

suspend fun OpenCodeApi.getConfigProviders(conn: ServerConnection): ConfigProviders {
    return client.get(conn.buildUrl("/config/providers")) {
        withAuth(conn)
    }.body()
}

// === Skill ===

suspend fun OpenCodeApi.getSkills(conn: ServerConnection): List<SkillInfo> {
    return client.get(conn.buildUrl("/skill")) {
        withAuth(conn)
    }.body()
}

// === Path ===

suspend fun OpenCodeApi.getPathInfo(conn: ServerConnection): PathInfo {
    return client.get(conn.buildUrl("/path")) {
        withAuth(conn)
    }.body()
}

// === Formatter ===

suspend fun OpenCodeApi.getFormatters(conn: ServerConnection): List<FormatterInfo> {
    return client.get(conn.buildUrl("/formatter")) {
        withAuth(conn)
    }.body()
}

// === Instance ===

suspend fun OpenCodeApi.disposeInstance(
    conn: ServerConnection,
    directory: String? = null,
): Boolean {
    return client.post(conn.buildUrl("/instance/dispose")) {
        withAuth(conn)
        withDirectory(directory)
    }.body()
}

// === Log ===

suspend fun OpenCodeApi.sendLog(
    conn: ServerConnection,
    entry: LogEntry,
): Boolean {
    return client.post(conn.buildUrl("/log")) {
        withAuth(conn)
        contentType(ContentType.Application.Json)
        setBody(entry)
    }.body()
}

// === LSP ===

suspend fun OpenCodeApi.getLspServers(conn: ServerConnection): List<LspInfo> {
    return client.get(conn.buildUrl("/lsp")) {
        withAuth(conn)
    }.body()
}
