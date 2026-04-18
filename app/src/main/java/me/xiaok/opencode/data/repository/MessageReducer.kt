package me.xiaok.opencode.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.xiaok.opencode.domain.model.*
import kotlin.reflect.KClass

/**
 * Handles message-related SSE events, streaming delta batching, and part merging.
 * Accesses EventReducer's StateFlows via internal accessors.
 */
class MessageReducer internal constructor(
    private val host: EventReducer,
) {
    private val scope get() = host.scopeAccessor

    // === Delta Batching (streaming text performance) ===

    /** Accumulated text deltas keyed by "messageId:partId", flushed periodically */
    private val pendingDeltas = mutableMapOf<String, StringBuilder>()

    /** Scheduled flush job — cancelled and rescheduled on each delta */
    private var flushJob: Job? = null

    /** Flush interval in milliseconds — balances responsiveness vs. recomposition overhead */
    private val deltaFlushIntervalMs = 50L

    /**
     * Apply all accumulated deltas to parts StateFlow immediately.
     * Called by the scheduled flush job, and before any non-delta part operation.
     */
    internal fun flushPendingDeltas() {
        if (pendingDeltas.isEmpty()) return
        val snapshot = pendingDeltas.toMap()
        pendingDeltas.clear()
        flushJob = null

        for ((key, accumulated) in snapshot) {
            val (messageId, partId) = key.split(":", limit = 2)
            val current = host.partsFlow.value[messageId] ?: continue
            val updated = current.map { part ->
                if (part.id != partId) return@map part
                when (part) {
                    is Part.Text -> part.copy(text = part.text + accumulated.toString())
                    is Part.Reasoning -> part.copy(text = part.text + accumulated.toString())
                    else -> part
                }
            }
            host.partsFlow.value = host.partsFlow.value.toMutableMap().apply {
                put(messageId, updated)
            }
        }
    }

    /** Flush deltas for specific messages (used when setting parts from API response). */
    private fun flushPendingDeltasForMessages(messageIds: Set<String>) {
        if (pendingDeltas.isEmpty()) return
        val toFlush = pendingDeltas.keys.filter { key ->
            val messageId = key.substringBefore(":")
            messageId in messageIds
        }
        if (toFlush.isEmpty()) return
        for (key in toFlush) {
            val accumulated = pendingDeltas.remove(key) ?: continue
            val (messageId, partId) = key.split(":", limit = 2)
            val current = host.partsFlow.value[messageId] ?: continue
            val updated = current.map { part ->
                if (part.id != partId) return@map part
                when (part) {
                    is Part.Text -> part.copy(text = part.text + accumulated.toString())
                    is Part.Reasoning -> part.copy(text = part.text + accumulated.toString())
                    else -> part
                }
            }
            host.partsFlow.value = host.partsFlow.value.toMutableMap().apply {
                put(messageId, updated)
            }
        }
    }

    // === Event Handlers ===

    fun onMessageUpdated(message: Message) {
        val sessionId = message.info.sessionID
        val current = host.messagesFlow.value[sessionId] ?: emptyList()
        val index = current.indexOfFirst { it.info.id == message.info.id }
        val updated = if (index >= 0) {
            Log.d(TAG, "onMessageUpdated: UPDATE sessionId=$sessionId, msgId=${message.info.id}, role=${message.info.role}, parts=${message.parts.size}")
            current.toMutableList().apply { set(index, message) }
        } else {
            Log.d(TAG, "onMessageUpdated: APPEND sessionId=$sessionId, msgId=${message.info.id}, role=${message.info.role}, parts=${message.parts.size}")
            (current + message).sortedBy { it.info.time.created }
        }
        host.messagesFlow.value = host.messagesFlow.value.toMutableMap().apply {
            put(sessionId, updated)
        }

        // Sync inline parts from message to parts StateFlow so UI can render them.
        if (message.parts.isNotEmpty()) {
            val messageId = message.info.id
            flushPendingDeltasForMessages(setOf(messageId))
            val existingParts = host.partsFlow.value[messageId] ?: emptyList()
            val merged = mergeParts(existingParts, message.parts)
            host.partsFlow.value = host.partsFlow.value.toMutableMap().apply {
                put(messageId, merged)
            }
        }

        // Track unread: when an assistant message arrives, recompute unread
        if (message.isAssistant) {
            val serverId = host.serverSessionsFlow.value.entries.find { sessionId in it.value }?.key
            if (serverId != null) {
                scope.launch {
                    host.sessionReducer.computeUnreadSessions(serverId)
                }
            }
        }
    }

    fun onMessageRemoved(sessionId: String, messageId: String) {
        flushPendingDeltas()
        val current = host.messagesFlow.value[sessionId] ?: return
        host.messagesFlow.value = host.messagesFlow.value.toMutableMap().apply {
            put(sessionId, current.filterNot { it.info.id == messageId })
        }
        host.partsFlow.value = host.partsFlow.value.toMutableMap().apply { remove(messageId) }
    }

    fun onMessagePartUpdated(part: Part) {
        if (part::class in HIDDEN_PART_TYPES) return
        flushPendingDeltas()
        val messageId = part.messageId
        val current = host.partsFlow.value[messageId] ?: emptyList()
        val index = current.indexOfFirst { it.id == part.id }
        Log.d(TAG, "onMessagePartUpdated: msgId=$messageId, partId=${part.id}, type=${part::class.simpleName}, isUpdate=${index >= 0}")
        val updated = if (index >= 0) {
            current.toMutableList().apply { set(index, part) }
        } else {
            current + part
        }
        host.partsFlow.value = host.partsFlow.value.toMutableMap().apply {
            put(messageId, updated)
        }
    }

    fun onMessagePartDelta(
        sessionId: String,
        messageId: String,
        partId: String,
        field: String,
        delta: String,
    ) {
        // Stub creation: if parts don't exist yet, create immediately
        var current = host.partsFlow.value[messageId]
        if (current == null) {
            Log.d(TAG, "onMessagePartDelta: STUB msgId=$messageId, partId=$partId, field=$field, deltaLen=${delta.length}")
            val stub = when (field) {
                "text" -> Part.Text(id = partId, sessionId = sessionId, messageId = messageId, text = delta)
                else -> return
            }
            host.partsFlow.value = host.partsFlow.value.toMutableMap().apply {
                put(messageId, listOf(stub))
            }
            return
        }

        // Batch: accumulate delta in memory, schedule flush
        if (field != "text") return
        val key = "$messageId:$partId"
        pendingDeltas.getOrPut(key) { StringBuilder() }.append(delta)
        flushJob?.cancel()
        flushJob = scope.launch(Dispatchers.IO) {
            delay(deltaFlushIntervalMs)
            flushPendingDeltas()
        }
    }

    fun onMessagePartRemoved(sessionId: String, messageId: String, partId: String) {
        flushPendingDeltas()
        val current = host.partsFlow.value[messageId] ?: return
        host.partsFlow.value = host.partsFlow.value.toMutableMap().apply {
            put(messageId, current.filterNot { it.id == partId })
        }
    }

    // === Bulk Init ===

    /**
     * Bulk init messages from REST API.
     * Uses merge semantics to avoid race condition with SSE.
     */
    fun setMessages(sessionId: String, messages: List<Message>) {
        val current = host.messagesFlow.value[sessionId] ?: emptyList()
        if (current.isEmpty()) {
            host.messagesFlow.value = host.messagesFlow.value.toMutableMap().apply {
                put(sessionId, messages)
            }
        } else {
            val restById = messages.associateBy { it.id }
            val sseOnly = current.filter { it.id !in restById }
            val merged = (messages + sseOnly).sortedBy { it.info.time.created }
            Log.d(TAG, "setMessages merge: sessionId=$sessionId, rest=${messages.size}, sseOnly=${sseOnly.size}, merged=${merged.size}")
            host.messagesFlow.value = host.messagesFlow.value.toMutableMap().apply {
                put(sessionId, merged)
            }
        }
    }

    /** Prepend older messages to the front of the list (for reverse pagination) */
    fun prependMessages(sessionId: String, olderMessages: List<Message>) {
        val current = host.messagesFlow.value[sessionId] ?: emptyList()
        val existingIds = current.map { it.id }.toSet()
        val newMessages = olderMessages.filter { it.id !in existingIds }
        host.messagesFlow.value = host.messagesFlow.value.toMutableMap().apply {
            put(sessionId, newMessages + current)
        }
    }

    /** Bulk init parts for a message, merging with any existing SSE-accumulated parts */
    fun setParts(messageId: String, parts: List<Part>) {
        flushPendingDeltasForMessages(setOf(messageId))
        val existing = host.partsFlow.value[messageId] ?: emptyList()
        val merged = if (existing.isEmpty()) parts else mergeParts(existing, parts)
        host.partsFlow.value = host.partsFlow.value.toMutableMap().apply {
            put(messageId, merged)
        }
    }

    // === Merge Logic ===

    private val HIDDEN_PART_TYPES: Set<KClass<out Part>> = setOf(
        Part.StepStart::class,
        Part.StepFinish::class,
    )

    /**
     * Merge two part lists by ID. Preserves existing streaming content when possible.
     * Strategy: For each part ID, if both existing and incoming have it:
     * - Text/Reasoning parts: keep whichever has MORE text (longer = newer)
     * - All other parts: prefer incoming (authoritative update from server)
     */
    private fun mergeParts(existing: List<Part>, incoming: List<Part>): List<Part> {
        val existingById = existing.associateBy { it.id }
        val result = mutableListOf<Part>()
        val seen = mutableSetOf<String>()

        for (part in incoming) {
            if (part::class in HIDDEN_PART_TYPES) continue
            val existingPart = existingById[part.id]
            if (existingPart != null) {
                val merged = when {
                    part is Part.Text && existingPart is Part.Text ->
                        if (existingPart.text.length >= part.text.length) existingPart else part
                    part is Part.Reasoning && existingPart is Part.Reasoning ->
                        if (existingPart.text.length >= part.text.length) existingPart else part
                    else -> part
                }
                result.add(merged)
            } else {
                result.add(part)
            }
            seen.add(part.id)
        }

        for (part in existing) {
            if (part.id !in seen && part::class !in HIDDEN_PART_TYPES) {
                result.add(part)
            }
        }
        return result
    }

    // === Cleanup ===

    fun clearForServer(sessionIds: Set<String>) {
        flushPendingDeltas()
        pendingDeltas.clear()
        flushJob?.cancel()
        flushJob = null

        val messages = host.messagesFlow.value.toMutableMap()
        val parts = host.partsFlow.value.toMutableMap()
        sessionIds.forEach { sid ->
            messages.remove(sid)
        }
        host.messagesFlow.value = messages
        host.partsFlow.value = parts
    }

    fun clearAll() {
        flushJob?.cancel()
        flushJob = null
        pendingDeltas.clear()
        host.messagesFlow.value = emptyMap()
        host.partsFlow.value = emptyMap()
    }

    companion object {
        private const val TAG = "MessageReducer"
    }
}
