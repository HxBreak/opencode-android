package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents connection info to an OpenCode server.
 * All API methods accept this as parameter (stateless design).
 */
@Serializable
data class ServerConnection(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String = "",
    val password: String = "",
    val autoConnect: Boolean = true,
) {
    /** HTTP Basic Auth header value, e.g. "Basic base64(username:password)" */
    val authHeader: String?
        get() = if (username.isNotEmpty() || password.isNotEmpty()) {
            "Basic ${java.util.Base64.getEncoder().encodeToString(
                "$username:$password".toByteArray()
            )}"
        } else null
}
