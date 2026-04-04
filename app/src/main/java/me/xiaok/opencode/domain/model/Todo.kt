package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Todo(
    val id: String = "",
    val content: String = "",
    val status: String = "",   // "pending", "in_progress", "completed", "cancelled"
    val priority: String = "", // "high", "medium", "low"
)
