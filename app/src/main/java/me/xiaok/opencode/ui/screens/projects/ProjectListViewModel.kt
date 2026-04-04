package me.xiaok.opencode.ui.screens.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.FileNode
import me.xiaok.opencode.domain.model.PathInfo
import me.xiaok.opencode.domain.model.Project
import me.xiaok.opencode.domain.model.ServerConnection
import javax.inject.Inject

data class ProjectListUiState(
    val serverName: String = "",
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class DirectoryBrowserState(
    val currentPath: String = ".",
    val currentAbsolutePath: String = "",
    val entries: List<FileNode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val suggestions: List<DirectorySuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val pathInfo: PathInfo? = null,
)

/**
 * A directory suggestion produced by the autocomplete engine.
 */
data class DirectorySuggestion(
    val name: String,
    val path: String,
    val absolute: String,
)

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
    private val eventReducer: EventReducer,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _projects = MutableStateFlow<List<Project>>(emptyList())

    private val _browserState = MutableStateFlow(DirectoryBrowserState())

    /** Event flow for directory selection — collected by the Route. */
    private val _selectedDirectory = MutableSharedFlow<String>()
    val selectedDirectory: SharedFlow<String> = _selectedDirectory.asSharedFlow()

    // --- Autocomplete ---
    private val _searchQuery = MutableStateFlow("")
    /** Cache of already-browsed directories: relativePath -> directory FileNodes. */
    private val dirCache = mutableMapOf<String, List<FileNode>>()

    private val _serverName = MutableStateFlow("")

    val uiState: StateFlow<ProjectListUiState> = combine(
        _projects,
        _isLoading,
        _error,
        _serverName,
    ) { projects, loading, error, serverName ->
        ProjectListUiState(
            serverName = serverName,
            projects = projects,
            isLoading = loading,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListUiState())

    val browserState: StateFlow<DirectoryBrowserState> = _browserState.asStateFlow()

    init {
        loadServerName()
        loadProjects()

        // Debounced autocomplete pipeline
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query -> performAutocomplete(query) }
        }
    }

    private fun loadServerName() {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId) ?: return@launch
            _serverName.value = server.name
        }
    }

    // ---------------------------------------------------------------------------
    // Project list
    // ---------------------------------------------------------------------------

    fun loadProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _error.value = "Server not found"
                    _isLoading.value = false
                    return@launch
                }
                val projects = api.listProjects(server)
                _projects.value = projects
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load projects"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Directory browsing
    // ---------------------------------------------------------------------------

    fun browseDirectory(path: String) {
        viewModelScope.launch {
            _browserState.update { it.copy(isLoading = true, error = null, suggestions = emptyList()) }
            _searchQuery.value = ""
            try {
                val server = serverRepository.getServer(serverId) ?: run {
                    _browserState.update { it.copy(isLoading = false, error = "Server not found") }
                    return@launch
                }
                val allEntries = api.listFiles(server, path)
                val directories = allEntries.filter { it.type == "directory" }
                // Cache for autocomplete
                dirCache[path] = directories
                // Derive absolute path from entries
                val absolutePath = allEntries.firstOrNull()?.absolute?.let { entryAbs ->
                    entryAbs.substringBeforeLast("/")
                } ?: path
                _browserState.update {
                    it.copy(
                        currentPath = path,
                        currentAbsolutePath = absolutePath,
                        entries = directories,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _browserState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to browse directory")
                }
            }
        }
    }

    fun navigateUp() {
        val current = _browserState.value.currentPath
        if (current == "." || current == "/" || current.isEmpty()) return
        val segments = current.trimEnd('/').split('/')
        val parent = segments.dropLast(1).joinToString("/").ifEmpty { "." }
        browseDirectory(parent)
    }

    fun selectDirectory() {
        viewModelScope.launch {
            val absolute = _browserState.value.currentAbsolutePath
            _selectedDirectory.emit(absolute)
        }
    }

    /** Fetch PathInfo once (home directory, worktree, etc.). */
    fun ensurePathInfo() {
        if (_browserState.value.pathInfo != null) return
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId) ?: return@launch
            try {
                val info = api.getPathInfo(server)
                _browserState.update { it.copy(pathInfo = info) }
            } catch (_: Exception) { }
        }
    }

    // ---------------------------------------------------------------------------
    // Autocomplete
    // ---------------------------------------------------------------------------

    /** Called on every keystroke in the search field. */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private suspend fun performAutocomplete(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            _browserState.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        _browserState.update { it.copy(isSearching = true) }

        val server = serverRepository.getServer(serverId)
        if (server == null) {
            _browserState.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }

        try {
            val suggestions = resolveSuggestions(server, query)
            _browserState.update { it.copy(suggestions = suggestions, isSearching = false) }
        } catch (_: Exception) {
            _browserState.update { it.copy(suggestions = emptyList(), isSearching = false) }
        }
    }

    /**
     * Mixed strategy (方案三):
     * - Path mode (contains `/` or starts with `~`): parse segments → listFiles parent → local filter
     * - Search mode (plain keyword): fileSearch API global search
     */
    private suspend fun resolveSuggestions(
        server: ServerConnection,
        query: String,
    ): List<DirectorySuggestion> {
        val pathInfo = _browserState.value.pathInfo

        // Expand ~ to home directory
        val expanded = if (query.startsWith("~")) {
            val home = pathInfo?.home ?: ""
            home + query.substring(1)
        } else {
            query
        }

        return if (expanded.contains("/")) {
            // --- Path mode ---
            resolvePathMode(server, expanded)
        } else {
            // --- Search mode ---
            resolveSearchMode(server, expanded)
        }
    }

    /**
     * Path mode: split into segments, list the parent directory, filter last segment.
     * e.g. "/home/us/pro" → parent="/home/us", filter="pro"
     */
    private suspend fun resolvePathMode(
        server: ServerConnection,
        path: String,
    ): List<DirectorySuggestion> {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        val parentPath: String
        val filterSegment: String

        if (lastSlash <= 0) {
            // e.g. "home" (no leading slash parent) or "/" root
            parentPath = if (path.startsWith("/")) "/" else "."
            filterSegment = trimmed.removePrefix("/")
        } else {
            parentPath = trimmed.substring(0, lastSlash).ifEmpty { "." }
            filterSegment = trimmed.substring(lastSlash + 1)
        }

        // Try cache first
        val cachedDirs = dirCache[parentPath]
        val dirs: List<FileNode> = if (cachedDirs != null) {
            cachedDirs
        } else {
            try {
                val all = api.listFiles(server, parentPath)
                val directories = all.filter { it.type == "directory" }
                dirCache[parentPath] = directories
                directories
            } catch (_: Exception) {
                emptyList()
            }
        }

        val lowerFilter = filterSegment.lowercase()
        return dirs
            .filter { dir ->
                if (filterSegment.isEmpty()) true
                else dir.name.lowercase().startsWith(lowerFilter) ||
                        dir.name.lowercase().contains(lowerFilter)
            }
            .sortedBy { dir ->
                when {
                    dir.name.lowercase().startsWith(lowerFilter) -> 0
                    else -> 1
                }
            }
            .take(30)
            .map { dir ->
                DirectorySuggestion(
                    name = dir.name,
                    path = dir.path,
                    absolute = dir.absolute,
                )
            }
    }

    /**
     * Search mode: use fileSearch API with type=directory for global search.
     */
    private suspend fun resolveSearchMode(
        server: ServerConnection,
        query: String,
    ): List<DirectorySuggestion> {
        val results = try {
            api.fileSearch(server, query = query, type = "directory", limit = 30)
        } catch (_: Exception) {
            emptyList()
        }

        return results.map { filePath ->
            val name = filePath.substringAfterLast("/")
            DirectorySuggestion(
                name = name.ifEmpty { filePath },
                path = filePath,
                absolute = if (filePath.startsWith("/")) filePath else "/$filePath",
            )
        }
    }
}
