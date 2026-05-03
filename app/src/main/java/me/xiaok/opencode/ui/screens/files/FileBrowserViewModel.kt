package me.xiaok.opencode.ui.screens.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.FileContent
import me.xiaok.opencode.domain.model.FileNode
import me.xiaok.opencode.domain.model.FileStatus
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

/** Result of a download/save operation. */
sealed class DownloadResult {
    data class Success(val fileName: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

data class FileBrowserUiState(
    val fileTree: List<FileNode> = emptyList(),
    val currentPath: String = ".",
    val fileContent: FileContent? = null,
    val viewingFilePath: String? = null,
    val fileStatuses: List<FileStatus> = emptyList(),
    val searchResults: List<JsonElement> = emptyList(),
    val fileNameResults: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDownloading: Boolean = false,
    val downloadResult: DownloadResult? = null,
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
    private val eventReducer: EventReducer,
    private val errorCollector: ErrorCollector,
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    private val sessionId: String? = savedStateHandle["sessionId"]
    private val navigationDirectory: String? = savedStateHandle["directory"]

    /** Resolve the workspace ID from the current session (if any). */
    private val workspaceId: String?
        get() = sessionId?.let { eventReducer.sessions.value[it]?.workspaceID }

    /**
     * Resolved project directory used for `x-opencode-directory` header.
     *
     * Priority: navigation argument → session.directory → getCurrentProject().worktree → null
     */
    private val _resolvedDirectory = MutableStateFlow<String?>(null)

    /** Lazily resolved directory — set once in init, then reused for all API calls. */
    private val directory: String?
        get() = _resolvedDirectory.value

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    private val _currentPath = MutableStateFlow(".")
    private val _fileContent = MutableStateFlow<FileContent?>(null)
    private val _viewingFilePath = MutableStateFlow<String?>(null)
    private val _fileStatuses = MutableStateFlow<List<FileStatus>>(emptyList())
    private val _searchResults = MutableStateFlow<List<JsonElement>>(emptyList())
    private val _fileNameResults = MutableStateFlow<List<String>>(emptyList())
    private val _isSearching = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _isDownloading = MutableStateFlow(false)
    private val _downloadResult = MutableStateFlow<DownloadResult?>(null)

    val uiState: StateFlow<FileBrowserUiState> = combine(
        _fileTree,
        _currentPath,
        _fileContent,
        _viewingFilePath,
        _fileStatuses,
        _searchResults,
        _fileNameResults,
        _isSearching,
        _isLoading,
        _error,
        _isDownloading,
        _downloadResult,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        FileBrowserUiState(
            fileTree = values[0] as List<FileNode>,
            currentPath = values[1] as String,
            fileContent = values[2] as FileContent?,
            viewingFilePath = values[3] as String?,
            fileStatuses = values[4] as List<FileStatus>,
            searchResults = values[5] as List<JsonElement>,
            fileNameResults = values[6] as List<String>,
            isSearching = values[7] as Boolean,
            isLoading = values[8] as Boolean,
            error = values[9] as String?,
            isDownloading = values[10] as Boolean,
            downloadResult = values[11] as DownloadResult?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FileBrowserUiState())

    init {
        resolveDirectoryAndLoad()
    }

    /**
     * Resolve the project directory using a priority chain:
     * 1. `directory` passed via navigation arguments
     * 2. `session.directory` from the EventReducer (when entering from Chat)
     * 3. `getCurrentProject().worktree` as fallback
     */
    private fun resolveDirectoryAndLoad() {
        // 1. Navigation argument takes highest priority
        if (!navigationDirectory.isNullOrBlank()) {
            _resolvedDirectory.value = navigationDirectory
            loadDirectory(".")
            loadFileStatuses()
            return
        }

        // 2. Try session directory from EventReducer
        val sessionDirectory = sessionId?.let {
            eventReducer.sessions.value[it]?.directory
        }
        if (!sessionDirectory.isNullOrBlank()) {
            _resolvedDirectory.value = sessionDirectory
            loadDirectory(".")
            loadFileStatuses()
            return
        }

        // 3. Fallback: fetch current project from server
        viewModelScope.launch {
            val conn = getConnection()
            if (conn != null) {
                try {
                    val project = api.getCurrentProject(conn)
                    val worktree = project.worktree
                    if (!worktree.isBlank() && worktree != "/") {
                        _resolvedDirectory.value = worktree
                    }
                } catch (_: Exception) {
                    // Silently ignore — will load without directory header
                }
            }
            loadDirectory(".")
            loadFileStatuses()
        }
    }

    private fun getConnection() = serverRepository.getServer(serverId)

    fun loadDirectory(path: String) {
        val conn = getConnection() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val nodes = api.listFiles(conn, path, workspace = workspaceId, directory = directory)
                _fileTree.value = nodes.sortedWith(
                    compareBy<FileNode> { it.type != "directory" }
                        .thenBy { it.name.lowercase() }
                )
                _currentPath.value = path
                _fileContent.value = null
                _viewingFilePath.value = null
            } catch (e: Exception) {
                errorCollector.logError(e, "FileBrowser")
                _error.value = e.message ?: "Failed to load directory"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFileContent(path: String) {
        val conn = getConnection() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val content = api.getFileContent(conn, path, workspace = workspaceId, directory = directory)
                _fileContent.value = content
                _viewingFilePath.value = path
            } catch (e: Exception) {
                errorCollector.logError(e, "FileBrowser")
                _error.value = e.message ?: "Failed to load file"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFileStatuses() {
        val conn = getConnection() ?: return
        viewModelScope.launch {
            try {
                _fileStatuses.value = api.getFileStatuses(conn, workspace = workspaceId, directory = directory)
            } catch (_: Exception) {
                // Statuses are optional, ignore errors
            }
        }
    }

    fun searchContent(pattern: String) {
        if (pattern.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val conn = getConnection() ?: return
        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            try {
                _searchResults.value = api.textSearch(conn, pattern, workspace = workspaceId, directory = directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "FileBrowser")
                _error.value = e.message ?: "Search failed"
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun searchFiles(query: String) {
        if (query.isBlank()) {
            _fileNameResults.value = emptyList()
            return
        }
        val conn = getConnection() ?: return
        viewModelScope.launch {
            _isSearching.value = true
            _error.value = null
            try {
                _fileNameResults.value = api.fileSearch(conn, query, workspace = workspaceId, directory = directory)
            } catch (e: Exception) {
                errorCollector.logError(e, "FileBrowser")
                _error.value = e.message ?: "File search failed"
                _fileNameResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current == "." || current == "/" || current.isEmpty()) return
        val parent = current.substringBeforeLast("/", ".")
        loadDirectory(if (parent.isEmpty()) "." else parent)
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _fileNameResults.value = emptyList()
        _isSearching.value = false
    }

    fun clearError() {
        _error.value = null
    }

    // --- Download from directory listing (long-press context menu) ---

    private val _pendingSaveAsPath = MutableStateFlow<String?>(null)
    val pendingSaveAsPath: StateFlow<String?> = _pendingSaveAsPath

    private var _pendingSaveAsContent: FileContent? = null

    /** Download a file directly to the system Downloads folder. */
    fun downloadToDownloads(path: String, contentResolver: android.content.ContentResolver) {
        val conn = getConnection() ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadResult.value = null
            try {
                val fileContent = api.getFileContent(conn, path, workspace = workspaceId, directory = directory)
                val bytes = FileSaver.decodeBytes(fileContent)
                val fileName = FileSaver.extractFileName(path)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE,
                            fileContent.mimeType ?: android.webkit.MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(fileName.substringAfterLast('.'))
                                ?: "application/octet-stream")
                        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                    }

                    val uri = contentResolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: throw java.io.IOException("Failed to create MediaStore entry")

                    FileSaver.writeToUri(contentResolver, uri, bytes)

                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    )
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    java.io.File(downloadsDir, fileName).writeBytes(bytes)
                }

                _downloadResult.value = DownloadResult.Success(fileName)
            } catch (e: Exception) {
                errorCollector.logError(e, "FileBrowser")
                _downloadResult.value = DownloadResult.Error(e.message ?: "Download failed")
            } finally {
                _isDownloading.value = false
            }
        }
    }

    /** First step of "Save As": fetch file content then trigger SAF picker. */
    fun startSaveAs(path: String, contentResolver: android.content.ContentResolver) {
        val conn = getConnection() ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadResult.value = null
            try {
                val fileContent = api.getFileContent(conn, path, workspace = workspaceId, directory = directory)
                _pendingSaveAsContent = fileContent
                _pendingSaveAsPath.value = path
            } catch (e: Exception) {
                errorCollector.logError(e, "FileBrowser")
                _downloadResult.value = DownloadResult.Error(e.message ?: "Failed to load file")
                _isDownloading.value = false
            }
        }
    }

    /** Second step of "Save As": write pending content to the user-chosen URI. */
    fun savePendingToUri(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        val fileContent = _pendingSaveAsContent ?: return
        viewModelScope.launch {
            try {
                val bytes = FileSaver.decodeBytes(fileContent)
                FileSaver.writeToUri(contentResolver, uri, bytes)
                val fileName = FileSaver.extractFileName(_pendingSaveAsPath.value ?: "")
                _downloadResult.value = DownloadResult.Success(fileName)
            } catch (e: Exception) {
                errorCollector.logError(e, "FileBrowser")
                _downloadResult.value = DownloadResult.Error(e.message ?: "Save failed")
            } finally {
                _isDownloading.value = false
                _pendingSaveAsPath.value = null
                _pendingSaveAsContent = null
            }
        }
    }

    /** Cancel a pending Save As operation. */
    fun clearPendingSaveAs() {
        _pendingSaveAsPath.value = null
        _pendingSaveAsContent = null
        _isDownloading.value = false
    }

    fun clearDownloadResult() {
        _downloadResult.value = null
    }
}
