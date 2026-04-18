package me.xiaok.opencode.data.repository

import android.util.Log
import me.xiaok.opencode.domain.model.PermissionRequest
import me.xiaok.opencode.domain.model.QuestionRequest

/**
 * Handles permission and question interaction state within EventReducer.
 * Manages the _permissions and _questions MutableStateFlows on behalf of the host.
 *
 * Extracted from EventReducer to reduce file size and isolate interaction concern.
 */
class InteractionReducer internal constructor(
    private val host: EventReducer,
) {

    // === Event Handlers ===

    fun onPermissionAsked(permission: PermissionRequest) {
        val sessionId = permission.sessionID
        val current = host.permissionsFlow.value[sessionId] ?: emptyList()
        host.permissionsFlow.value = host.permissionsFlow.value.toMutableMap().apply {
            put(sessionId, current + permission)
        }
    }

    fun onPermissionReplied(sessionId: String, requestId: String) {
        removePermission(sessionId, requestId)
    }

    fun onQuestionAsked(question: QuestionRequest) {
        val sessionId = question.sessionID
        Log.d(TAG, "onQuestionAsked: id=${question.id}, sessionID=$sessionId, questions=${question.questions.size}")
        val current = host.questionsFlow.value[sessionId] ?: emptyList()
        host.questionsFlow.value = host.questionsFlow.value.toMutableMap().apply {
            put(sessionId, current + question)
        }
    }

    fun onQuestionReplied(sessionId: String, requestId: String) {
        removeQuestion(sessionId, requestId)
    }

    fun onQuestionRejected(sessionId: String, requestId: String) {
        removeQuestion(sessionId, requestId)
    }

    // === Public Operations (delegated from EventReducer) ===

    fun removePermission(sessionId: String, requestId: String) {
        val current = host.permissionsFlow.value[sessionId] ?: return
        host.permissionsFlow.value = host.permissionsFlow.value.toMutableMap().apply {
            put(sessionId, current.filterNot { it.id == requestId })
        }
    }

    fun removeQuestion(sessionId: String, requestId: String) {
        val current = host.questionsFlow.value[sessionId] ?: return
        host.questionsFlow.value = host.questionsFlow.value.toMutableMap().apply {
            put(sessionId, current.filterNot { it.id == requestId })
        }
    }

    /**
     * Initialize questions for a session from REST API.
     * Only adds questions not already present (avoids duplicates from SSE).
     */
    fun setQuestions(sessionId: String, questions: List<QuestionRequest>) {
        val current = host.questionsFlow.value[sessionId] ?: emptyList()
        val existingIds = current.map { it.id }.toSet()
        val newQuestions = questions.filterNot { it.id in existingIds }
        Log.d(TAG, "setQuestions: sessionId=$sessionId, current=${current.size}, incoming=${questions.size}, new=${newQuestions.size}")
        if (newQuestions.isEmpty()) return
        host.questionsFlow.value = host.questionsFlow.value.toMutableMap().apply {
            put(sessionId, current + newQuestions)
        }
    }

    // === Cleanup ===

    /** Clear permissions and questions for the given session IDs. */
    fun clearForServer(sessionIds: Set<String>) {
        host.permissionsFlow.value = host.permissionsFlow.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }
        host.questionsFlow.value = host.questionsFlow.value.toMutableMap().apply {
            sessionIds.forEach { remove(it) }
        }
    }

    /** Full reset of permissions and questions state. */
    fun clearAll() {
        host.permissionsFlow.value = emptyMap()
        host.questionsFlow.value = emptyMap()
    }

    companion object {
        private const val TAG = "InteractionReducer"
    }
}
