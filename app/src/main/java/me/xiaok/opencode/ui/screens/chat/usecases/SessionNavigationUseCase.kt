package me.xiaok.opencode.ui.screens.chat.usecases

import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.Part
import me.xiaok.opencode.domain.model.Session
import me.xiaok.opencode.domain.model.SseEvent
import javax.inject.Inject

class SessionNavigationUseCase @Inject constructor(
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
) {
    suspend fun loadChildSessions(
        serverId: String,
        sessionId: String,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        val directory = eventReducer.sessions.value[sessionId]?.directory
        val children = api.getSessionChildren(server, sessionId, directory = directory)
        if (children.isNotEmpty()) {
            children.forEach { child ->
                eventReducer.processEvent(
                    serverId,
                    SseEvent.SessionCreated(child)
                )
            }
        }
    }

    suspend fun loadPendingQuestions(
        serverId: String,
        sessionId: String,
    ) {
        val server = serverRepository.getServer(serverId) ?: return
        val directory = eventReducer.sessions.value[sessionId]?.directory
        val allQuestions = api.listQuestions(server, directory = directory)
        allQuestions.groupBy { it.sessionID }.forEach { (sid, questions) ->
            eventReducer.setQuestions(sid, questions)
        }
    }

    fun computeChildSessionIds(
        parentSessionId: String,
        messages: List<Message>,
        allParts: Map<String, List<Part>>,
        allSessions: Map<String, Session>,
    ): Map<String, String> {
        val childSessions = allSessions
            .filter { (_, s) -> s.parentID == parentSessionId }
            .values
            .sortedBy { it.time.created }

        val subSessionIds = mutableMapOf<String, String>()
        if (childSessions.isNotEmpty()) {
            val subtaskParts = mutableListOf<Pair<String, Part.Subtask>>()
            for (msg in messages) {
                val msgParts = allParts[msg.id] ?: msg.parts
                for (part in msgParts) {
                    if (part is Part.Subtask) {
                        subtaskParts.add(part.id to part)
                    }
                }
            }

            val unmatchedSessions = childSessions.toMutableList()
            val matchedSessionIds = mutableSetOf<String>()

            for ((partId, subtask) in subtaskParts) {
                val agent = subtask.agent
                val bestMatch = unmatchedSessions.firstOrNull { child ->
                    child.title.contains("(@${agent}", ignoreCase = true) &&
                            child.id !in matchedSessionIds
                } ?: unmatchedSessions.firstOrNull { child ->
                    child.title.contains(agent, ignoreCase = true) &&
                            child.id !in matchedSessionIds
                }

                if (bestMatch != null) {
                    subSessionIds[partId] = bestMatch.id
                    unmatchedSessions.remove(bestMatch)
                    matchedSessionIds.add(bestMatch.id)
                } else if (unmatchedSessions.isNotEmpty()) {
                    val fallback = unmatchedSessions.removeAt(0)
                    subSessionIds[partId] = fallback.id
                    matchedSessionIds.add(fallback.id)
                }
            }
        }
        return subSessionIds
    }

    fun findAllDescendants(
        parentSessionId: String,
        allSessions: Map<String, Session>,
    ): List<String> {
        val result = mutableListOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(parentSessionId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            allSessions.values
                .filter { it.parentID == current }
                .forEach { child ->
                    result.add(child.id)
                    queue.add(child.id)
                }
        }
        return result
    }
}
