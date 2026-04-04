package me.xiaok.opencode.data.local.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xiaok.opencode.data.local.db.entity.ErrorLogEntity

@Dao
interface ErrorLogDao {
    @Query("SELECT * FROM error_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ErrorLogEntity>>

    @Query("SELECT COUNT(*) FROM error_log")
    suspend fun count(): Int

    @Insert
    suspend fun insert(error: ErrorLogEntity): Long

    @Query("DELETE FROM error_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM error_log")
    suspend fun deleteAll()
}
