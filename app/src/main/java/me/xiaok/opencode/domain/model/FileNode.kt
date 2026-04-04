package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileNode(
    val name: String = "",
    val path: String = "",
    val absolute: String = "",
    val type: String = "",  // "file" | "directory"
    val ignored: Boolean = false,
)
