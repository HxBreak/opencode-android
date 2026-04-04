package me.xiaok.opencode.data.local.db.entity

import androidx.room.Entity

/**
 * Tracks when a session was last viewed by the user.
 * Used to determine unread state: session.time.updated > lastViewedAt → unread.
 */
@Entity(
    tableName = "session_view_log",
    primaryKeys = ["serverId", "sessionId"],
)
data class SessionViewLog(
    val serverId: String,
    val sessionId: String,
    val lastViewedAt: Long,
)
