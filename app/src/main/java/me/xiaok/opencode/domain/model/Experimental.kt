package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceCreateRequest(
    val id: String? = null,
    val type: String = "",
    val branch: String? = null,
    val extra: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class WorktreeCreateRequest(
    val name: String? = null,
    val startCommand: String? = null,
)

@Serializable
data class WorktreeDeleteRequest(
    val directory: String = "",
)

@Serializable
data class WorktreeResetRequest(
    val directory: String = "",
)
