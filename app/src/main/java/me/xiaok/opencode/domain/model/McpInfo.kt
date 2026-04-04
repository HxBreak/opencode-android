package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class McpStatus(
    val status: String = "",      // "connected" | "disabled" | "failed" | "needs_auth" | "needs_client_registration"
    val error: String? = null,
)

@Serializable
data class McpServerCreateRequest(
    val name: String = "",
    val config: McpServerConfig = McpServerConfig(),
)

@Serializable
data class McpServerConfig(
    val type: String = "",        // "local" | "remote"
    val command: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val timeout: Long = 5000L,
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val oauth: McpOAuthConfig? = null,
)

@Serializable
data class McpOAuthConfig(
    val clientId: String = "",
    val scope: String = "",
)

@Serializable
data class McpAuthUrl(
    val authorizationUrl: String = "",
)
