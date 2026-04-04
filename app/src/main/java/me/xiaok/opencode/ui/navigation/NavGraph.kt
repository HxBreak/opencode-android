package me.xiaok.opencode.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import me.xiaok.opencode.ui.components.common.OfflineBanner
import me.xiaok.opencode.ui.screens.about.AboutRoute
import me.xiaok.opencode.ui.screens.chat.ChatRoute
import me.xiaok.opencode.ui.screens.diff.DiffViewerRoute
import me.xiaok.opencode.ui.screens.errorlog.ErrorLogRoute
import me.xiaok.opencode.ui.screens.experimental.ExperimentalRoute
import me.xiaok.opencode.ui.screens.files.FileBrowserRoute
import me.xiaok.opencode.ui.screens.home.HomeRoute
import me.xiaok.opencode.ui.screens.iconpreview.IconPreviewRoute
import me.xiaok.opencode.ui.screens.projects.ProjectListRoute
import me.xiaok.opencode.ui.screens.server.McpManagementRoute
import me.xiaok.opencode.ui.screens.server.ServerModelFilterRoute
import me.xiaok.opencode.ui.screens.server.ServerProvidersRoute
import me.xiaok.opencode.ui.screens.server.ProjectConfigRoute
import me.xiaok.opencode.ui.screens.terminal.TerminalRoute
import me.xiaok.opencode.ui.screens.server.ServerSettingsRoute
import me.xiaok.opencode.ui.screens.sessions.SessionListRoute
import me.xiaok.opencode.ui.screens.settings.SettingsRoute
import me.xiaok.opencode.ui.screens.tooldetail.ToolDetailRoute
import me.xiaok.opencode.utils.NetworkMonitor

@Composable
fun OpenCodeNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    networkMonitor: NetworkMonitor = hiltViewModel<OfflineViewModel>().networkMonitor,
) {
    val isOffline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val isOfflineState = !isOffline

    Column(modifier = modifier.fillMaxSize()) {
        OfflineBanner(isOffline = isOfflineState)

        NavHost(
            navController = navController,
            startDestination = Screen.Home,
            modifier = Modifier.weight(1f),
            // Global defaults: forward/backward slide for hierarchical navigation
            enterTransition = { ScreenTransitions.forwardEnter },
            exitTransition = { ScreenTransitions.forwardExit },
            popEnterTransition = { ScreenTransitions.backwardEnter },
            popExitTransition = { ScreenTransitions.backwardExit },
        ) {
        // === Home: Server Management (Entry Page) ===
        composable<Screen.Home> {
            HomeRoute(
                onNavigateToProjects = { serverId ->
                    navController.navigate(Screen.ProjectList(serverId))
                },
                onNavigateToServerSettings = { serverId ->
                    navController.navigate(Screen.ServerSettings(serverId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
            )
        }

        // === Project List ===
        composable<Screen.ProjectList> { backStackEntry ->
            val projectList: Screen.ProjectList = backStackEntry.toRoute()
            ProjectListRoute(
                serverId = projectList.serverId,
                onNavigateToSessions = { serverId, directory ->
                    navController.navigate(Screen.SessionList(serverId, directory))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Session List ===
        composable<Screen.SessionList>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "opencode://sessions/{serverId}" }
            )
        ) { backStackEntry ->
            val sessionList: Screen.SessionList = backStackEntry.toRoute()
            SessionListRoute(
                serverId = sessionList.serverId,
                onNavigateToChat = { serverId, sessionId ->
                    navController.navigate(Screen.Chat(serverId, sessionId))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Chat (core — fade through for sub-session swaps) ===
        composable<Screen.Chat>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "opencode://session/{serverId}/{sessionId}" }
            ),
            enterTransition = {
                val fromRoute = initialState.destination.route
                if (fromRoute != null && fromRoute.startsWith("me.xiaok.opencode.ui.navigation.Screen.Chat")) {
                    ScreenTransitions.fadeThroughEnter
                } else {
                    ScreenTransitions.forwardEnter
                }
            },
            popExitTransition = {
                val toRoute = targetState.destination.route
                if (toRoute != null && toRoute.startsWith("me.xiaok.opencode.ui.navigation.Screen.Chat")) {
                    ScreenTransitions.fadeThroughExit
                } else {
                    ScreenTransitions.backwardExit
                }
            },
        ) { backStackEntry ->
            val chat: Screen.Chat = backStackEntry.toRoute()
            ChatRoute(
                serverId = chat.serverId,
                sessionId = chat.sessionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTerminal = {
                    navController.navigate(Screen.Terminal(chat.serverId, chat.sessionId))
                },
                onNavigateToSession = { subSessionId ->
                    navController.navigate(Screen.Chat(chat.serverId, subSessionId))
                },
                onNavigateToNewSession = {
                    navController.navigate(Screen.SessionList(chat.serverId))
                },
                onNavigateToSessionList = {
                    navController.popBackStack()
                },
                onNavigateToFiles = {
                    navController.navigate(Screen.FileBrowser(chat.serverId, chat.sessionId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
                onNavigateToMcp = {
                    navController.navigate(Screen.McpManagement(chat.serverId))
                },
                onNavigateToToolDetail = { partId ->
                    navController.navigate(Screen.ToolDetail(chat.serverId, chat.sessionId, partId))
                },
            )
        }

        // === Tool Detail (pushed from Chat) ===
        composable<Screen.ToolDetail> { backStackEntry ->
            val toolDetail: Screen.ToolDetail = backStackEntry.toRoute()
            ToolDetailRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { subSessionId ->
                    navController.navigate(Screen.Chat(toolDetail.serverId, subSessionId))
                },
            )
        }

        // === Terminal (overlay — slides up from bottom) ===
        composable<Screen.Terminal>(
            enterTransition = { ScreenTransitions.slideUpEnter },
            popExitTransition = { ScreenTransitions.slideDownExit },
        ) { backStackEntry ->
            val terminal: Screen.Terminal = backStackEntry.toRoute()
            TerminalRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === File Browser (overlay — slides up from bottom) ===
        composable<Screen.FileBrowser>(
            enterTransition = { ScreenTransitions.slideUpEnter },
            popExitTransition = { ScreenTransitions.slideDownExit },
        ) { backStackEntry ->
            val files: Screen.FileBrowser = backStackEntry.toRoute()
            FileBrowserRoute(
                serverId = files.serverId,
                sessionId = files.sessionId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Server Settings ===
        composable<Screen.ServerSettings> { backStackEntry ->
            val settings: Screen.ServerSettings = backStackEntry.toRoute()
            ServerSettingsRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProviders = {
                    navController.navigate(Screen.ServerProviders(settings.serverId))
                },
                onNavigateToModelFilter = {
                    navController.navigate(Screen.ServerModelFilter(settings.serverId))
                },
                onNavigateToMcpManagement = {
                    navController.navigate(Screen.McpManagement(settings.serverId))
                },
                onNavigateToExperimental = {
                    navController.navigate(Screen.Experimental(settings.serverId))
                },
                onNavigateToProjectConfig = {
                    navController.navigate(Screen.ProjectConfig(settings.serverId))
                },
            )
        }

        // === Server Providers ===
        composable<Screen.ServerProviders> { backStackEntry ->
            val providers: Screen.ServerProviders = backStackEntry.toRoute()
            ServerProvidersRoute(
                serverId = providers.serverId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Server Model Filter ===
        composable<Screen.ServerModelFilter> { backStackEntry ->
            val filter: Screen.ServerModelFilter = backStackEntry.toRoute()
            ServerModelFilterRoute(
                serverId = filter.serverId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === MCP Management ===
        composable<Screen.McpManagement> {
            McpManagementRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Experimental ===
        composable<Screen.Experimental> {
            ExperimentalRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Project Config ===
        composable<Screen.ProjectConfig> {
            ProjectConfigRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === App Settings ===
        composable<Screen.Settings>(
            deepLinks = listOf(
                navDeepLink { uriPattern = "opencode://settings" }
            )
        ) {
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToIconPreview = {
                    navController.navigate(Screen.IconPreview)
                },
                onNavigateToErrorLog = {
                    navController.navigate(Screen.ErrorLog)
                },
            )
        }

        // === About ===
        composable<Screen.About> {
            AboutRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Diff Viewer ===
        composable<Screen.DiffViewer> { backStackEntry ->
            val diffViewer: Screen.DiffViewer = backStackEntry.toRoute()
            DiffViewerRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Icon Preview (Debug) ===
        composable<Screen.IconPreview> {
            IconPreviewRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // === Error Log ===
        composable<Screen.ErrorLog> {
            ErrorLogRoute(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        }
    }
}
