package me.xiaok.opencode.ui.screens.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.domain.model.FileContent
import me.xiaok.opencode.domain.model.FileNode
import me.xiaok.opencode.domain.model.FileStatus

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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
                    onBack = onNavigateBack,
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

// ---------------------------------------------------------------------------
// Top Bars
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryTopBar(
    currentPath: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            if (currentPath == ".") {
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            } else {
                BreadcrumbPath(
                    path = currentPath,
                    onPathClick = onNavigateToPath,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun BreadcrumbPath(
    path: String,
    onPathClick: (String) -> Unit,
) {
    val segments = path.split("/")
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Root
        Text(
            text = "root",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.clickable { onPathClick(".") },
        )

        segments.forEachIndexed { index, segment ->
            Text(
                text = " / ",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                ),
            )
            val targetPath = if (index == segments.lastIndex) path
            else segments.subList(0, index + 1).joinToString("/")
            val isLast = index == segments.lastIndex
            Text(
                text = segment,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.primary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (!isLast) Modifier.clickable { onPathClick(targetPath) } else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileViewerTopBar(
    filePath: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(
                text = filePath.substringAfterLast("/"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch(query) },
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                )
            }
        },
        actions = {
            IconButton(onClick = { onSearch(query) }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Execute search",
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

// ---------------------------------------------------------------------------
// Directory Browser
// ---------------------------------------------------------------------------

@Composable
private fun DirectoryBrowserView(
    uiState: FileBrowserUiState,
    onNavigateUp: () -> Unit,
    onDirectoryClick: (path: String) -> Unit,
    onFileClick: (path: String) -> Unit,
) {
    val statusMap = remember(uiState.fileStatuses) {
        uiState.fileStatuses.associateBy { it.path }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        // "Up" navigation item when not at root
        if (uiState.currentPath != "." && uiState.currentPath != "/") {
            item {
                NavigateUpItem(onClick = onNavigateUp)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }

        items(
            items = uiState.fileTree,
            key = { it.path },
        ) { node ->
            val status = statusMap[node.path]
            FileNodeItem(
                node = node,
                fileStatus = status,
                onClick = {
                    if (node.type == "directory") {
                        onDirectoryClick(node.path)
                    } else {
                        onFileClick(node.path)
                    }
                },
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 56.dp),
            )
        }

        // Empty state
        if (uiState.fileTree.isEmpty() && !uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Empty directory",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NavigateUpItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Navigate up",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "..",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileNodeItem(
    node: FileNode,
    fileStatus: FileStatus?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(
            fileName = node.name,
            type = node.type,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (node.type == "directory") FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.type == "file" && node.path != node.name) {
                Text(
                    text = node.path,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Git status badge
        if (fileStatus != null) {
            GitStatusBadge(status = fileStatus.status, added = fileStatus.added, removed = fileStatus.removed)
        }
    }
}

@Composable
private fun FileIcon(
    fileName: String,
    type: String,
    modifier: Modifier = Modifier,
) {
    if (type == "directory") {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = Color(0xFFE8A838),
            modifier = modifier,
        )
        return
    }

    val ext = fileName.substringAfterLast('.', "").lowercase()
    val (icon, tint) = when (ext) {
        "kt", "kts" -> Icons.Default.Description to Color(0xFFA97BFF)
        "java" -> Icons.Default.Description to Color(0xFFFF7043)
        "py" -> Icons.Default.Description to Color(0xFF4B8BBE)
        "js", "jsx" -> Icons.Default.Description to Color(0xFFF7DF1E)
        "ts", "tsx" -> Icons.Default.Description to Color(0xFF3178C6)
        "json", "yaml", "yml", "toml" -> Icons.Default.Settings to Color(0xFF8BC34A)
        "xml", "html", "svg" -> Icons.Default.Description to Color(0xFFE44D26)
        "css", "scss" -> Icons.Default.Description to Color(0xFF264DE4)
        "md", "markdown" -> Icons.Default.Description to Color(0xFF2196F3)
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp" -> Icons.Default.Image to Color(0xFF4CAF50)
        "gradle", "properties" -> Icons.Default.Settings to Color(0xFF607D8B)
        "sh", "bash", "zsh" -> Icons.Default.Description to Color(0xFF4EAA25)
        "sql" -> Icons.Default.Description to Color(0xFFFF9800)
        "gitignore", "dockerignore", "editorconfig" -> Icons.Default.Settings to Color(0xFF9E9E9E)
        else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

@Composable
private fun GitStatusBadge(status: String, added: Int, removed: Int) {
    val (backgroundColor, contentColor, label) = when (status) {
        "added" -> Triple(Color(0xFF4CAF50), Color.White, "+$added")
        "deleted" -> Triple(Color(0xFFF44336), Color.White, "-$removed")
        "modified" -> Triple(Color(0xFFFF9800), Color.White, "+$added/-$removed")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, status)
    }

    Box(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
            color = contentColor,
        )
    }
}

// ---------------------------------------------------------------------------
// File Content Viewer
// ---------------------------------------------------------------------------

private const val MAX_FILE_SIZE_CHARS = 100_000
private const val MAX_DISPLAY_LINES = 2000

@Composable
private fun FileContentViewer(fileContent: FileContent, filePath: String? = null) {
    when {
        fileContent.isImage && fileContent.encoding == "base64" -> {
            BinaryImagePreview(
                base64Data = fileContent.content,
                mimeType = fileContent.mimeType ?: "image/png",
            )
        }
        fileContent.isBinary -> {
            BinaryFilePlaceholder(
                mimeType = fileContent.mimeType,
                filePath = filePath,
            )
        }
        else -> {
            TextFileContentViewer(
                content = fileContent.content,
                filePath = filePath,
                diff = fileContent.diff,
            )
        }
    }
}

@Composable
private fun BinaryImagePreview(
    base64Data: String,
    mimeType: String,
) {
    val bitmap = remember(base64Data) {
        try {
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Failed to decode image",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BinaryFilePlaceholder(
    mimeType: String?,
    filePath: String?,
) {
    val ext = filePath?.substringAfterLast('.', "")?.uppercase() ?: "FILE"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$ext file",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (mimeType != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mimeType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Binary file preview not supported",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextFileContentViewer(
    content: String,
    filePath: String?,
    diff: String?,
) {
    val isLargeFile = content.length > MAX_FILE_SIZE_CHARS
    val lines = remember(content) { content.lines() }
    val displayLines = remember(lines, isLargeFile) {
        if (isLargeFile) lines.take(MAX_DISPLAY_LINES) else lines
    }
    val lineCountDigits = remember(displayLines) {
        displayLines.size.toString().length
    }
    val scrollState = rememberScrollState()
    val syntaxContent = remember(displayLines, filePath) {
        displayLines.map { line -> highlightSyntaxLine(line, filePath) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Truncation warning
        if (isLargeFile) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "File too large (${formatFileSize(content.length)}). Showing first $MAX_DISPLAY_LINES of ${lines.size} lines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Diff section
        if (!diff.isNullOrBlank()) {
            DiffSection(diff = diff)
        }

        // Code content with line numbers
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(scrollState),
        ) {
            SelectionContainer {
                Text(
                    text = buildAnnotatedString {
                        syntaxContent.forEachIndexed { index, line ->
                            // Line number
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace,
                                )
                            ) {
                                append(String.format("%${lineCountDigits}d  ", index + 1))
                            }
                            // Code content
                            append(line)
                            if (index < syntaxContent.lastIndex) append("\n")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.4,
                    ),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DiffSection(diff: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Git Diff",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = diff,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

private fun formatFileSize(charCount: Int): String {
    return when {
        charCount < 1_000 -> "$charCount B"
        charCount < 1_000_000 -> "${charCount / 1_000} KB"
        else -> "${"%.1f".format(charCount / 1_000_000.0)} MB"
    }
}

private fun highlightSyntaxLine(line: String, filePath: String?): AnnotatedString {
    val rules = getSyntaxRules(filePath)
    if (rules.isEmpty()) return AnnotatedString(line)

    data class MatchEntry(val start: Int, val end: Int, val style: SpanStyle)

    val matches = mutableListOf<MatchEntry>()
    for (rule in rules) {
        rule.pattern.findAll(line).forEach { result ->
            matches.add(MatchEntry(result.range.first, result.range.last + 1, rule.style))
        }
    }

    matches.sortBy { it.start }
    val filtered = mutableListOf<MatchEntry>()
    var lastEnd = 0
    for (m in matches) {
        if (m.start >= lastEnd) {
            filtered.add(m)
            lastEnd = m.end
        }
    }

    return buildAnnotatedString {
        var pos = 0
        for (m in filtered) {
            if (pos < m.start) {
                append(line.substring(pos, m.start))
            }
            pushStyle(m.style)
            append(line.substring(m.start, m.end))
            pop()
            pos = m.end
        }
        if (pos < line.length) {
            append(line.substring(pos))
        }
    }
}

// ---------------------------------------------------------------------------
// Syntax Highlighting
// ---------------------------------------------------------------------------

private data class SyntaxRule(
    val pattern: Regex,
    val style: SpanStyle,
)

private fun getSyntaxRules(filePath: String?): List<SyntaxRule> {
    val ext = filePath?.substringAfterLast('.', "")?.lowercase() ?: return emptyList()

    // Common styles
    val keywordStyle = SpanStyle(
        color = Color(0xFFC678DD), // purple
        fontWeight = FontWeight.Bold,
    )
    val stringStyle = SpanStyle(
        color = Color(0xFF98C379), // green
    )
    val commentStyle = SpanStyle(
        color = Color(0xFF5C6370), // gray
    )
    val numberStyle = SpanStyle(
        color = Color(0xFFD19A66), // orange
    )
    val annotationStyle = SpanStyle(
        color = Color(0xFFE5C07B), // yellow
    )
    val typeStyle = SpanStyle(
        color = Color(0xFF61AFEF), // blue
    )

    return when (ext) {
        "kt", "kts", "java" -> listOf(
            SyntaxRule(Regex("//.*$"), commentStyle),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentStyle),
            SyntaxRule(Regex("""\"\"\"[\s\S]*?\"\"\""""), stringStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(package|import|class|interface|object|enum|data|sealed|abstract|open|override|private|protected|public|internal|val|var|fun|suspend|inline|infix|operator|companion|init|constructor|if|else|when|for|while|do|try|catch|finally|throw|return|break|continue|is|in|as|typealias|typeof|true|false|null|this|super|it|lazy|by|crossinline|noinline|reified|tailrec|annotation|expect|actual|lateinit|field|property|receiver|param|setparam|get|set|delegate|dynamic|context)\b"""), keywordStyle),
            SyntaxRule(Regex("""@[A-Za-z]\w*"""), annotationStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*[fFLl]?\b"""), numberStyle),
            SyntaxRule(Regex("""\b(?:String|Int|Long|Boolean|Float|Double|List|Map|Set|Unit|Any|Nothing|Pair|Triple|Array|ByteArray|IntArray|Sequence|Flow|MutableStateFlow|StateFlow|LiveData|Bundle|Intent|Context|View|ViewModel|Composable)\b"""), typeStyle),
        )
        "py" -> listOf(
            SyntaxRule(Regex("#.*$"), commentStyle),
            SyntaxRule(Regex("\"\"\"[\\s\\S]*?\"\"\""), stringStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(def|class|async|await|import|from|return|if|elif|else|for|while|try|except|finally|with|raise|yield|lambda|pass|break|continue|and|or|not|in|is|True|False|None|self|global|nonlocal|assert|del)\b"""), keywordStyle),
            SyntaxRule(Regex("""@\w+"""), annotationStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*\b"""), numberStyle),
        )
        "js", "ts", "tsx", "jsx" -> listOf(
            SyntaxRule(Regex("//.*$"), commentStyle),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentStyle),
            SyntaxRule(Regex("""`[\s\S]*?`"""), stringStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|class|extends|implements|import|export|from|default|async|await|yield|typeof|instanceof|in|of|null|undefined|true|false|this|super|interface|type|enum|abstract|as|readonly|declare|module|namespace|public|private|protected|static|override|void)\b"""), keywordStyle),
            SyntaxRule(Regex("""@\w+"""), annotationStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*\b"""), numberStyle),
        )
        "json", "yaml", "yml", "toml" -> listOf(
            SyntaxRule(Regex("\"(?:[^\"\\\\]|\\\\.)*\"\\s*:"), typeStyle),
            SyntaxRule(Regex(":\\s*\"(?:[^\"\\\\]|\\\\.)*\""), stringStyle),
            SyntaxRule(Regex(":\\s*(?:true|false|null)\\b"), keywordStyle),
            SyntaxRule(Regex(":\\s*\\d+\\.?\\d*\\b"), numberStyle),
            SyntaxRule(Regex("#.*$"), commentStyle),
        )
        "xml", "html", "svg" -> listOf(
            SyntaxRule(Regex("<!--.*?-->"), commentStyle),
            SyntaxRule(Regex("</?[A-Za-z][\\w-]*"), keywordStyle),
            SyntaxRule(Regex("""\w+\s*=\s*"[^"]*""""), stringStyle),
            SyntaxRule(Regex(""""[^"]*""""), stringStyle),
        )
        "css", "scss" -> listOf(
            SyntaxRule(Regex("/\\*.*?\\*/"), commentStyle),
            SyntaxRule(Regex("""\b(?:color|background|margin|padding|border|display|position|width|height|font|text|flex|grid|gap|overflow|opacity|z-index|transition|animation|transform)\s*:"""), keywordStyle),
            SyntaxRule(Regex("""#[0-9a-fA-F]{3,8}\b"""), numberStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*(px|em|rem|%|vh|vw|dp|sp)?\b"""), numberStyle),
        )
        "md", "markdown" -> listOf(
            SyntaxRule(Regex("^#{1,6}\\s+.*$"), SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF61AFEF))),
            SyntaxRule(Regex("""`[^`]+`"""), SpanStyle(color = Color(0xFF98C379), fontFamily = FontFamily.Monospace)),
            SyntaxRule(Regex("""```[\s\S]*?```"""), SpanStyle(color = Color(0xFF98C379), fontFamily = FontFamily.Monospace)),
            SyntaxRule(Regex("""\*\*[^*]+\*\*"""), SpanStyle(fontWeight = FontWeight.Bold)),
            SyntaxRule(Regex("""\*[^*]+\*"""), SpanStyle(fontStyle = FontStyle.Italic)),
        )
        "sh", "bash", "zsh" -> listOf(
            SyntaxRule(Regex("#.*$"), commentStyle),
            SyntaxRule(Regex(""""(?:[^"\\]|\\.)*"""), stringStyle),
            SyntaxRule(Regex("""'(?:[^'\\]|\\.)*'"""), stringStyle),
            SyntaxRule(Regex("""\b(if|then|else|elif|fi|for|while|do|done|case|esac|in|function|return|exit|export|import|source|alias|echo|cd|ls|grep|sed|awk|find|cat|mkdir|rm|cp|mv|chmod|chown|sudo|apt|yum|npm|pip|docker|kubectl|git)\b"""), keywordStyle),
            SyntaxRule(Regex("""\$\{?\w+\}?"""), typeStyle),
            SyntaxRule(Regex("""\b\d+\b"""), numberStyle),
        )
        "sql" -> listOf(
            SyntaxRule(Regex("--.*$"), commentStyle),
            SyntaxRule(Regex("/\\*.*?\\*/"), commentStyle),
            SyntaxRule(Regex("""'[^']*'"""), stringStyle),
            SyntaxRule(Regex("""\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|ALTER|DROP|INDEX|JOIN|LEFT|RIGHT|INNER|OUTER|ON|AND|OR|NOT|IN|IS|NULL|LIKE|ORDER|BY|GROUP|HAVING|LIMIT|OFFSET|AS|DISTINCT|COUNT|SUM|AVG|MIN|MAX|EXISTS|BETWEEN|UNION|ALL|PRIMARY|KEY|FOREIGN|REFERENCES|CASCADE|CONSTRAINT|DEFAULT|AUTO_INCREMENT|INT|BIGINT|VARCHAR|TEXT|BOOLEAN|FLOAT|DOUBLE|DATE|TIMESTAMP|SERIAL)\b"""), keywordStyle),
            SyntaxRule(Regex("""\b\d+\.?\d*\b"""), numberStyle),
        )
        else -> emptyList()
    }
}

// ---------------------------------------------------------------------------
// Search Results View
// ---------------------------------------------------------------------------

@Composable
private fun SearchResultsView(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    contentResults: List<kotlinx.serialization.json.JsonElement>,
    fileResults: List<String>,
    isSearching: Boolean,
    onFileClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text("Content") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text("Files") },
            )
        }

        // Loading indicator
        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Results
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (selectedTab == 0) {
                if (contentResults.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
                    item {
                        EmptySearchResult(message = "No content matches found")
                    }
                }
                items(
                    items = contentResults,
                    key = { it.toString() },
                ) { result ->
                    ContentSearchResultItem(
                        result = result,
                        onClick = onFileClick,
                    )
                }
            } else {
                if (fileResults.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
                    item {
                        EmptySearchResult(message = "No files found")
                    }
                }
                items(
                    items = fileResults,
                    key = { it },
                ) { filePath ->
                    FileSearchResultItem(
                        filePath = filePath,
                        onClick = { onFileClick(filePath) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ContentSearchResultItem(
    result: kotlinx.serialization.json.JsonElement,
    onClick: (String) -> Unit,
) {
    // Parse the JsonElement to extract file path and matching line
    val filePath = (result as? JsonObject)
        ?.get("path")
        ?.let { (it as? JsonPrimitive)?.content }
        ?: ""

    val line = (result as? JsonObject)
        ?.get("line")
        ?.let { (it as? JsonPrimitive)?.content }
        ?: ""

    val lineNum = (result as? JsonObject)
        ?.get("line_number")
        ?.let { (it as? JsonPrimitive)?.content }
        ?: ""

    val displayText: AnnotatedString = if (line.isNotBlank()) {
        val prefix = if (lineNum.isNotBlank()) "$lineNum: " else ""
        buildAnnotatedString {
            append(prefix)
            append(line)
        }
    } else {
        buildAnnotatedString { append(result.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (filePath.isNotBlank()) onClick(filePath) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (filePath.isNotBlank()) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun FileSearchResultItem(
    filePath: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = filePath,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 48.dp),
    )
}

@Composable
private fun EmptySearchResult(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
