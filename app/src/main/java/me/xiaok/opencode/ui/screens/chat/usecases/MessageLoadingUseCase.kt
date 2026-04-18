package me.xiaok.opencode.ui.screens.chat.usecases

import android.util.Log
import kotlinx.coroutines.flow.first
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

class MessageLoadingUseCase @Inject constructor(
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val errorCollector: ErrorCollector,
) {
    sealed class LoadResult {
        data class Success(
            val nextCursor: String?,
        ) : LoadResult()
        data class Error(val message: String) : LoadResult()
    }

    suspend fun loadInitial(serverId: String, sessionId: String): LoadResult {
        Log.d(TAG, "loadInitial: START sessionId=$sessionId")
        return try {
            val server = serverRepository.getServer(serverId) ?: return LoadResult.Error("Server not found")
            val limit = settingsRepository.initialMessages.first()
            val directory = eventReducer.sessions.value[sessionId]?.directory
            Log.d(TAG, "loadInitial: requesting limit=$limit from API, directory=$directory")
            val page = api.listMessages(server, sessionId, limit = limit, directory = directory)
            val messages = page.messages
            Log.d(TAG, "loadInitial: API returned ${messages.size} messages, nextCursor=${page.nextCursor}")

            eventReducer.setMessages(sessionId, messages)

            messages.forEach { message ->
                if (eventReducer.parts.value[message.id] == null) {
                    eventReducer.setParts(message.id, message.parts)
                }
            }

            val finalMessages = eventReducer.messages.value[sessionId] ?: emptyList()
            Log.d(TAG, "loadInitial: DONE, messages in reducer=${finalMessages.size}, parts keys=${eventReducer.parts.value.keys.size}")

            LoadResult.Success(page.nextCursor)
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            Log.e(TAG, "loadInitial: FAILED", e)
            LoadResult.Error(e.message ?: "Failed to load messages")
        }
    }

    suspend fun loadOlder(
        serverId: String,
        sessionId: String,
        cursor: String,
    ): LoadResult {
        Log.d(TAG, "loadOlder: START sessionId=$sessionId")
        return try {
            val server = serverRepository.getServer(serverId) ?: return LoadResult.Error("Server not found")
            val limit = settingsRepository.initialMessages.first()
            val directory = eventReducer.sessions.value[sessionId]?.directory
            val page = api.listMessages(
                server, sessionId,
                limit = limit,
                before = cursor,
                directory = directory,
            )
            val olderMessages = page.messages

            if (olderMessages.isEmpty()) {
                Log.d(TAG, "loadOlder: no older messages found")
                LoadResult.Success(null)  // null cursor means no more
            } else {
                eventReducer.prependMessages(sessionId, olderMessages)

                olderMessages.forEach { message ->
                    if (eventReducer.parts.value[message.id] == null) {
                        eventReducer.setParts(message.id, message.parts)
                    }
                }

                Log.d(TAG, "loadOlder: DONE, fetched=${olderMessages.size}")
                LoadResult.Success(page.nextCursor)
            }
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            Log.e(TAG, "loadOlder: FAILED", e)
            LoadResult.Error(e.message ?: "Failed to load older messages")
        }
    }

    companion object {
        private const val TAG = "MessageLoadingUseCase"
    }
}
