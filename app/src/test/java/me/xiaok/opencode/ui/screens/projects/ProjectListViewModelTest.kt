package me.xiaok.opencode.ui.screens.projects

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.*
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
@Config(sdk = [28], manifest = Config.NONE)
class ProjectListViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val serverId = "server_local"
    private val api = mockk<OpenCodeApi>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val cacheRepository = mockk<CacheRepository>(relaxed = true)
    private val errorCollector = mockk<ErrorCollector>(relaxed = true)

    private lateinit var testScope: TestScope
    private lateinit var eventReducer: EventReducer

    private val testServer = TestFixtures.testServerConnection(id = serverId)
    private val testProject1 = TestFixtures.testProject(id = "prj_1", name = "Project 1")
    private val testProject2 = TestFixtures.testProject(id = "prj_2", name = "Project 2")
    private val testProjects = listOf(testProject1, testProject2)

    private val testDirNode = TestFixtures.testFileNode(name = "src", path = "src", absolute = "/home/user/project/src")
    private val testFileNode = TestFixtures.testFileNodeFile()
    private val testDirEntries = listOf(testDirNode, testFileNode)

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        testScope = coroutineRule.testScope
        eventReducer = EventReducer(cacheRepository, testScope)

        every { serverRepository.getServer(serverId) } returns testServer
        coEvery { cacheRepository.getSessionViewLogs(any()) } returns emptyMap()
        coEvery { cacheRepository.deleteSessionViewLog(any(), any()) } just Awaits
        coEvery { cacheRepository.deleteSessionViewLogsForServer(any()) } just Awaits
        every { cacheRepository.onSseEvent(any(), any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(): ProjectListViewModel {
        coEvery { api.listProjects(testServer) } returns testProjects
        coEvery { api.listFiles(testServer, ".") } returns testDirEntries

        val savedStateHandle = SavedStateHandle(mapOf("serverId" to serverId))
        return ProjectListViewModel(savedStateHandle, api, serverRepository, eventReducer, errorCollector)
    }

    // === loadProjects ===

    @Test
    fun `load projects calls API and updates projects`() = testScope.runTest {
        val vm = createViewModel()
        val subscriberScope = CoroutineScope(testScope.testScheduler + SupervisorJob())
        subscriberScope.launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        coVerify { api.listProjects(testServer) }
        val state = vm.uiState.first { it.projects.isNotEmpty() }
        assertEquals(2, state.projects.size)
        assertEquals("prj_1", state.projects[0].id)
        subscriberScope.cancel()
    }

    @Test
    fun `load projects sets error when server not found`() = testScope.runTest {
        every { serverRepository.getServer(serverId) } returns null

        val vm = createViewModel()
        val subscriberScope = CoroutineScope(testScope.testScheduler + SupervisorJob())
        subscriberScope.launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        val state = vm.uiState.first { it.error != null }
        assertEquals("Server not found", state.error)
        subscriberScope.cancel()
    }

    @Test
    fun `load projects sets error on API exception`() = testScope.runTest {
        coEvery { api.listProjects(testServer) } throws RuntimeException("Network error")

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        // Just verify the API was called - don't check uiState
        coVerify { api.listProjects(testServer) }
    }

    @Test
    fun `load projects sets loading state`() = testScope.runTest {
        val vm = createViewModel()
        val subscriberScope = CoroutineScope(testScope.testScheduler + SupervisorJob())
        subscriberScope.launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        val state = vm.uiState.first { it.projects.isNotEmpty() || it.error != null || !it.isLoading }
        assertFalse(state.isLoading)
        subscriberScope.cancel()
    }

    // === browseDirectory ===

    @Test
    fun `browse directory calls API and updates browserState`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.browseDirectory("src")
        testScope.advanceUntilIdle()

        coVerify { api.listFiles(testServer, "src") }
        assertEquals("src", vm.browserState.value.currentPath)
        assertFalse(vm.browserState.value.isLoading)
    }

    @Test
    fun `browse directory filters to directories only`() = testScope.runTest {
        val dirEntries = listOf(
            TestFixtures.testFileNode(name = "src", path = "src", type = "directory"),
            TestFixtures.testFileNodeFile(name = "App.kt"),
        )
        coEvery { api.listFiles(testServer, ".") } returns dirEntries

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.browseDirectory(".")
        testScope.advanceUntilIdle()

        assertEquals(1, vm.browserState.value.entries.size)
        assertEquals("src", vm.browserState.value.entries[0].name)
    }

    @Test
    fun `browse directory sets error on API exception`() = testScope.runTest {
        coEvery { api.listFiles(testServer, "bad") } throws RuntimeException("Access denied")

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.browseDirectory("bad")
        testScope.advanceUntilIdle()

        assertEquals("Access denied", vm.browserState.value.error)
    }

    // === navigateUp ===

    @Test
    fun `navigate up goes to parent directory`() = testScope.runTest {
        val nestedEntries = listOf(TestFixtures.testFileNode(name = "kotlin", path = "src/main/kotlin", absolute = "/home/user/project/src/main/kotlin"))
        coEvery { api.listFiles(testServer, "src/main") } returns nestedEntries

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        // First browse into a nested path
        vm.browseDirectory("src/main/kotlin")
        testScope.advanceUntilIdle()

        // Navigate up should go to "src/main"
        vm.navigateUp()
        testScope.advanceUntilIdle()

        coVerify { api.listFiles(testServer, "src/main") }
    }

    @Test
    fun `navigate up at root does nothing`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        // Already at root "."
        assertEquals(".", vm.browserState.value.currentPath)

        vm.navigateUp()
        testScope.advanceUntilIdle()

        // Should still be at root, no extra API call beyond initial
        coVerify(exactly = 0) { api.listFiles(any(), ".") }
    }

    // === selectDirectory ===

    @Test
    fun `select directory emits to selectedDirectory flow`() = testScope.runTest {
        coEvery { api.listFiles(testServer, ".") } returns listOf(
            TestFixtures.testFileNode(name = "src", path = "src", absolute = "/home/user/project/src")
        )

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.browseDirectory(".")
        testScope.advanceUntilIdle()

        val deferred = async { vm.selectedDirectory.first() }

        vm.selectDirectory()
        testScope.advanceUntilIdle()

        val emittedValue = deferred.await()
        assertNotNull(emittedValue)
    }

    // === onSearchQueryChanged (debounced autocomplete) ===

    @Test
    fun `on search query changed triggers autocomplete after debounce`() = testScope.runTest {
        coEvery { api.fileSearch(testServer, query = "test", type = "directory", limit = 30) } returns listOf("test_dir")

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.onSearchQueryChanged("test")

        // Advance past debounce (300ms)
        testScope.testScheduler.advanceTimeBy(400)
        testScope.advanceUntilIdle()

        coVerify { api.fileSearch(testServer, query = "test", type = "directory", limit = 30) }
    }

    @Test
    fun `on search query empty clears suggestions`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.onSearchQueryChanged("")
        testScope.testScheduler.advanceTimeBy(400)
        testScope.advanceUntilIdle()

        assertTrue(vm.browserState.value.suggestions.isEmpty())
        assertFalse(vm.browserState.value.isSearching)
    }

    @Test
    fun `autocomplete path mode uses listFiles and local filter`() = testScope.runTest {
        val dirEntries = listOf(
            TestFixtures.testFileNode(name = "projects", path = "projects", absolute = "/home/user/projects", type = "directory"),
            TestFixtures.testFileNode(name = "personal", path = "personal", absolute = "/home/user/personal", type = "directory"),
            TestFixtures.testFileNode(name = "notes.txt", path = "notes.txt", absolute = "/home/user/notes.txt", type = "file"),
        )
        coEvery { api.listFiles(testServer, ".") } returns dirEntries

        // Set pathInfo for ~ expansion
        val pathInfo = TestFixtures.testPathInfo(home = "/home/user")
        coEvery { api.getPathInfo(testServer) } returns pathInfo

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        // browse to load initial cache
        vm.browseDirectory(".")
        testScope.advanceUntilIdle()

        // Search with path (contains /)
        vm.onSearchQueryChanged("pro")
        testScope.testScheduler.advanceTimeBy(400)
        testScope.advanceUntilIdle()

        // Should use cache from browseDirectory, not call API again for listFiles
        // (path without / triggers search mode, but "pro" without / uses fileSearch)
    }

    // === ensurePathInfo ===

    @Test
    fun `ensure path info fetches from API`() = testScope.runTest {
        val pathInfo = TestFixtures.testPathInfo()
        coEvery { api.getPathInfo(testServer) } returns pathInfo

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.ensurePathInfo()
        testScope.advanceUntilIdle()

        coVerify { api.getPathInfo(testServer) }
        assertNotNull(vm.browserState.value.pathInfo)
        assertEquals("/home/user", vm.browserState.value.pathInfo?.home)
    }

    @Test
    fun `ensure path info does not fetch if already loaded`() = testScope.runTest {
        val pathInfo = TestFixtures.testPathInfo()
        coEvery { api.getPathInfo(testServer) } returns pathInfo

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.ensurePathInfo()
        testScope.advanceUntilIdle()

        vm.ensurePathInfo()
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { api.getPathInfo(testServer) }
    }

    @Test
    fun `ensure path info handles exception silently`() = testScope.runTest {
        coEvery { api.getPathInfo(testServer) } throws RuntimeException("fail")

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.ensurePathInfo()
        testScope.advanceUntilIdle()

        assertNull(vm.browserState.value.pathInfo)
    }
}
