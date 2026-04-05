package me.xiaok.opencode.ui.screens.diff

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.DeltaType

/**
 * Data class representing a single line in a diff view.
 */
data class DiffLine(
    val content: String,
    val type: LineType,
    val oldLineNum: Int?,
    val newLineNum: Int?,
    /** Word-level inline change spans for character-level highlighting within a line. */
    val inlineSpans: List<InlineSpan> = emptyList(),
) {
    enum class LineType {
        CONTEXT,
        ADDITION,
        DELETION,
        HUNK_HEADER,
    }
}

/**
 * Represents a character range within a line that has changed (for inline highlighting).
 */
data class InlineSpan(
    val start: Int,
    val end: Int,
)

/**
 * Computes diff lines from before/after text using kotlin-multiplatform-diff.
 *
 * Unlike parsing raw unified diff text, this produces structured data that supports
 * word-level inline highlighting and accurate line number tracking.
 */
object DiffEngine {

    /** Number of context lines around each change block */
    private const val CONTEXT_LINES = 3

    /**
     * Compute unified diff lines from before/after file content.
     *
     * @param before Original file content (may be empty for new files)
     * @param after  Modified file content (may be empty for deleted files)
     * @param filePath File path for the diff header
     * @return List of [DiffLine] ready for rendering
     */
    fun computeDiff(before: String, after: String, filePath: String = ""): List<DiffLine> {
        val originalLines = if (before.isEmpty()) emptyList() else before.lines()
        val revisedLines = if (after.isEmpty()) emptyList() else after.lines()

        // Handle edge cases
        if (originalLines.isEmpty() && revisedLines.isEmpty()) return emptyList()
        if (originalLines.isEmpty()) {
            // All additions (new file)
            return revisedLines.mapIndexed { index, line ->
                DiffLine(
                    content = line,
                    type = DiffLine.LineType.ADDITION,
                    oldLineNum = null,
                    newLineNum = index + 1,
                )
            }
        }
        if (revisedLines.isEmpty()) {
            // All deletions (deleted file)
            return originalLines.mapIndexed { index, line ->
                DiffLine(
                    content = line,
                    type = DiffLine.LineType.DELETION,
                    oldLineNum = index + 1,
                    newLineNum = null,
                )
            }
        }

        // Compute the diff patch
        val patch = DiffUtils.diff(originalLines, revisedLines)

        // Generate unified diff for display
        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            "a/$filePath",
            "b/$filePath",
            originalLines,
            patch,
            CONTEXT_LINES,
        )

        // Parse unified diff output into structured lines
        return parseUnifiedDiff(unifiedDiff)
    }

    /**
     * Parse unified diff output into structured DiffLines with line numbers.
     */
    private fun parseUnifiedDiff(unifiedDiffLines: List<String>): List<DiffLine> {
        val result = mutableListOf<DiffLine>()
        var oldLine = 0
        var newLine = 0

        val hunkHeaderRegex = Regex("^@@\\s+-(\\d+)(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@")

        for (line in unifiedDiffLines) {
            when {
                line.startsWith("---") || line.startsWith("+++") -> {
                    // Skip file headers
                    continue
                }
                line.startsWith("@@") -> {
                    val match = hunkHeaderRegex.find(line)
                    if (match != null) {
                        oldLine = match.groupValues[1].toIntOrNull() ?: 0
                        newLine = match.groupValues[2].toIntOrNull() ?: 0
                    }
                    result.add(
                        DiffLine(
                            content = line,
                            type = DiffLine.LineType.HUNK_HEADER,
                            oldLineNum = null,
                            newLineNum = null,
                        )
                    )
                }
                line.startsWith("+") -> {
                    result.add(
                        DiffLine(
                            content = line.removePrefix("+"),
                            type = DiffLine.LineType.ADDITION,
                            oldLineNum = null,
                            newLineNum = newLine,
                        )
                    )
                    newLine++
                }
                line.startsWith("-") -> {
                    result.add(
                        DiffLine(
                            content = line.removePrefix("-"),
                            type = DiffLine.LineType.DELETION,
                            oldLineNum = oldLine,
                            newLineNum = null,
                        )
                    )
                    oldLine++
                }
                else -> {
                    // Context line (may start with a space)
                    val content = line.removePrefix(" ")
                    result.add(
                        DiffLine(
                            content = content,
                            type = DiffLine.LineType.CONTEXT,
                            oldLineNum = oldLine,
                            newLineNum = newLine,
                        )
                    )
                    if (oldLine > 0) oldLine++
                    if (newLine > 0) newLine++
                }
            }
        }

        return result
    }

    /**
     * Compute word-level inline changes for paired add/delete lines.
     *
     * Given a deletion line and the next addition line, finds the character ranges
     * that changed, enabling inline highlighting (like GitHub's word diff).
     *
     * @param oldLine The deleted line content
     * @param newLine The added line content
     * @return Pair of (deletionSpans, additionSpans) — character ranges that changed
     */
    fun computeWordDiff(oldLine: String, newLine: String): Pair<List<InlineSpan>, List<InlineSpan>> {
        val oldWords = oldLine.split(Regex("(?<=\\s)|(?=\\s)"))
        val newWords = newLine.split(Regex("(?<=\\s)|(?=\\s)"))

        val patch = DiffUtils.diff(oldWords.toList(), newWords.toList())

        val deletionSpans = mutableListOf<InlineSpan>()
        val additionSpans = mutableListOf<InlineSpan>()

        for (delta in patch.deltas) {
            when (delta.type) {
                DeltaType.DELETE -> {
                    val start = oldWords.take(delta.source.position).sumOf { it.length }
                    val end = start + oldWords.drop(delta.source.position).take(delta.source.size()).sumOf { it.length }
                    if (start != end) deletionSpans.add(InlineSpan(start, end))
                }
                DeltaType.INSERT -> {
                    val start = newWords.take(delta.target.position).sumOf { it.length }
                    val end = start + newWords.drop(delta.target.position).take(delta.target.size()).sumOf { it.length }
                    if (start != end) additionSpans.add(InlineSpan(start, end))
                }
                DeltaType.CHANGE -> {
                    val delStart = oldWords.take(delta.source.position).sumOf { it.length }
                    val delEnd = delStart + oldWords.drop(delta.source.position).take(delta.source.size()).sumOf { it.length }
                    if (delStart != delEnd) deletionSpans.add(InlineSpan(delStart, delEnd))

                    val addStart = newWords.take(delta.target.position).sumOf { it.length }
                    val addEnd = addStart + newWords.drop(delta.target.position).take(delta.target.size()).sumOf { it.length }
                    if (addStart != addEnd) additionSpans.add(InlineSpan(addStart, addEnd))
                }
                DeltaType.EQUAL -> { /* no change */ }
            }
        }

        return Pair(deletionSpans, additionSpans)
    }

    /**
     * Pair up consecutive delete/add lines for word-level diff.
     * Returns a map of (lineIndex -> inline spans) for each line that has inline changes.
     */
    fun computeInlineDiffs(diffLines: List<DiffLine>): Map<Int, Pair<List<InlineSpan>, List<InlineSpan>>> {
        val result = mutableMapOf<Int, Pair<List<InlineSpan>, List<InlineSpan>>>()
        var i = 0
        while (i < diffLines.size) {
            val current = diffLines[i]
            if (current.type == DiffLine.LineType.DELETION) {
                // Look ahead for paired addition lines
                val deleteBlock = mutableListOf<DiffLine>()
                var j = i
                while (j < diffLines.size && diffLines[j].type == DiffLine.LineType.DELETION) {
                    deleteBlock.add(diffLines[j])
                    j++
                }
                val addBlock = mutableListOf<DiffLine>()
                while (j < diffLines.size && diffLines[j].type == DiffLine.LineType.ADDITION) {
                    addBlock.add(diffLines[j])
                    j++
                }
                // Pair up delete/add lines and compute word diffs
                val pairCount = minOf(deleteBlock.size, addBlock.size)
                for (k in 0 until pairCount) {
                    val delIdx = i + k
                    val addIdx = i + deleteBlock.size + k
                    val (delSpans, addSpans) = computeWordDiff(deleteBlock[k].content, addBlock[k].content)
                    if (delSpans.isNotEmpty()) result[delIdx] = Pair(delSpans, emptyList())
                    if (addSpans.isNotEmpty()) result[addIdx] = Pair(emptyList(), addSpans)
                }
                i = j
            } else {
                i++
            }
        }
        return result
    }
}
