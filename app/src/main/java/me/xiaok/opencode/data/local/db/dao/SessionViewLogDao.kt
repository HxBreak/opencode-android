package me.xiaok.opencode.data.local.db.dao

import androidx.room.*
import me.xiaok.opencode.data.local.db.entity.SessionViewLog

@Dao
interface SessionViewLogDao {

    @Query("SELECT * FROM session_view_log WHERE serverId = :serverId")
    suspend fun getAllForServer(serverId: String): List<SessionViewLog>

    @Query("SELECT * FROM session_view_log WHERE serverId = :serverId AND sessionId = :sessionId")
    suspend fun get(serverId: String, sessionId: String): SessionViewLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SessionViewLog)

    @Query("DELETE FROM session_view_log WHERE serverId = :serverId AND sessionId = :sessionId")
    suspend fun delete(serverId: String, sessionId: String)

    @Query("DELETE FROM session_view_log WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)

    @Query("DELETE FROM session_view_log")
    suspend fun deleteAll()
}
