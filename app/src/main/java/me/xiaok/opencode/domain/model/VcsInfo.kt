package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VcsInfo(
    val branch: String? = null,
)
