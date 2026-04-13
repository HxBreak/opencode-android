package me.xiaok.opencode.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes using sealed class hierarchy.
 * All routes are @Serializable for Compose Navigation type-safe routing.
 */
@Serializable
sealed class Screen {
    @Serializable
    data object Home : Screen()

    @Serializable
    data class SessionList(
        val serverId: String,
        val directory: String? = null,
    ) : Screen()

    @Serializable
    data class ProjectList(
        val serverId: String,
    ) : Screen()

    @Serializable
    data class Chat(
        val serverId: String,
        val sessionId: String,
    ) : Screen()

    @Serializable
    data class FileBrowser(
        val serverId: String,
        val sessionId: String? = null,
        val directory: String? = null,
    ) : Screen()

    @Serializable
    data class ServerSettings(
        val serverId: String,
    ) : Screen()

    @Serializable
    data class ServerProviders(
        val serverId: String,
    ) : Screen()

    @Serializable
    data class ServerModelFilter(
        val serverId: String,
    ) : Screen()

    @Serializable
    data class McpManagement(
        val serverId: String,
    ) : Screen()

    @Serializable
    data class Experimental(
        val serverId: String,
    ) : Screen()

    @Serializable
    data class ProjectConfig(
        val serverId: String,
    ) : Screen()

    @Serializable
    data object Settings : Screen()

    @Serializable
    data class Terminal(
        val serverId: String,
        val sessionId: String? = null,
        val ptyId: String? = null,
    ) : Screen()

    @Serializable
    data object About : Screen()

    @Serializable
    data class DiffViewer(
        val title: String? = null,
    ) : Screen()

    @Serializable
    data class SessionDiff(
        val serverId: String,
        val sessionId: String,
    ) : Screen()

    @Serializable
    data class ToolDetail(
        val serverId: String,
        val sessionId: String,
        val partId: String,
    ) : Screen()

    @Serializable
    data object ErrorLog : Screen()

    @Serializable
    data object FullScreenEditor : Screen()
}
