package me.xiaok.opencode.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.xiaok.opencode.data.local.db.AppDatabase
import me.xiaok.opencode.data.local.db.dao.ErrorLogDao
import me.xiaok.opencode.data.local.db.dao.MessageDao
import me.xiaok.opencode.data.local.db.dao.SessionDao
import me.xiaok.opencode.data.local.db.dao.SessionViewLogDao
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `session_view_log` (
                `serverId` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `lastViewedAt` INTEGER NOT NULL,
                PRIMARY KEY(`serverId`, `sessionId`)
            )
        """.trimIndent())
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `error_log` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `message` TEXT NOT NULL,
                `screen` TEXT NOT NULL,
                `stackTrace` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "opencode.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideSessionViewLogDao(db: AppDatabase): SessionViewLogDao = db.sessionViewLogDao()

    @Provides
    fun provideErrorLogDao(db: AppDatabase): ErrorLogDao = db.errorLogDao()
}
