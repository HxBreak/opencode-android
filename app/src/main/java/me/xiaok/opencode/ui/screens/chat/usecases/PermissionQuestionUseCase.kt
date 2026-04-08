package me.xiaok.opencode.ui.screens.chat.usecases

import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.PermissionReply
import me.xiaok.opencode.domain.model.QuestionRequest
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

class PermissionQuestionUseCase @Inject constructor(
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val errorCollector: ErrorCollector,
) {
    sealed class QuestionResult {
        data object Success : QuestionResult()
        data class ApiFailure(val errorMessage: String) : QuestionResult()
        data object ServerNotFound : QuestionResult()
    }

    suspend fun replyPermission(
        serverId: String,
        sessionId: String,
        permissionId: String,
        reply: String,
        message: String? = null,
    ): Result<Unit> {
        return try {
            val server = serverRepository.getServer(serverId) ?: return Result.failure(IllegalStateException("Server not found"))
            api.replyPermission(server, permissionId, PermissionReply(reply, message))
            eventReducer.removePermission(sessionId, permissionId)
            Result.success(Unit)
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            Result.failure(e)
        }
    }

    suspend fun replyQuestion(
        serverId: String,
        question: QuestionRequest,
        answers: List<List<String>>,
    ): QuestionResult {
        return try {
            val server = serverRepository.getServer(serverId) ?: return QuestionResult.ServerNotFound
            val directory = eventReducer.sessions.value[question.sessionID]?.directory
            val success = api.replyQuestion(server, question.id, answers, directory = directory)
            if (success) {
                eventReducer.removeQuestion(question.sessionID, question.id)
                QuestionResult.Success
            } else {
                QuestionResult.ApiFailure("Failed to reply to question")
            }
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            QuestionResult.ApiFailure(e.message ?: "Failed to reply")
        }
    }

    suspend fun rejectQuestion(
        serverId: String,
        question: QuestionRequest,
    ): QuestionResult {
        return try {
            val server = serverRepository.getServer(serverId) ?: return QuestionResult.ServerNotFound
            val directory = eventReducer.sessions.value[question.sessionID]?.directory
            val success = api.rejectQuestion(server, question.id, directory = directory)
            if (success) {
                eventReducer.removeQuestion(question.sessionID, question.id)
                QuestionResult.Success
            } else {
                QuestionResult.ApiFailure("Failed to reject")
            }
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            QuestionResult.ApiFailure(e.message ?: "Failed to reject")
        }
    }
}
