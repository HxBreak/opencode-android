package me.xiaok.opencode.ui.screens.chat

import android.util.Log
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Session operations — extracted from ChatViewModel
// ---------------------------------------------------------------------------

private fun <T> ChatViewModel.executeSessionOp(
    operationName: String,
    onResult: ((T) -> Unit)? = null,
    block: suspend (serverId: String, sessionId: String, directory: String?) -> T,
) {
    vmScope.launch {
        try {
            val directory = eventReducer.sessions.value[sessionId]?.directory
            val result = block(serverId, sessionId, directory)
            onResult?.invoke(result)
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            _error.value = ChatError(ErrorKind.SESSION, e.message ?: "Failed to $operationName")
        }
    }
}

fun ChatViewModel.refreshSessionDiffs() {
    vmScope.launch {
        try {
            val directory = eventReducer.sessions.value[sessionId]?.directory
            val workspace = eventReducer.sessions.value[sessionId]?.workspaceID
            sessionOpsUseCase.refreshSessionDiffs(serverId, sessionId, directory, workspace)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "refreshSessionDiffs: failed", e)
        }
    }
}

fun ChatViewModel.dismissDiffs() {
    sessionOpsUseCase.dismissDiffs(serverId, sessionId)
}

fun ChatViewModel.forkSession(messageId: String, onResult: (String) -> Unit) {
    executeSessionOp("fork session", onResult) { sId, sesId, dir ->
        sessionOpsUseCase.forkSession(sId, sesId, messageId, dir)
    }
}

fun ChatViewModel.shareSession(onResult: (String) -> Unit) {
    executeSessionOp("share session", onResult) { sId, sesId, dir ->
        sessionOpsUseCase.shareSession(sId, sesId, dir)
    }
}

fun ChatViewModel.unshareSession() {
    executeSessionOp<Unit>("unshare session") { sId, sesId, dir ->
        sessionOpsUseCase.unshareSession(sId, sesId, dir)
    }
}

fun ChatViewModel.revertSession(messageId: String) {
    executeSessionOp<Unit>("revert session") { sId, sesId, dir ->
        sessionOpsUseCase.revertSession(sId, sesId, messageId, dir)
    }
}

fun ChatViewModel.summarizeSession() {
    executeSessionOp<Unit>("summarize session") { sId, sesId, dir ->
        sessionOpsUseCase.summarizeSession(sId, sesId, modelSelectionUseCase.selectedModel.value, dir)
    }
}

fun ChatViewModel.unrevertSession() {
    executeSessionOp<Unit>("unrevert session") { sId, sesId, dir ->
        sessionOpsUseCase.unrevertSession(sId, sesId, dir)
    }
}

fun ChatViewModel.renameSession(newTitle: String) {
    executeSessionOp<Unit>("rename session") { sId, sesId, dir ->
        sessionOpsUseCase.renameSession(sId, sesId, newTitle, dir)
    }
}

fun ChatViewModel.deleteSession() {
    executeSessionOp<Unit>("delete session") { sId, sesId, dir ->
        sessionOpsUseCase.deleteSession(sId, sesId, dir)
    }
}

suspend fun ChatViewModel.exportSession(): String {
    return sessionOpsUseCase.exportSession(serverId, sessionId)
}
