package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the current processing status of a session.
 *
 * Server sends status as a discriminated JSON object:
 * - `{"type":"idle"}` — session waiting for input
 * - `{"type":"busy"}` — AI actively processing
 * - `{"type":"retry","attempt":1,"message":"...","next":1234567890}` — auto-retry in progress
 */
@Serializable
sealed class SessionStatus {

    /** Session is idle, waiting for user input. */
    @Serializable
    data object Idle : SessionStatus()

    /** AI is actively processing a request. */
    @Serializable
    data object Busy : SessionStatus()

    /** Previous operation failed, auto-retry scheduled. */
    @Serializable
    data class Retry(
        val attempt: Int = 0,
        val message: String = "",
        val next: Long = 0L,
    ) : SessionStatus()
}
