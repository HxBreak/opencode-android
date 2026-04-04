package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CommandInfo(
    val name: String = "",
    val description: String = "",
    val agent: String? = null,
    val source: String = "",      // "command" | "mcp" | "skill"
    val template: JsonElement? = null,
    val hints: List<String> = emptyList(),
)
