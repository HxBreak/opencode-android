package me.xiaok.opencode.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.domain.model.ToolState

// ---------------------------------------------------------------------------
// Tool status colors
// ---------------------------------------------------------------------------

internal val ColorToolPending = Color(0xFFFFA000)
internal val ColorToolRunning = Color(0xFF42A5F5)
internal val ColorToolCompleted = Color(0xFF66BB6A)
internal val ColorToolError = Color(0xFFE53935)

// ---------------------------------------------------------------------------
// Tool type registry
// ---------------------------------------------------------------------------

internal data class ToolTypeInfo(
    val emoji: String,
    val displayName: String,
    val hasDetails: Boolean,
    val deferContent: Boolean = false,
)

internal fun getToolTypeInfo(toolName: String): ToolTypeInfo = when (toolName) {
    "bash" -> ToolTypeInfo("\uD83D\uDCBB", "Shell", hasDetails = true)
    "grep" -> ToolTypeInfo("\uD83D\uDD0D", "Grep", hasDetails = true)
    "glob" -> ToolTypeInfo("\uD83D\uDD0D", "Glob", hasDetails = true)
    "list" -> ToolTypeInfo("\uD83D\uDCC2", "List", hasDetails = true)
    "find" -> ToolTypeInfo("\uD83D\uDD0D", "Find", hasDetails = true)
    "read" -> ToolTypeInfo("\uD83D\uDC53", "Read", hasDetails = false)
    "edit" -> ToolTypeInfo("\u270F\uFE0F", "Edit", hasDetails = true, deferContent = true)
    "write" -> ToolTypeInfo("\uD83D\uDCDD", "Write", hasDetails = true, deferContent = true)
    "apply_patch" -> ToolTypeInfo("\uD83E\uDDF1", "Patch", hasDetails = true, deferContent = true)
    "webfetch" -> ToolTypeInfo("\uD83C\uDF10", "Fetch", hasDetails = false)
    "websearch" -> ToolTypeInfo("\uD83D\uDD0E", "Search", hasDetails = true)
    "codesearch" -> ToolTypeInfo("\uD83D\uDD0E", "CodeSearch", hasDetails = true)
    "task" -> ToolTypeInfo("\uD83D\uDCCB", "Task", hasDetails = false)
    "skill" -> ToolTypeInfo("\u2699\uFE0F", "Skill", hasDetails = false)
    "question" -> ToolTypeInfo("\u2753", "Question", hasDetails = true)
    else -> ToolTypeInfo("\uD83D\uDD27", toolName, hasDetails = true)
}

// ---------------------------------------------------------------------------
// Argument extraction helpers
// ---------------------------------------------------------------------------

internal fun extractSubtitle(toolName: String, input: JsonElement?): String {
    val obj = input as? JsonObject ?: return ""
    return when (toolName) {
        // bash: subtitle = description (not command), matching web frontend
        "bash" -> obj.stringField("description")
        // grep: subtitle = directory path; pattern/include shown as arg tags
        "grep" -> getDirectory(obj.stringField("path"))
        // glob: subtitle = directory path; pattern shown as arg tag
        "glob" -> getDirectory(obj.stringField("path"))
        "list" -> getDirectory(obj.stringField("path"))
        "find" -> getDirectory(obj.stringField("path"))
        // read: subtitle = filename; offset/limit shown as arg tags
        "read" -> getFilename(obj.stringField("filePath"))
        "edit", "write" -> getFilename(
            obj.stringField("filePath").ifEmpty { obj.stringField("file_path") }
        )
        "apply_patch" -> getFilename(
            obj.stringField("path").ifEmpty { obj.stringField("file_path") }
        )
        "webfetch" -> obj.stringField("url")
        "websearch", "codesearch" -> obj.stringField("query")
        // task: subtitle = description or subagent_type
        "task" -> {
            val desc = obj.stringField("description")
            if (desc.isNotEmpty()) desc else obj.stringField("subagent_type")
        }
        // skill: subtitle = skill name from input
        "skill" -> obj.stringField("name")
        else -> buildString {
            val desc = obj.stringField("description")
            if (desc.isNotEmpty()) { append(desc); return@buildString }
            val q = obj.stringField("query")
            if (q.isNotEmpty()) { append(q); return@buildString }
            val url = obj.stringField("url")
            if (url.isNotEmpty()) { append(url); return@buildString }
            val fp = obj.stringField("filePath")
                .ifEmpty { obj.stringField("file_path") }
                .ifEmpty { obj.stringField("path") }
            if (fp.isNotEmpty()) append(fp)
        }
    }
}

/** Extract last path segment as filename, matching web's getFilename(). */
private fun getFilename(path: String): String {
    if (path.isEmpty()) return ""
    return path.trimEnd('/').substringAfterLast('/')
}

/** Extract directory portion of path, matching web's getDirectory(). */
private fun getDirectory(path: String): String {
    if (path.isEmpty()) return "/"
    val trimmed = path.trimEnd('/')
    val lastSlash = trimmed.lastIndexOf('/')
    return if (lastSlash > 0) trimmed.substring(lastSlash + 1) else trimmed
}

/**
 * Extract argument tags per tool type, matching web's trigger.args pattern.
 * Web shows pattern/include/offset/limit as small arg pills in the header.
 */
internal fun extractArgTags(toolName: String, input: JsonElement?): List<Pair<String, String>> {
    val obj = input as? JsonObject ?: return emptyList()
    val tags = mutableListOf<Pair<String, String>>()
    when (toolName) {
        "glob" -> {
            val pattern = obj.stringField("pattern")
            if (pattern.isNotEmpty()) tags += "pattern" to pattern.take(30)
        }
        "grep" -> {
            val pattern = obj.stringField("pattern")
            if (pattern.isNotEmpty()) tags += "pattern" to pattern.take(30)
            val include = obj.stringField("include")
            if (include.isNotEmpty()) tags += "include" to include.take(20)
        }
        "read" -> {
            val offset = obj.intField("offset")
            if (offset != null) tags += "offset" to offset.toString()
            val limit = obj.intField("limit")
            if (limit != null) tags += "limit" to limit.toString()
        }
        else -> {
            // Generic: pick up to 3 interesting string fields
            val exclude = setOf("command", "description", "timeout", "workdir",
                "filePath", "file_path", "path", "url", "query", "pattern")
            for ((key, value) in obj) {
                if (tags.size >= 3) break
                if (key in exclude) continue
                val strValue = value.stringValue()
                if (strValue != null && strValue.length <= 40) {
                    tags += key to strValue
                }
            }
        }
    }
    return tags
}

private fun JsonObject.stringField(key: String): String {
    val el = this[key] ?: return ""
    return el.stringValue() ?: ""
}

private fun JsonObject.intField(key: String): Int? {
    val el = this[key] ?: return null
    return try { el.jsonPrimitive.int } catch (_: Exception) { null }
}

private fun JsonElement.stringValue(): String? = try {
    jsonPrimitive.content
} catch (_: Exception) { null }

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

internal fun toolStatusInfo(state: ToolState): Pair<Color, String> = when {
    state.isPending -> ColorToolPending to "Pending"
    state.isRunning -> ColorToolRunning to "Running"
    state.isCompleted -> ColorToolCompleted to "Completed"
    state.isError -> ColorToolError to "Error"
    else -> Color(0xFF9E9E9E) to state.status.replaceFirstChar { it.uppercase() }
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
