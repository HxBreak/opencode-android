package me.xiaok.opencode.ui.screens.sessions

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
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
class SessionListViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val serverId = "server_local"
    private val api = mockk<OpenCodeApi>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val cacheRepository = mockk<CacheRepository>(relaxed = true)
    private val errorCollector = mockk<ErrorCollector>(relaxed = true)

    private lateinit var testScope: TestScope
    private lateinit var eventReducer: EventReducer

    private val testServer = TestFixtures.testServerConnection(id = serverId)
    private val testSession1 = TestFixtures.testSession(id = "ses_1", title = "Session 1")
    private val testSession2 = TestFixtures.testSession(id = "ses_2", title = "Session 2")
    private val testSessions = listOf(testSession1, testSession2)

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
        every { settingsRepository.collapsedDirectories } returns flowOf(emptySet())
        coEvery { cacheRepository.getSessionViewLogs(any()) } returns emptyMap()
        coEvery { cacheRepository.deleteSessionViewLog(any(), any()) } returns Unit
        coEvery { cacheRepository.deleteSessionViewLogsForServer(any()) } returns Unit
        every { cacheRepository.onSseEvent(any(), any()) } just Runs

        coEvery { api.listSessions(testServer, directory = null, roots = true) } returns testSessions
        coEvery { api.createSession(testServer, directory = null, title = null) } returns testSession1
        coEvery { api.deleteSession(testServer, "ses_1") } returns true
        coEvery { api.updateSession(testServer, "ses_1", title = "Updated Title") } returns testSession1.copy(title = "Updated Title")
        coEvery { api.updateSession(testServer, "ses_1", archived = any()) } returns testSession1.copy(time = TestFixtures.testSessionTime(archived = 1000L))
        coEvery { api.updateSession(testServer, "ses_1", unarchive = any(), directory = any()) } returns testSession1.copy(time = TestFixtures.testSessionTime(archived = null))
        coEvery { api.getSessionChildren(testServer, "ses_1") } returns listOf(testSession2)
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(directory: String? = null): SessionListViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("serverId" to serverId, "directory" to directory))
        return SessionListViewModel(savedStateHandle, api, eventReducer, serverRepository, settingsRepository, errorCollector)
    }

    // === refreshSessions ===

    @Test
    fun `refresh sessions calls API and sets sessions in EventReducer`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        assertEquals(testSession1, eventReducer.sessions.value["ses_1"])
        assertEquals(testSession2, eventReducer.sessions.value["ses_2"])
        assertEquals(setOf("ses_1", "ses_2"), eventReducer.serverSessions.value[serverId])
        coVerify { api.listSessions(testServer, directory = null, roots = true) }
    }

    @Test
    fun `refresh sessions sets error when server not found`() = testScope.runTest {
        every { serverRepository.getServer(serverId) } returns null

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        val state = vm.uiState.first { it.error != null }
        assertEquals("Server not found", state.error)
    }

    @Test
    fun `refresh sessions handles API exception`() = testScope.runTest {
        coEvery { api.listSessions(testServer, directory = null, roots = true) } throws RuntimeException("Network error")

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        val state = vm.uiState.first { it.error != null }
        assertEquals("Network error", state.error)
    }

    // === createSession ===

    @Test
    fun `create session calls API and processes SessionCreated event`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.createSession()
        testScope.advanceUntilIdle()

        coVerify { api.createSession(testServer, directory = null, title = null) }
        assertNotNull(eventReducer.sessions.value["ses_1"])
    }

    @Test
    fun `create session with title passes title to API`() = testScope.runTest {
        coEvery { api.createSession(testServer, directory = null, title = "My Session") } returns testSession1.copy(title = "My Session")

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.createSession(title = "My Session")
        testScope.advanceUntilIdle()

        coVerify { api.createSession(testServer, directory = null, title = "My Session") }
    }

    // === deleteSession ===

    @Test
    fun `delete session calls API and processes SessionDeleted event`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.deleteSession("ses_1")
        testScope.advanceUntilIdle()

        coVerify { api.deleteSession(testServer, "ses_1") }
        assertNull(eventReducer.sessions.value["ses_1"])
    }

    // === updateSessionTitle ===

    @Test
    fun `update session title calls API and processes SessionUpdated event`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.updateSessionTitle("ses_1", "Updated Title")
        testScope.advanceUntilIdle()

        coVerify { api.updateSession(testServer, "ses_1", title = "Updated Title") }
        assertEquals("Updated Title", eventReducer.sessions.value["ses_1"]?.title)
    }

    // === archiveSession / unarchiveSession ===

    @Test
    fun `archive session calls API with timestamp`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.archiveSession("ses_1")
        testScope.advanceUntilIdle()

        coVerify { api.updateSession(testServer, "ses_1", archived = any()) }
    }

    @Test
    fun `unarchive session calls API with unarchive true`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.unarchiveSession("ses_1")
        testScope.advanceUntilIdle()

        coVerify { api.updateSession(testServer, "ses_1", unarchive = true, directory = null) }
    }

    // === setArchiveFilter ===

    @Test
    fun `set archive filter updates uiState`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()
        val collectJob = launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        vm.setArchiveFilter(SessionArchiveFilter.Archived)

        val state = vm.uiState.first { it.archiveFilter == SessionArchiveFilter.Archived }
        assertEquals(SessionArchiveFilter.Archived, state.archiveFilter)
        collectJob.cancel()
    }

    @Test
    fun `set archive filter to Active hides archived sessions`() = testScope.runTest {
        val archivedSession = TestFixtures.testSession(
            id = "ses_archived",
            time = TestFixtures.testSessionTime(archived = 1000L)
        )
        coEvery { api.listSessions(any(), any(), any()) } returns listOf(testSession1, archivedSession)

        val vm = createViewModel()
        testScope.advanceUntilIdle()
        val collectJob = launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        vm.setArchiveFilter(SessionArchiveFilter.Active)

        val state = vm.uiState.first { it.archiveFilter == SessionArchiveFilter.Active }
        assertTrue(state.sessions.none { it.id == "ses_archived" })
        collectJob.cancel()
    }

    // === Selection mode ===

    @Test
    fun `enter selection mode sets selection and mode`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()
        val collectJob = launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        vm.enterSelectionMode("ses_1")

        val state = vm.uiState.first { it.isSelectionMode }
        assertTrue(state.selectedSessions.contains("ses_1"))
        collectJob.cancel()
    }

    @Test
    fun `toggle selection adds to selection`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()
        val collectJob = launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        vm.enterSelectionMode("ses_1")
        vm.toggleSelection("ses_2")

        val state = vm.uiState.first { it.selectedSessions.size == 2 }
        assertEquals(setOf("ses_1", "ses_2"), state.selectedSessions)
        collectJob.cancel()
    }

    @Test
    fun `toggle selection removes from selection and exits mode`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()
        val collectJob = launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        vm.enterSelectionMode("ses_1")
        vm.toggleSelection("ses_1")

        val state = vm.uiState.first { !it.selectedSessions.contains("ses_1") }
        assertFalse(state.isSelectionMode)
        collectJob.cancel()
    }

    @Test
    fun `exit selection mode clears selection`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()
        val collectJob = launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        vm.enterSelectionMode("ses_1")
        vm.exitSelectionMode()

        val state = vm.uiState.first { it.selectedSessions.isEmpty() && !it.isSelectionMode }
        assertTrue(state.selectedSessions.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `select all selects all sessions`() = testScope.runTest {
        val vm = createViewModel()
        testScope.advanceUntilIdle()
        val collectJob = launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()

        vm.enterSelectionMode("ses_1")
        vm.selectAll()

        val state = vm.uiState.first { it.selectedSessions.size == 2 }
        assertEquals(setOf("ses_1", "ses_2"), state.selectedSessions)
        collectJob.cancel()
    }

    // === toggleDirectoryCollapsed ===

    @Test
    fun `toggle directory collapsed calls settingsRepository`() = testScope.runTest {
        every { settingsRepository.collapsedDirectories } returns flowOf(emptySet())
        coEvery { settingsRepository.setCollapsedDirectories(any()) } just Awaits

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.toggleDirectoryCollapsed("/home/user/project/src")
        testScope.advanceUntilIdle()

        coVerify { settingsRepository.setCollapsedDirectories(setOf("/home/user/project/src")) }
    }

    @Test
    fun `toggle directory collapsed removes if already collapsed`() = testScope.runTest {
        every { settingsRepository.collapsedDirectories } returns flowOf(setOf("/home/user/project/src"))
        coEvery { settingsRepository.setCollapsedDirectories(any()) } just Awaits

        val vm = createViewModel()
        testScope.advanceUntilIdle()

        vm.toggleDirectoryCollapsed("/home/user/project/src")
        testScope.advanceUntilIdle()

        coVerify { settingsRepository.setCollapsedDirectories(emptySet()) }
    }
}
