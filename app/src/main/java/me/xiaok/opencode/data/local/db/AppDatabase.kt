package me.xiaok.opencode.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.xiaok.opencode.data.local.db.dao.ErrorLogDao
import me.xiaok.opencode.data.local.db.dao.MessageDao
import me.xiaok.opencode.data.local.db.dao.SessionDao
import me.xiaok.opencode.data.local.db.dao.SessionViewLogDao
import me.xiaok.opencode.data.local.db.entity.ErrorLogEntity
import me.xiaok.opencode.data.local.db.entity.MessageEntity
import me.xiaok.opencode.data.local.db.entity.SessionEntity
import me.xiaok.opencode.data.local.db.entity.SessionViewLog

@Database(
    entities = [SessionEntity::class, MessageEntity::class, SessionViewLog::class, ErrorLogEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun sessionViewLogDao(): SessionViewLogDao
    abstract fun errorLogDao(): ErrorLogDao
}
