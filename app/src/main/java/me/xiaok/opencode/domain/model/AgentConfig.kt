package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentConfig(
    val name: String = "",
    val description: String = "",
    val mode: String = "",        // "primary" | "subagent" | "all"
    val native: Boolean = false,
    val hidden: Boolean = false,
    val model: ModelRef? = null,
    val steps: Int? = null,
    val color: String? = null,
)
