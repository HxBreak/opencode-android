package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LogEntry(
    val service: String = "",
    val level: String = "",       // "debug" | "info" | "error" | "warn"
    val message: String = "",
    val extra: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)
