package me.xiaok.opencode.ui.screens.tooldetail

import me.xiaok.opencode.domain.model.ToolState

/**
 * Cached tool call data for navigating from ChatScreen to ToolDetailScreen.
 */
data class CachedToolData(
    val toolName: String,
    val state: ToolState,
    val childSessionId: String? = null,
)

/**
 * Temporary in-memory cache for passing tool call data from ChatScreen to ToolDetailScreen.
 * The caller (ChatScreen) stores the data before navigating, and ToolDetailViewModel reads it by partId.
 *
 * This avoids serializing complex ToolState (with JsonElement fields) through navigation arguments.
 * Entries are never removed — each partId maps to a unique tool call whose data is immutable once completed.
 */
object ToolDetailCache {
    private val cache = mutableMapOf<String, CachedToolData>()

    fun put(partId: String, data: CachedToolData) {
        cache[partId] = data
    }

    fun get(partId: String): CachedToolData? = cache[partId]
}
