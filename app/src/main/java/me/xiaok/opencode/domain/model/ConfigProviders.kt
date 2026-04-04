package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigProviders(
    val providers: List<Provider> = emptyList(),
    val default: Map<String, String> = emptyMap(),
)
