package me.xiaok.opencode.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val slug: String = "",
    val projectID: String = "",
    val workspaceID: String? = null,
    val directory: String = "",
    val parentID: String? = null,
    val title: String = "",
    val version: String = "",
    val summaryJson: String? = null,
    val shareJson: String? = null,
    val permissionJson: String? = null,
    val revertJson: String? = null,
    val timeJson: String = "{}",
    val updatedAt: Long = 0L,
)
