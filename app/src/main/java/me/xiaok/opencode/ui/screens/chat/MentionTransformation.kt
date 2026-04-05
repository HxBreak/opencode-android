package me.xiaok.opencode.ui.screens.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * VisualTransformation that renders @mentions (file paths and agent names)
 * as inline chips with colored backgrounds in the TextField.
 *
 * Matches the pattern `@\S+` (same regex as detection) and applies
 * distinct visual styles:
 * - File mentions: blue-tinted background
 * - Agent mentions: purple-tinted background
 *
 * Since we cannot know the mention types from text alone, we accept
 * a set of known mention display texts to determine styling.
 */
class MentionTransformation(
    private val mentionDisplayTexts: Set<String>,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val plainText = text.text
        val annotated = buildAnnotatedString {
            val mentionPattern = Regex("""@\S+""")
            var lastIndex = 0

            for (match in mentionPattern.findAll(plainText)) {
                // Append text before this mention
                append(plainText.substring(lastIndex, match.range.first))

                val mentionText = match.value
                val isKnownMention = mentionText in mentionDisplayTexts

                if (isKnownMention) {
                    // Determine if this looks like a file (contains / or .) vs agent
                    val isFile = mentionText.contains("/") || mentionText.substringAfter("@").contains(".")
                    val bgColor = if (isFile) {
                        Color(0xFF2196F3).copy(alpha = 0.15f)   // Blue tint for files
                    } else {
                        Color(0xFF9C27B0).copy(alpha = 0.15f)   // Purple tint for agents
                    }

                    withStyle(
                        SpanStyle(
                            background = bgColor,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    ) {
                        append(mentionText)
                    }
                } else {
                    // Not a recognized mention — render as plain text
                    append(mentionText)
                }

                lastIndex = match.range.last + 1
            }

            // Append remaining text
            if (lastIndex < plainText.length) {
                append(plainText.substring(lastIndex))
            }
        }

        return TransformedText(
            text = annotated,
            offsetMapping = OffsetMapping.Identity,
        )
    }
}
