package me.xiaok.opencode.ui.screens.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.FileNode
import me.xiaok.opencode.domain.model.FileStatus
import javax.inject.Inject

data class FileBrowserUiState(
    val fileTree: List<FileNode> = emptyList(),
    val currentPath: String = ".",
    val fileContent: String? = null,
    val viewingFilePath: String? = null,
    val fileStatuses: List<FileStatus> = emptyList(),
    val searchResults: List<JsonElement> = emptyList(),
    val fileNameResults: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    private val _currentPath = MutableStateFlow(".")
    private val _fileContent = MutableStateFlow<String?>(null)
    private val _viewingFilePath = MutableStateFlow<String?>(null)
    private val _fileStatuses = MutableStateFlow<List<FileStatus>>(emptyList())
    private val _searchResults = MutableStateFlow<List<JsonElement>>(emptyList())
    private val _fileNameResults = MutableStateFlow<List<String>>(emptyList())
    private val _isSearching = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

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
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        FileBrowserUiState(
            fileTree = values[0] as List<FileNode>,
            currentPath = values[1] as String,
            fileContent = values[2] as String?,
            viewingFilePath = values[3] as String?,
            fileStatuses = values[4] as List<FileStatus>,
            searchResults = values[5] as List<JsonElement>,
            fileNameResults = values[6] as List<String>,
            isSearching = values[7] as Boolean,
            isLoading = values[8] as Boolean,
            error = values[9] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FileBrowserUiState())

    init {
        loadDirectory(".")
        loadFileStatuses()
    }

    private fun getConnection() = serverRepository.getServer(serverId)

    fun loadDirectory(path: String) {
        val conn = getConnection() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val nodes = api.listFiles(conn, path)
                _fileTree.value = nodes.sortedWith(
                    compareBy<FileNode> { it.type != "directory" }
                        .thenBy { it.name.lowercase() }
                )
                _currentPath.value = path
                _fileContent.value = null
                _viewingFilePath.value = null
            } catch (e: Exception) {
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
                val content = api.getFileContent(conn, path)
                _fileContent.value = content
                _viewingFilePath.value = path
            } catch (e: Exception) {
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
                _fileStatuses.value = api.getFileStatuses(conn)
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
                _searchResults.value = api.textSearch(conn, pattern)
            } catch (e: Exception) {
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
                _fileNameResults.value = api.fileSearch(conn, query)
            } catch (e: Exception) {
                _error.value = e.message ?: "File search failed"
                _fileNameResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current == "." || current == "/" || current.isBlank()) return
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
}
