package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FormatterInfo(
    val name: String = "",
    val extensions: List<String> = emptyList(),
    val enabled: Boolean = false,
)
