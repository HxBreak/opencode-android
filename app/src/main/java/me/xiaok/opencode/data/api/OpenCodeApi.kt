package me.xiaok.opencode.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.xiaok.opencode.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless REST API client for OpenCode server.
 * All methods accept ServerConnection for per-request authentication.
 * No mutable state — safe to use as @Singleton.
 */
@Singleton
class OpenCodeApi @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    // === Global ===

    suspend fun health(conn: ServerConnection): HealthResponse {
        return client.get(conn.buildUrl("/global/health")) {
            withAuth(conn)
        }.body<HealthResponse>()
    }

    suspend fun getConfig(conn: ServerConnection): JsonElement {
        return client.get(conn.buildUrl("/global/config")) {
            withAuth(conn)
        }.body()
    }

    // === Session ===

    suspend fun listSessions(
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

    suspend fun createSession(
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

    suspend fun getSession(
        conn: ServerConnection,
        sessionId: String,
    ): Session {
        return client.get(conn.buildUrl("/session/$sessionId")) {
            withAuth(conn)
        }.body()
    }

    suspend fun deleteSession(
        conn: ServerConnection,
        sessionId: String,
    ): Boolean {
        return client.delete(conn.buildUrl("/session/$sessionId")) {
            withAuth(conn)
        }.body()
    }

    suspend fun updateSession(
        conn: ServerConnection,
        sessionId: String,
        title: String? = null,
        archived: Long? = null,
        unarchive: Boolean = false,
    ): Session {
        val body = buildMap {
            title?.let { put("title", it) }
            if (archived != null) {
                put("time", mapOf("archived" to archived))
            } else if (unarchive) {
                put("time", mapOf<String, Any?>("archived" to null))
            }
        }
        return client.patch(conn.buildUrl("/session/$sessionId")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    suspend fun abortSession(
        conn: ServerConnection,
        sessionId: String,
    ): Boolean {
        return client.post(conn.buildUrl("/session/$sessionId/abort")) {
            withAuth(conn)
        }.body()
    }

    // === Messages ===

    data class MessagesPage(
        val messages: List<Message>,
        val nextCursor: String?,
    )

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
    suspend fun listMessages(
        conn: ServerConnection,
        sessionId: String,
        limit: Int? = null,
        before: String? = null,
    ): MessagesPage {
        val response: HttpResponse = client.get(conn.buildUrl("/session/$sessionId/message")) {
            withAuth(conn)
            limit?.let { parameter("limit", it) }
            before?.let { parameter("before", it) }
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            Log.e(TAG, "listMessages: HTTP ${response.status.value} body=$body")
            throw IllegalStateException("listMessages failed: ${response.status.value} - $body")
        }

        val element = response.body<JsonElement>()
        if (element is JsonObject) {
            // Server returned an error object instead of an array
            // (e.g. Zod validation error: {"data":...,"error":[...],"success":false})
            val errorMsg = try {
                val json = org.json.JSONObject(element.toString())
                val errors = json.optJSONArray("error")
                if (errors != null && errors.length() > 0) {
                    errors.getJSONObject(0).optString("message", element.toString())
                } else {
                    element.toString()
                }
            } catch (_: Exception) {
                element.toString()
            }
            Log.e(TAG, "listMessages: server returned error object: $errorMsg")
            throw IllegalStateException("listMessages: $errorMsg")
        }

        val messages = json.decodeFromJsonElement<List<Message>>(element)
        val nextCursor = response.headers["X-Next-Cursor"]
        return MessagesPage(messages = messages, nextCursor = nextCursor)
    }

    suspend fun promptAsync(
        conn: ServerConnection,
        sessionId: String,
        parts: List<Map<String, String>>? = null,
        agent: String? = null,
        model: ModelRef? = null,
        directory: String? = null,
    ) {
        val url = conn.buildUrl("/session/$sessionId/prompt_async")
        val body = SendMessageRequest(
            agent = agent,
            model = model,
            parts = parts,
        )
        Log.d(TAG, "promptAsync: POST $url body=$body")

        val response: HttpResponse = client.post(url) {
            withAuth(conn)
            directory?.let { parameter("directory", it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e(TAG, "promptAsync: FAILED status=${response.status.value} body=$errorBody")
            throw IllegalStateException("promptAsync failed: ${response.status.value} - $errorBody")
        }

        Log.d(TAG, "promptAsync: response received, status=${response.status.value}")
    }

    // === Session Status ===

    suspend fun getSessionStatuses(conn: ServerConnection): Map<String, String> {
        return client.get(conn.buildUrl("/session/status")) {
            withAuth(conn)
        }.body()
    }

    // === Project ===

    suspend fun listProjects(conn: ServerConnection): List<Project> {
        return client.get(conn.buildUrl("/project")) {
            withAuth(conn)
        }.body()
    }

    suspend fun getCurrentProject(conn: ServerConnection): Project {
        return client.get(conn.buildUrl("/project/current")) {
            withAuth(conn)
        }.body()
    }

    // === Provider ===

    suspend fun getProviders(conn: ServerConnection): ProviderList {
        return client.get(conn.buildUrl("/provider")) {
            withAuth(conn)
        }.body()
    }

    // === File ===

    suspend fun listFiles(
        conn: ServerConnection,
        path: String = ".",
    ): List<FileNode> {
        return client.get(conn.buildUrl("/file")) {
            withAuth(conn)
            parameter("path", path)
        }.body()
    }

    suspend fun getFileContent(
        conn: ServerConnection,
        path: String,
    ): String {
        return client.get(conn.buildUrl("/file/content")) {
            withAuth(conn)
            parameter("path", path)
        }.body()
    }

    suspend fun getFileStatuses(conn: ServerConnection): List<FileStatus> {
        return client.get(conn.buildUrl("/file/status")) {
            withAuth(conn)
        }.body()
    }

    // === Find ===

    suspend fun textSearch(
        conn: ServerConnection,
        pattern: String,
    ): List<JsonElement> {
        return client.get(conn.buildUrl("/find")) {
            withAuth(conn)
            parameter("pattern", pattern)
        }.body()
    }

    suspend fun fileSearch(
        conn: ServerConnection,
        query: String,
        dirs: String? = null,
        type: String? = null,
        limit: Int? = null,
    ): List<String> {
        return client.get(conn.buildUrl("/find/file")) {
            withAuth(conn)
            parameter("query", query)
            dirs?.let { parameter("dirs", it) }
            type?.let { parameter("type", it) }
            limit?.let { parameter("limit", it) }
        }.body()
    }

    suspend fun symbolSearch(
        conn: ServerConnection,
        query: String,
    ): List<JsonElement> {
        return client.get(conn.buildUrl("/find/symbol")) {
            withAuth(conn)
            parameter("query", query)
        }.body()
    }

    // === VCS ===

    suspend fun getVcsInfo(conn: ServerConnection): VcsInfo {
        return client.get(conn.buildUrl("/vcs")) {
            withAuth(conn)
        }.body()
    }

    // === Provider Auth ===

    suspend fun getProviderAuthMethods(conn: ServerConnection): JsonElement {
        return client.get(conn.buildUrl("/provider/auth")) {
            withAuth(conn)
        }.body()
    }

    suspend fun authorizeOAuth(
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

    suspend fun completeOAuth(
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

    suspend fun setAuth(
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

    suspend fun removeAuth(
        conn: ServerConnection,
        providerId: String,
    ): Boolean {
        return client.delete(conn.buildUrl("/auth/$providerId")) {
            withAuth(conn)
        }.body()
    }

    // === Permission ===

    suspend fun replyPermission(
        conn: ServerConnection,
        permissionId: String,
        reply: PermissionReply,
    ) {
        client.post(conn.buildUrl("/permission/$permissionId/reply")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(reply)
        }
    }

    // === Question ===

    suspend fun listQuestions(
        conn: ServerConnection,
    ): List<QuestionRequest> {
        return client.get(conn.buildUrl("/question")) {
            withAuth(conn)
        }.body()
    }

    suspend fun replyQuestion(
        conn: ServerConnection,
        questionId: String,
        answers: List<List<String>>,
    ): Boolean {
        val response = client.post(conn.buildUrl("/question/$questionId/reply")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(mapOf("answers" to answers))
        }
        return response.status.value in 200..299
    }

    suspend fun rejectQuestion(
        conn: ServerConnection,
        questionId: String,
    ): Boolean {
        val response = client.post(conn.buildUrl("/question/$questionId/reject")) {
            withAuth(conn)
        }
        return response.status.value in 200..299
    }

    // === Diff ===

    suspend fun getSessionDiff(
        conn: ServerConnection,
        sessionId: String,
        messageId: String? = null,
    ): List<FileDiff> {
        return client.get(conn.buildUrl("/session/$sessionId/diff")) {
            withAuth(conn)
            messageId?.let { parameter("messageID", it) }
        }.body()
    }

    // === Todo ===

    suspend fun getSessionTodos(
        conn: ServerConnection,
        sessionId: String,
    ): List<Todo> {
        return client.get(conn.buildUrl("/session/$sessionId/todo")) {
            withAuth(conn)
        }.body()
    }

    // === Session operations ===

    suspend fun forkSession(
        conn: ServerConnection,
        sessionId: String,
        messageId: String,
        directory: String? = null,
    ): Session {
        return client.post(conn.buildUrl("/session/$sessionId/fork")) {
            withAuth(conn)
            directory?.let { parameter("directory", it) }
            contentType(ContentType.Application.Json)
            setBody(mapOf("messageID" to messageId))
        }.body()
    }

    suspend fun shareSession(
        conn: ServerConnection,
        sessionId: String,
    ): SessionShare {
        return client.post(conn.buildUrl("/session/$sessionId/share")) {
            withAuth(conn)
        }.body()
    }

    suspend fun unshareSession(
        conn: ServerConnection,
        sessionId: String,
    ): Boolean {
        return client.delete(conn.buildUrl("/session/$sessionId/share")) {
            withAuth(conn)
        }.body()
    }

    suspend fun revertSession(
        conn: ServerConnection,
        sessionId: String,
        messageId: String,
    ) {
        client.post(conn.buildUrl("/session/$sessionId/revert")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(mapOf("messageID" to messageId))
        }
    }

    suspend fun summarizeSession(
        conn: ServerConnection,
        sessionId: String,
        providerId: String? = null,
        modelId: String? = null,
    ): Boolean {
        return client.post(conn.buildUrl("/session/$sessionId/summarize")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(buildMap {
                providerId?.let { put("providerID", it) }
                modelId?.let { put("modelID", it) }
            })
        }.body()
    }

    suspend fun unrevertSession(
        conn: ServerConnection,
        sessionId: String,
    ): Session {
        return client.post(conn.buildUrl("/session/$sessionId/unrevert")) {
            withAuth(conn)
        }.body()
    }

    // === Session Init & Children ===

    suspend fun initSession(
        conn: ServerConnection,
        sessionId: String,
    ): Session {
        return client.post(conn.buildUrl("/session/$sessionId/init")) {
            withAuth(conn)
        }.body()
    }

    suspend fun getSessionChildren(
        conn: ServerConnection,
        sessionId: String,
    ): List<Session> {
        return client.get(conn.buildUrl("/session/$sessionId/children")) {
            withAuth(conn)
        }.body()
    }

    // === Message Operations ===

    suspend fun deleteMessage(
        conn: ServerConnection,
        sessionId: String,
        messageId: String,
    ): Boolean {
        return client.delete(conn.buildUrl("/session/$sessionId/message/$messageId")) {
            withAuth(conn)
        }.body()
    }

    suspend fun patchMessagePart(
        conn: ServerConnection,
        sessionId: String,
        messageId: String,
        partId: String,
        update: Map<String, kotlinx.serialization.json.JsonElement>,
    ): Part {
        return client.patch(conn.buildUrl("/session/$sessionId/message/$messageId/part/$partId")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(update)
        }.body()
    }

    suspend fun deleteMessagePart(
        conn: ServerConnection,
        sessionId: String,
        messageId: String,
        partId: String,
    ): Boolean {
        return client.delete(conn.buildUrl("/session/$sessionId/message/$messageId/part/$partId")) {
            withAuth(conn)
        }.body()
    }

    // === Shell ===

    suspend fun runShell(
        conn: ServerConnection,
        sessionId: String,
        command: String,
        arguments: String? = null,
    ) {
        client.post(conn.buildUrl("/session/$sessionId/shell")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(buildMap {
                put("command", command)
                arguments?.let { put("arguments", it) }
            })
        }
    }

    // === Command ===

    suspend fun sendCommand(
        conn: ServerConnection,
        sessionId: String,
        command: String,
        arguments: String? = null,
    ) {
        client.post(conn.buildUrl("/session/$sessionId/command")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(buildMap {
                put("command", command)
                arguments?.let { put("arguments", it) }
            })
        }
    }

    // === PTY ===

    suspend fun listPtys(
        conn: ServerConnection,
        directory: String? = null,
    ): List<PtyInfo> {
        return client.get(conn.buildUrl("/pty")) {
            withAuth(conn)
            withDirectory(directory)
        }.body()
    }

    suspend fun createPty(
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

    suspend fun getPty(
        conn: ServerConnection,
        ptyId: String,
        directory: String? = null,
    ): PtyInfo {
        return client.get(conn.buildUrl("/pty/$ptyId")) {
            withAuth(conn)
            withDirectory(directory)
        }.body()
    }

    suspend fun updatePty(
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

    suspend fun deletePty(
        conn: ServerConnection,
        ptyId: String,
        directory: String? = null,
    ): Boolean {
        return client.delete(conn.buildUrl("/pty/$ptyId")) {
            withAuth(conn)
            withDirectory(directory)
        }.body()
    }

    // === Agent ===

    suspend fun getAgents(conn: ServerConnection): List<AgentConfig> {
        return client.get(conn.buildUrl("/agent")) {
            withAuth(conn)
        }.body()
    }

    // === Commands ===

    suspend fun getCommands(conn: ServerConnection): List<CommandInfo> {
        return client.get(conn.buildUrl("/command")) {
            withAuth(conn)
        }.body()
    }

    // === Config (per-project) ===

    suspend fun getProjectConfig(conn: ServerConnection): JsonElement {
        return client.get(conn.buildUrl("/config")) {
            withAuth(conn)
        }.body()
    }

    suspend fun patchProjectConfig(
        conn: ServerConnection,
        config: JsonElement,
    ): JsonElement {
        return client.patch(conn.buildUrl("/config")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(config)
        }.body()
    }

    suspend fun getConfigProviders(conn: ServerConnection): ConfigProviders {
        return client.get(conn.buildUrl("/config/providers")) {
            withAuth(conn)
        }.body()
    }

    // === Skill ===

    suspend fun getSkills(conn: ServerConnection): List<SkillInfo> {
        return client.get(conn.buildUrl("/skill")) {
            withAuth(conn)
        }.body()
    }

    // === MCP ===

    suspend fun listMcpServers(conn: ServerConnection): Map<String, McpStatus> {
        return client.get(conn.buildUrl("/mcp")) {
            withAuth(conn)
        }.body()
    }

    suspend fun addMcpServer(
        conn: ServerConnection,
        request: McpServerCreateRequest,
    ): Map<String, McpStatus> {
        return client.post(conn.buildUrl("/mcp")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun connectMcpServer(
        conn: ServerConnection,
        name: String,
    ): Boolean {
        return client.post(conn.buildUrl("/mcp/$name/connect")) {
            withAuth(conn)
        }.body()
    }

    suspend fun disconnectMcpServer(
        conn: ServerConnection,
        name: String,
    ): Boolean {
        return client.post(conn.buildUrl("/mcp/$name/disconnect")) {
            withAuth(conn)
        }.body()
    }

    suspend fun startMcpAuth(
        conn: ServerConnection,
        name: String,
    ): McpAuthUrl {
        return client.post(conn.buildUrl("/mcp/$name/auth")) {
            withAuth(conn)
        }.body()
    }

    suspend fun completeMcpAuth(
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

    suspend fun authenticateMcp(
        conn: ServerConnection,
        name: String,
    ): McpStatus {
        return client.post(conn.buildUrl("/mcp/$name/auth/authenticate")) {
            withAuth(conn)
        }.body()
    }

    suspend fun removeMcpAuth(
        conn: ServerConnection,
        name: String,
    ): Boolean {
        return client.delete(conn.buildUrl("/mcp/$name/auth")) {
            withAuth(conn)
        }.body()
    }

    // === Path ===

    suspend fun getPathInfo(conn: ServerConnection): PathInfo {
        return client.get(conn.buildUrl("/path")) {
            withAuth(conn)
        }.body()
    }

    // === Formatter ===

    suspend fun getFormatters(conn: ServerConnection): List<FormatterInfo> {
        return client.get(conn.buildUrl("/formatter")) {
            withAuth(conn)
        }.body()
    }

    // === Instance ===

    suspend fun disposeInstance(
        conn: ServerConnection,
        directory: String? = null,
    ): Boolean {
        return client.post(conn.buildUrl("/instance/dispose")) {
            withAuth(conn)
            directory?.let { parameter("directory", it) }
        }.body()
    }

    // === Log ===

    suspend fun sendLog(
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

    suspend fun getLspServers(conn: ServerConnection): List<LspInfo> {
        return client.get(conn.buildUrl("/lsp")) {
            withAuth(conn)
        }.body()
    }

    // === VCS ===

    suspend fun getVcsBranch(conn: ServerConnection): VcsInfo {
        return client.get(conn.buildUrl("/vcs")) {
            withAuth(conn)
        }.body()
    }

    // === Experimental ===

    suspend fun listWorkspaces(conn: ServerConnection): JsonElement {
        return client.get(conn.buildUrl("/experimental/workspace")) {
            withAuth(conn)
        }.body()
    }

    suspend fun createWorkspace(
        conn: ServerConnection,
        request: WorkspaceCreateRequest,
    ): JsonElement {
        return client.post(conn.buildUrl("/experimental/workspace")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteWorkspace(
        conn: ServerConnection,
        workspaceId: String,
    ): Boolean {
        return client.delete(conn.buildUrl("/experimental/workspace/$workspaceId")) {
            withAuth(conn)
        }.body()
    }

    suspend fun listWorktrees(conn: ServerConnection): List<String> {
        return client.get(conn.buildUrl("/experimental/worktree")) {
            withAuth(conn)
        }.body()
    }

    suspend fun createWorktree(
        conn: ServerConnection,
        request: WorktreeCreateRequest,
    ): JsonElement {
        return client.post(conn.buildUrl("/experimental/worktree")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteWorktree(
        conn: ServerConnection,
        request: WorktreeDeleteRequest,
    ): Boolean {
        return client.delete(conn.buildUrl("/experimental/worktree")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun resetWorktree(
        conn: ServerConnection,
        request: WorktreeResetRequest,
    ) {
        client.post(conn.buildUrl("/experimental/worktree/reset")) {
            withAuth(conn)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getExperimentalResources(conn: ServerConnection): JsonElement {
        return client.get(conn.buildUrl("/experimental/resource")) {
            withAuth(conn)
        }.body()
    }

    suspend fun getExperimentalTools(
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

    suspend fun getExperimentalToolIds(conn: ServerConnection): List<String> {
        return client.get(conn.buildUrl("/experimental/tool/ids")) {
            withAuth(conn)
        }.body()
    }

    // === Helper extensions ===

    private fun HttpRequestBuilder.withAuth(conn: ServerConnection) {
        val auth = conn.authHeader
        Log.d(TAG, "withAuth: authHeader=${if (auth != null) "Basic *** (${auth.length} chars)" else "null"}")
        auth?.let { header("Authorization", it) }
    }

    private fun HttpRequestBuilder.withDirectory(directory: String?) {
        directory?.let { header("x-opencode-directory", it) }
    }

    private fun ServerConnection.buildUrl(path: String): String {
        val base = baseUrl.trimEnd('/')
        return "$base$path"
    }

    // === Request/Response models ===

    @kotlinx.serialization.Serializable
    data class SendMessageRequest(
        val agent: String? = null,
        val model: ModelRef? = null,
        val parts: List<Map<String, String>>? = null,
    )

    @kotlinx.serialization.Serializable
    data class HealthResponse(
        val healthy: Boolean = false,
        val version: String = "",
    )

    companion object {
        private const val TAG = "OpenCodeApi"
    }
}
