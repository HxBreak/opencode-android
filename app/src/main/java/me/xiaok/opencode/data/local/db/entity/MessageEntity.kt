package me.xiaok.opencode.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val serverId: String,
    val role: String = "",
    val messageJson: String = "{}",    // Full JSON-serialized Message
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
