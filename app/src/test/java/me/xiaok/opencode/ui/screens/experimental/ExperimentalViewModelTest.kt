package me.xiaok.opencode.ui.screens.experimental

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
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
class ExperimentalViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private val api = mockk<OpenCodeApi>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
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
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(): ExperimentalViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("serverId" to "test_server"))
        return ExperimentalViewModel(savedStateHandle, api, serverRepository, errorCollector)
    }

    @Test
    fun `loadWorkspaces success updates uiState`() = testScope.runTest {
        val json = JsonObject(mapOf("items" to JsonPrimitive("[]")))
        coEvery { api.listWorkspaces(server) } returns json

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(json, vm.uiState.value.workspaces)
        assertFalse(vm.uiState.value.isLoadingWorkspaces)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `loadWorkspaces failure sets error`() = testScope.runTest {
        coEvery { api.listWorkspaces(server) } throws RuntimeException("ws fail")
        coEvery { api.listWorktrees(server) } throws RuntimeException("wt fail")
        coEvery { api.getExperimentalResources(server) } throws RuntimeException("res fail")

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoadingWorkspaces)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `loadWorktrees success updates uiState`() = testScope.runTest {
        val worktrees = listOf("main", "feature-branch")
        coEvery { api.listWorktrees(server) } returns worktrees

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(worktrees, vm.uiState.value.worktrees)
        assertFalse(vm.uiState.value.isLoadingWorktrees)
    }

    @Test
    fun `loadWorktrees failure sets error`() = testScope.runTest {
        coEvery { api.listWorkspaces(server) } throws RuntimeException("ws fail")
        coEvery { api.listWorktrees(server) } throws RuntimeException("worktree fail")
        coEvery { api.getExperimentalResources(server) } throws RuntimeException("res fail")

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), vm.uiState.value.worktrees)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `loadResources success updates uiState`() = testScope.runTest {
        val json = JsonObject(mapOf("count" to JsonPrimitive(42)))
        coEvery { api.getExperimentalResources(server) } returns json

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(json, vm.uiState.value.resources)
        assertFalse(vm.uiState.value.isLoadingResources)
    }

    @Test
    fun `loadResources failure sets error`() = testScope.runTest {
        coEvery { api.listWorkspaces(server) } throws RuntimeException("ws fail")
        coEvery { api.listWorktrees(server) } throws RuntimeException("wt fail")
        coEvery { api.getExperimentalResources(server) } throws RuntimeException("resource fail")

        val vm = createViewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.resources)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `selectTab updates selectedTab`() = testScope.runTest {
        val vm = createViewModel()

        assertEquals(0, vm.uiState.value.selectedTab)

        vm.selectTab(2)

        assertEquals(2, vm.uiState.value.selectedTab)
    }

    @Test
    fun `createWorkspace success reloads workspaces`() = testScope.runTest {
        val json = JsonObject(mapOf("id" to JsonPrimitive("ws_1")))
        coEvery { api.createWorkspace(server, any()) } returns json
        coEvery { api.listWorkspaces(server) } returns json

        val vm = createViewModel()
        advanceUntilIdle()

        vm.createWorkspace(id = "ws_1", type = "worktree")
        advanceUntilIdle()

        coVerify { api.createWorkspace(server, match { it.id == "ws_1" && it.type == "worktree" }) }
        assertFalse(vm.uiState.value.isCreating)
    }

    @Test
    fun `createWorkspace failure sets error`() = testScope.runTest {
        coEvery { api.createWorkspace(server, any()) } throws RuntimeException("create fail")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.createWorkspace()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isCreating)
        assertEquals("create fail", vm.uiState.value.error)
    }

    @Test
    fun `createWorktree success reloads worktrees`() = testScope.runTest {
        coEvery { api.createWorktree(server, any()) } returns JsonObject(emptyMap())
        coEvery { api.listWorktrees(server) } returns listOf("main", "feature")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.createWorktree(name = "feature")
        advanceUntilIdle()

        coVerify { api.createWorktree(server, match { it.name == "feature" }) }
        assertFalse(vm.uiState.value.isCreating)
    }

    @Test
    fun `createWorktree failure sets error`() = testScope.runTest {
        coEvery { api.createWorktree(server, any()) } throws RuntimeException("wt fail")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.createWorktree()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isCreating)
        assertEquals("wt fail", vm.uiState.value.error)
    }

    @Test
    fun `clearError resets error to null`() = testScope.runTest {
        coEvery { api.listWorkspaces(server) } throws RuntimeException("ws fail")
        coEvery { api.listWorktrees(server) } throws RuntimeException("wt fail")
        coEvery { api.getExperimentalResources(server) } throws RuntimeException("res fail")

        val vm = createViewModel()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)

        vm.clearError()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `loadAll returns early when server not found`() = testScope.runTest {
        every { serverRepository.getServer("test_server") } returns null

        val vm = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { api.listWorkspaces(any()) }
        coVerify(exactly = 0) { api.listWorktrees(any()) }
        coVerify(exactly = 0) { api.getExperimentalResources(any()) }
    }
}
