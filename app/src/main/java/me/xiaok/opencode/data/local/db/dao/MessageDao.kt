package me.xiaok.opencode.data.local.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xiaok.opencode.data.local.db.entity.MessageEntity

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getMessagesForSessionPaged(sessionId: String, limit: Int = 50): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM messages WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun delete(messageId: String)
}
