package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a mention item (@file or @agent) embedded in the chat input text.
 *
 * Unlike the web frontend which uses contenteditable pills, the Android app
 * stores mentions as structured data alongside the plain text. The text field
 * contains the display text (e.g. "@src/foo.ts"), while MentionItem tracks
 * the semantic meaning (file path vs agent name) and position for:
 * - VisualTransformation chip rendering
 * - Building structured API parts on send
 * - Draft persistence
 */
@Serializable
sealed class MentionItem {
    abstract val displayText: String
    abstract val start: Int
    abstract val end: Int

    @Serializable
    data class FileMention(
        val path: String,
        override val displayText: String,
        override val start: Int,
        override val end: Int,
    ) : MentionItem()

    @Serializable
    data class AgentMention(
        val name: String,
        override val displayText: String,
        override val start: Int,
        override val end: Int,
    ) : MentionItem()
}
