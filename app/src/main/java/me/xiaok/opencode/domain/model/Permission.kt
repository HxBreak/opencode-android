package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PermissionRequest(
    val id: String = "",
    val sessionID: String = "",
    val permission: String = "",
    val patterns: List<String> = emptyList(),
    val metadata: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val always: List<String> = emptyList(),
    val tool: PermissionToolInfo = PermissionToolInfo(),
)

@Serializable
data class PermissionToolInfo(
    val messageID: String = "",
    val callID: String = "",
)

@Serializable
data class PermissionReply(
    val reply: String = "",  // "once", "always", "reject"
    val message: String? = null,
)
