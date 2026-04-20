package me.xiaok.opencode.ui.screens.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.xiaok.opencode.domain.model.FileContent

// ---------------------------------------------------------------------------
// Route: wires ViewModel to the stateless FileBrowserScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserRoute(
    serverId: String,
    sessionId: String?,
    directory: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FileBrowserScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateUp = { viewModel.navigateUp() },
        onDirectoryClick = { viewModel.loadDirectory(it) },
        onFileClick = { viewModel.loadFileContent(it) },
        onNavigateToPath = { viewModel.loadDirectory(it) },
        onSearchContent = { viewModel.searchContent(it) },
        onSearchFiles = { viewModel.searchFiles(it) },
        onClearSearch = { viewModel.clearSearch() },
        onClearError = { viewModel.clearError() },
        onBackFromViewer = {
            viewModel.loadDirectory(uiState.currentPath)
        },
    )
}

// ---------------------------------------------------------------------------
// Stateless FileBrowserScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    uiState: FileBrowserUiState,
    onNavigateBack: () -> Unit,
    onNavigateUp: () -> Unit,
    onDirectoryClick: (path: String) -> Unit,
    onFileClick: (path: String) -> Unit,
    onNavigateToPath: (path: String) -> Unit,
    onSearchContent: (pattern: String) -> Unit,
    onSearchFiles: (query: String) -> Unit,
    onClearSearch: () -> Unit,
    onClearError: () -> Unit,
    onBackFromViewer: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Show error as snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            onClearError()
        }
    }

    // Determine display mode
    val isViewingFile = uiState.viewingFilePath != null && uiState.fileContent != null

    // Navigate up when not at root; close page when at root
    val handleBack: () -> Unit = {
        if (uiState.currentPath != "." && uiState.currentPath != "/") {
            onNavigateUp()
        } else {
            onNavigateBack()
        }
    }

    // Intercept system back button in directory browsing mode
    if (!isViewingFile && !isSearchMode) {
        BackHandler(onBack = handleBack)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isViewingFile) {
                FileViewerTopBar(
                    filePath = uiState.viewingFilePath.orEmpty(),
                    onBack = onBackFromViewer,
                    scrollBehavior = scrollBehavior,
                )
            } else if (isSearchMode) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        if (selectedTab == 0) onSearchContent(it)
                        else onSearchFiles(it)
                    },
                    onClose = {
                        isSearchMode = false
                        searchQuery = ""
                        onClearSearch()
                    },
                    scrollBehavior = scrollBehavior,
                )
            } else {
                DirectoryTopBar(
                    currentPath = uiState.currentPath,
                    onBack = handleBack,
                    onSearch = { isSearchMode = true },
                    onNavigateToPath = onNavigateToPath,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isViewingFile -> {
                    FileContentViewer(
                        fileContent = uiState.fileContent ?: FileContent(),
                        filePath = uiState.viewingFilePath,
                    )
                }
                isSearchMode -> {
                    SearchResultsView(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        searchQuery = searchQuery,
                        contentResults = uiState.searchResults,
                        fileResults = uiState.fileNameResults,
                        isSearching = uiState.isSearching,
                        onFileClick = onFileClick,
                    )
                }
                else -> {
                    DirectoryBrowserView(
                        uiState = uiState,
                        onNavigateUp = onNavigateUp,
                        onDirectoryClick = onDirectoryClick,
                        onFileClick = onFileClick,
                    )
                }
            }

            // Loading overlay
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

