package me.xiaok.opencode.ui.screens.chat

import androidx.annotation.VisibleForTesting
import me.xiaok.opencode.domain.model.Message
import me.xiaok.opencode.domain.model.MessageInfo
import me.xiaok.opencode.domain.model.Part

@VisibleForTesting
internal fun groupMessagesIntoTurns(
    messages: List<Message>,
    parts: Map<String, List<Part>> = emptyMap(),
): List<ChatTurn> {
    val turns = mutableListOf<ChatTurn>()
    var currentTurn: ChatTurn? = null
    // Track the last synthetic turn index so we can merge orphan assistants
    // that share the same parentID into one synthetic turn.
    var lastSyntheticIndex = -1

    for (msg in messages) {
        when {
            msg.isUser -> {
                currentTurn?.let { turns.add(computeTurnRenderData(it, parts)) }
                currentTurn = ChatTurn(userMessage = msg)
                lastSyntheticIndex = -1
            }
            msg.isAssistant && msg.info.parentID == currentTurn?.userMessage?.id -> {
                currentTurn = currentTurn?.copy(
                    assistantMessages = currentTurn.assistantMessages + msg
                )
            }
            else -> {
                // Orphan assistant: parentID mismatch or no current turn.
                // The user message this assistant replies to hasn't been loaded yet
                // (it's in an older page). Create a synthetic turn so the assistant
                // content is still visible, and merge with the previous synthetic turn
                // when they share the same parentID.
                currentTurn?.let { turns.add(computeTurnRenderData(it, parts)) }
                currentTurn = null

                val orphanParentId = msg.info.parentID
                if (lastSyntheticIndex >= 0 && orphanParentId != null
                    && turns[lastSyntheticIndex].assistantMessages.firstOrNull()?.info?.parentID == orphanParentId
                ) {
                    // Same orphan group — append to existing synthetic turn
                    val existing = turns[lastSyntheticIndex]
                    turns[lastSyntheticIndex] = computeTurnRenderData(
                        existing.copy(assistantMessages = existing.assistantMessages + msg),
                        parts,
                    )
                } else {
                    // New orphan group — create synthetic turn
                    val syntheticUser = Message(info = MessageInfo(role = "user"))
                    val syntheticTurn = ChatTurn(
                        userMessage = syntheticUser,
                        turnId = "synthetic_${msg.id}",
                        assistantMessages = listOf(msg),
                    )
                    turns.add(computeTurnRenderData(syntheticTurn, parts))
                    lastSyntheticIndex = turns.lastIndex
                }
            }
        }
    }
    currentTurn?.let { turns.add(computeTurnRenderData(it, parts)) }
    return turns
}

private fun computeTurnRenderData(turn: ChatTurn, parts: Map<String, List<Part>>): ChatTurn {
    val allAssistantParts = buildList {
        for (msg in turn.assistantMessages) {
            val msgParts = parts[msg.id] ?: msg.parts
            for (part in msgParts) {
                if (renderable(part)) {
                    add(PartRef(messageId = msg.id, partId = part.id) to part)
                }
            }
        }
    }

    val grouped = groupTurnParts(allAssistantParts)
    val partLookup = allAssistantParts.associate { (ref, part) -> ref to part }

    val userParts = (parts[turn.userMessage.id] ?: turn.userMessage.parts)
        .ifEmpty { turn.userMessage.parts }
    val isCompactionOnly = userParts.isNotEmpty() && userParts.all { it is Part.Compaction }
    val isSyntheticUser = turn.userMessage.id.isEmpty()

    val isActivelyReasoning = turn.assistantMessages.lastOrNull()?.let { lastMsg ->
        val lastMsgParts = parts[lastMsg.id] ?: lastMsg.parts
        lastMsgParts.any { it is Part.Reasoning } &&
            !lastMsgParts.any { it is Part.Text && it.text.isNotBlank() }
    } ?: false

    return turn.copy(
        groupedParts = grouped,
        partLookup = partLookup,
        isCompactionOnly = isCompactionOnly,
        userParts = userParts,
        isSyntheticUser = isSyntheticUser,
        isActivelyReasoning = isActivelyReasoning,
    )
}

/**
 * Extracts all copyable text from a [ChatTurn], combining user message text
 * and assistant response text. Returns empty string if no text parts found.
 */
internal fun extractTurnCopyText(turn: ChatTurn): String {
    val userText = turn.userParts
        .filterIsInstance<Part.Text>()
        .joinToString("\n") { it.text }
    val assistantText = turn.partLookup.values
        .filterIsInstance<Part.Text>()
        .joinToString("\n") { it.text }
    return listOf(userText, assistantText)
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
}
