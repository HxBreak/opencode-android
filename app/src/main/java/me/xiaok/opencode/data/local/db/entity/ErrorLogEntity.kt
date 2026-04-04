package me.xiaok.opencode.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "error_log")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val screen: String = "",
    val stackTrace: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
