package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PathInfo(
    val home: String = "",
    val state: String = "",
    val config: String = "",
    val worktree: String = "",
    val directory: String = "",
)
