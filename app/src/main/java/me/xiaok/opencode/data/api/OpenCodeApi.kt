package me.xiaok.opencode.data.api

import io.ktor.client.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless REST API client for OpenCode server.
 * All methods accept ServerConnection for per-request authentication.
 * No mutable state — safe to use as @Singleton.
 *
 * Public API methods are defined as extension functions in co-located files:
 * - OpenCodeApiGlobal.kt      — health, config, misc queries
 * - OpenCodeApiSession.kt     — session CRUD, messages, operations
 * - OpenCodeApiFile.kt        — file operations, search
 * - OpenCodeApiAuth.kt        — auth, permissions, questions
 * - OpenCodeApiPty.kt         — PTY terminal management
 * - OpenCodeApiMcp.kt         — MCP server management
 * - OpenCodeApiExperimental.kt — experimental features
 *
 * Shared helpers: ApiHelpers.kt
 * Data models: ApiModels.kt
 */
@Singleton
class OpenCodeApi @Inject constructor(
    internal val client: HttpClient,
    internal val json: Json,
)
