package me.xiaok.opencode.data.local.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xiaok.opencode.data.local.db.entity.SessionEntity

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE serverId = :serverId ORDER BY updatedAt DESC")
    fun getSessionsForServer(serverId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM sessions WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)
}
