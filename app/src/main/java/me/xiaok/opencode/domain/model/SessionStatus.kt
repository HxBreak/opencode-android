package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus {
    IDLE,
    BUSY,
    RETRY,
}
