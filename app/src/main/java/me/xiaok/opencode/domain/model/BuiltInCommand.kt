package me.xiaok.opencode.domain.model

/**
 * Represents a built-in slash command that is handled client-side
 * rather than being sent to the server.
 *
 * These commands perform local actions: navigation, state changes,
 * or calling specific backend APIs (revert, share, summarize, etc.)
 */
data class BuiltInCommand(
    val name: String,
    val description: String,
    val group: String,
) {
    /**
     * Unique identifier for matching user input.
     * E.g. "undo", "compact", "theme.cycle"
     */
    val id: String = name
}

/**
 * Registry of all built-in commands available in the chat input.
 *
 * Commands are split into groups matching the Web frontend categorization.
 * Each command is filtered by availability (e.g. /redo only when session.revert != null).
 */
object BuiltInCommands {

    val all: List<BuiltInCommand> = listOf(
        // ── Session ──────────────────────────────────────────
        BuiltInCommand("new", "Create a new session", "Session"),
        BuiltInCommand("undo", "Undo last conversation turn", "Session"),
        BuiltInCommand("redo", "Redo previously undone turn", "Session"),
        BuiltInCommand("compact", "Summarize conversation history", "Session"),
        BuiltInCommand("share", "Share this session", "Session"),
        BuiltInCommand("unshare", "Remove session share link", "Session"),
        BuiltInCommand("fork", "Fork this session", "Session"),
        BuiltInCommand("archive", "Archive this session", "Session"),

        // ── Navigation ───────────────────────────────────────
        BuiltInCommand("sessions", "Go to session list", "Navigation"),
        BuiltInCommand("terminal", "Open terminal", "Navigation"),
        BuiltInCommand("files", "Browse files", "Navigation"),
        BuiltInCommand("settings", "Open settings", "Navigation"),
        BuiltInCommand("mcp", "Manage MCP servers", "Navigation"),

        // ── Model & Agent ────────────────────────────────────
        BuiltInCommand("model", "Change model", "Model"),
        BuiltInCommand("agent", "Switch agent", "Model"),
        BuiltInCommand("variant", "Cycle model variant (fast/think/agentic)", "Model"),

        // ── Theme ────────────────────────────────────────────
        BuiltInCommand("theme", "Cycle color scheme (light/dark/system)", "Theme"),
    )

    /**
     * Match user input (without leading "/") to a built-in command.
     * Supports exact match and prefix match.
     */
    fun match(query: String): BuiltInCommand? {
        if (query.isBlank()) return null
        return all.find { it.id == query }
            ?: all.find { it.id.startsWith(query) }
    }

    /**
     * Filter commands matching a query substring (for autocomplete).
     */
    fun filter(query: String): List<BuiltInCommand> {
        if (query.isBlank()) return all
        return all.filter { it.id.contains(query, ignoreCase = true) }
    }
}
