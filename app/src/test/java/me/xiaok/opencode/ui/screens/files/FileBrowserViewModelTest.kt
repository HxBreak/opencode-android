package me.xiaok.opencode.ui.screens.files

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.FileContent
import me.xiaok.opencode.domain.model.Project
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import me.xiaok.opencode.utils.ErrorCollector
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileBrowserViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private val api = mockk<OpenCodeApi>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val eventReducer = mockk<EventReducer>(relaxed = true)
    private val errorCollector = mockk<ErrorCollector>(relaxed = true)
    private val server = TestFixtures.testServerConnection()

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        every { serverRepository.getServer("test_server") } returns server
        every { eventReducer.sessions } returns MutableStateFlow(emptyMap())
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("serverId" to "test_server"))
    ): FileBrowserViewModel {
        return FileBrowserViewModel(savedStateHandle, api, serverRepository, eventReducer, errorCollector)
    }

    @Test
    fun `loadDirectory success updates fileTree and currentPath`() = testScope.runTest {
        val files = listOf(
            TestFixtures.testFileNode(name = "src", path = "src", type = "directory"),
            TestFixtures.testFileNodeFile(name = "App.kt", path = "src/App.kt"),
        )
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns files
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(".", state.currentPath)
        assertEquals(2, state.fileTree.size)
        assertEquals("src", state.fileTree[0].name)
        assertFalse(state.isLoading)
        assertNull(state.error)

        collectJob.cancel()
    }

    @Test
    fun `loadDirectory failure sets error`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } throws RuntimeException("disk error")
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("disk error", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)

        collectJob.cancel()
    }

    @Test
    fun `loadFileContent success updates fileContent and viewingFilePath`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileContent(server, "src/App.kt", workspace = null, directory = null) } returns FileContent(content = "fun main() {}")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadFileContent("src/App.kt")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("fun main() {}", state.fileContent?.content)
        assertEquals("src/App.kt", state.viewingFilePath)
        assertFalse(state.isLoading)

        collectJob.cancel()
    }

    @Test
    fun `loadFileContent failure sets error`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileContent(server, "missing.txt", workspace = null, directory = null) } throws RuntimeException("not found")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadFileContent("missing.txt")
        advanceUntilIdle()

        assertEquals("not found", vm.uiState.value.error)

        collectJob.cancel()
    }

    @Test
    fun `searchContent success updates searchResults`() = testScope.runTest {
        val results = listOf(JsonObject(emptyMap()))
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()
        coEvery { api.textSearch(server, "TODO", workspace = null, directory = null) } returns results

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.searchContent("TODO")
        advanceUntilIdle()

        assertEquals(results, vm.uiState.value.searchResults)
        assertFalse(vm.uiState.value.isSearching)

        collectJob.cancel()
    }

    @Test
    fun `searchContent with blank pattern clears results`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.searchContent("   ")
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.uiState.value.searchResults)
        coVerify(exactly = 0) { api.textSearch(any(), any(), workspace = any(), directory = any()) }

        collectJob.cancel()
    }

    @Test
    fun `searchFiles success updates fileNameResults`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()
        coEvery { api.fileSearch(server, "App", workspace = null, directory = null) } returns listOf("src/App.kt")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.searchFiles("App")
        advanceUntilIdle()

        assertEquals(listOf("src/App.kt"), vm.uiState.value.fileNameResults)
        assertFalse(vm.uiState.value.isSearching)

        collectJob.cancel()
    }

    @Test
    fun `navigateUp from subdirectory goes to parent`() = testScope.runTest {
        val rootFiles = listOf(TestFixtures.testFileNode(name = "src", path = "src"))
        val subFiles = listOf(TestFixtures.testFileNodeFile(name = "App.kt", path = "src/App.kt"))
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns rootFiles
        coEvery { api.listFiles(server, "src", workspace = null, directory = null) } returns subFiles
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(".", vm.uiState.value.currentPath)

        vm.loadDirectory("src")
        advanceUntilIdle()

        assertEquals("src", vm.uiState.value.currentPath)

        vm.navigateUp()
        advanceUntilIdle()

        assertEquals(".", vm.uiState.value.currentPath)

        collectJob.cancel()
    }

    @Test
    fun `navigateUp at root does nothing`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.navigateUp()
        advanceUntilIdle()

        assertEquals(".", vm.uiState.value.currentPath)

        collectJob.cancel()
    }

    @Test
    fun `clearSearch resets search state`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()
        coEvery { api.textSearch(server, "query", workspace = null, directory = null) } returns listOf(JsonObject(emptyMap()))
        coEvery { api.fileSearch(server, "query", workspace = null, directory = null) } returns listOf("file.kt")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.searchContent("query")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.searchResults.isEmpty())

        vm.searchFiles("query")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.fileNameResults.isEmpty())

        vm.clearSearch()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.searchResults.isEmpty())
        assertTrue(vm.uiState.value.fileNameResults.isEmpty())
        assertFalse(vm.uiState.value.isSearching)

        collectJob.cancel()
    }

    @Test
    fun `clearError resets error to null`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } throws RuntimeException("err")
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)

        vm.clearError()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)

        collectJob.cancel()
    }

    @Test
    fun `directory from navigation argument is used`() = testScope.runTest {
        val files = listOf(
            TestFixtures.testFileNode(name = "src", path = "src", type = "directory"),
        )
        // When directory is provided via navigation, getCurrentProject should NOT be called
        coEvery { api.listFiles(server, ".", workspace = null, directory = "/home/user/project") } returns files
        coEvery { api.getFileStatuses(server, workspace = null, directory = "/home/user/project") } returns emptyList()

        val savedStateHandle = SavedStateHandle(mapOf(
            "serverId" to "test_server",
            "directory" to "/home/user/project",
        ))
        val vm = createViewModel(savedStateHandle)
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(".", state.currentPath)
        assertEquals(1, state.fileTree.size)
        coVerify(exactly = 0) { api.getCurrentProject(any()) }

        collectJob.cancel()
    }

    @Test
    fun `fallback to getCurrentProject when no directory provided`() = testScope.runTest {
        val files = listOf(
            TestFixtures.testFileNode(name = "src", path = "src", type = "directory"),
        )
        coEvery { api.getCurrentProject(server) } returns Project(id = "p1", worktree = "/home/user/project")
        coEvery { api.listFiles(server, ".", workspace = null, directory = "/home/user/project") } returns files
        coEvery { api.getFileStatuses(server, workspace = null, directory = "/home/user/project") } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.fileTree.size)
        coVerify(exactly = 1) { api.getCurrentProject(server) }

        collectJob.cancel()
    }

    @Test
    fun `saveToDownloads success updates downloadResult`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileContent(server, "src/App.kt", workspace = null, directory = null) } returns
            FileContent(content = "fun main() {}")

        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        val uri = mockk<android.net.Uri>(relaxed = true)
        val outputStream = java.io.ByteArrayOutputStream()

        every { contentResolver.insert(any(), any()) } returns uri
        every { contentResolver.openOutputStream(uri, "wt") } returns outputStream
        every { contentResolver.update(uri, any(), null, null) } returns 1

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadFileContent("src/App.kt")
        advanceUntilIdle()

        vm.saveToDownloads(contentResolver)
        advanceUntilIdle()

        val result = vm.uiState.value.downloadResult
        assertTrue(result is DownloadResult.Success)
        assertEquals("App.kt", (result as DownloadResult.Success).fileName)
        assertFalse(vm.uiState.value.isDownloading)

        collectJob.cancel()
    }

    @Test
    fun `saveToDownloads when no file loaded does nothing`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.saveToDownloads(contentResolver)
        advanceUntilIdle()

        assertNull(vm.uiState.value.downloadResult)
        coVerify(exactly = 0) { contentResolver.insert(any(), any()) }

        collectJob.cancel()
    }

    @Test
    fun `saveToUri success updates downloadResult`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileContent(server, "src/App.kt", workspace = null, directory = null) } returns
            FileContent(content = "fun main() {}")

        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        val uri = mockk<android.net.Uri>(relaxed = true)
        every { uri.lastPathSegment } returns "external/storage/App.kt"
        val outputStream = java.io.ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri, "wt") } returns outputStream

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadFileContent("src/App.kt")
        advanceUntilIdle()

        vm.saveToUri(uri, contentResolver)
        advanceUntilIdle()

        val result = vm.uiState.value.downloadResult
        assertTrue(result is DownloadResult.Success)
        assertFalse(vm.uiState.value.isDownloading)

        collectJob.cancel()
    }

    @Test
    fun `clearDownloadResult resets download state`() = testScope.runTest {
        coEvery { api.getCurrentProject(server) } returns Project(id = "global", worktree = "/")
        coEvery { api.listFiles(server, ".", workspace = null, directory = null) } returns emptyList()
        coEvery { api.getFileStatuses(server, workspace = null, directory = null) } returns emptyList()

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.clearDownloadResult()
        advanceUntilIdle()

        assertNull(vm.uiState.value.downloadResult)

        collectJob.cancel()
    }
}
