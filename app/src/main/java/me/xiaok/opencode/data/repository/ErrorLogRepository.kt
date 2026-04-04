package me.xiaok.opencode.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.local.db.dao.ErrorLogDao
import me.xiaok.opencode.data.local.db.entity.ErrorLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorLogRepository @Inject constructor(
    private val errorLogDao: ErrorLogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allErrors: Flow<List<ErrorLogEntity>> = errorLogDao.getAll()

    fun logError(message: String, screen: String, stackTrace: String) {
        scope.launch {
            errorLogDao.insert(
                ErrorLogEntity(
                    message = message,
                    screen = screen,
                    stackTrace = stackTrace,
                )
            )
        }
    }

    suspend fun deleteById(id: Long) = errorLogDao.deleteById(id)

    suspend fun deleteAll() = errorLogDao.deleteAll()

    suspend fun count(): Int = errorLogDao.count()
}
