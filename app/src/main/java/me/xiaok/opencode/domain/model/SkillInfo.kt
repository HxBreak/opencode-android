package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SkillInfo(
    val name: String = "",
    val description: String = "",
    val location: String = "",
    val content: String = "",
)
