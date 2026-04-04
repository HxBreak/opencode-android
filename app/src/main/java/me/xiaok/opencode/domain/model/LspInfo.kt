package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LspInfo(
    val id: String = "",
    val name: String = "",
    val root: String = "",
    val status: String = "",      // "connected" | "error"
)
