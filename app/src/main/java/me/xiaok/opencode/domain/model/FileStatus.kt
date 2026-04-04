package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileStatus(
    val path: String = "",
    val added: Int = 0,
    val removed: Int = 0,
    val status: String = "",  // "added", "deleted", "modified"
)
