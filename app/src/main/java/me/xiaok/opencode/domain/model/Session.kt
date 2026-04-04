package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val slug: String = "",
    val projectID: String = "",
    val workspaceID: String? = null,
    val directory: String = "",
    val parentID: String? = null,
    val title: String = "",
    val version: String = "",
    val summary: SessionSummary? = null,
    val share: SessionShare? = null,
    val permission: List<PermissionRule> = emptyList(),
    val revert: RevertInfo? = null,
    val time: SessionTime = SessionTime(),
)

@Serializable
data class SessionSummary(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0,
    val diffs: Int = 0,
)

@Serializable
data class SessionShare(
    val url: String = "",
)

@Serializable
data class PermissionRule(
    val permission: String = "",
    val pattern: String = "",
    val action: String = "",
)

@Serializable
data class RevertInfo(
    val messageID: String = "",
    val partID: String = "",
)

@Serializable
data class SessionTime(
    val created: Long = 0L,
    val updated: Long = 0L,
    val compacting: Long? = null,
    val archived: Long? = null,
)
