package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
class McpManagementViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private val api: OpenCodeApi = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val errorCollector: ErrorCollector = mockk(relaxed = true)
    private val testServer = TestFixtures.testServerConnection()

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun teardown() { unmockkStatic(android.util.Log::class) }

    private fun createVm(): McpManagementViewModel {
        every { serverRepository.getServer("test_server") } returns testServer
        return McpManagementViewModel(
            SavedStateHandle(mapOf("serverId" to "test_server")),
            api,
            serverRepository,
            errorCollector,
        )
    }

    @Test
    fun `loadServers success updates state with servers`() = testScope.runTest {
        val servers = mapOf("filesystem" to TestFixtures.testMcpStatus())
        coEvery { api.listMcpServers(testServer) } returns servers

        val vm = createVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(servers, state.mcpServers)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadServers failure sets error`() = testScope.runTest {
        coEvery { api.listMcpServers(testServer) } throws RuntimeException("Connection refused")

        val vm = createVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Connection refused", state.error)
    }

    @Test
    fun `loadServers when server not found returns early`() = testScope.runTest {
        every { serverRepository.getServer("test_server") } returns null

        val vm = createVm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `addServer success reloads servers`() = testScope.runTest {
        val servers = mapOf("filesystem" to TestFixtures.testMcpStatus())
        val request = TestFixtures.testMcpServerCreateRequest()
        coEvery { api.listMcpServers(testServer) } returns servers
        coEvery { api.addMcpServer(testServer, request) } returns servers

        val vm = createVm()
        advanceUntilIdle()

        vm.addServer(request)
        advanceUntilIdle()

        coVerify { api.addMcpServer(testServer, request) }
        assertFalse(vm.uiState.value.showAddDialog)
    }

    @Test
    fun `addServer failure sets error`() = testScope.runTest {
        val request = TestFixtures.testMcpServerCreateRequest()
        coEvery { api.listMcpServers(testServer) } returns emptyMap()
        coEvery { api.addMcpServer(testServer, request) } throws RuntimeException("Add failed")

        val vm = createVm()
        advanceUntilIdle()

        vm.addServer(request)
        advanceUntilIdle()

        assertEquals("Add failed", vm.uiState.value.error)
    }

    @Test
    fun `connectServer success reloads servers`() = testScope.runTest {
        val servers = mapOf("filesystem" to TestFixtures.testMcpStatus())
        coEvery { api.listMcpServers(testServer) } returns servers
        coEvery { api.connectMcpServer(testServer, "filesystem") } returns true

        val vm = createVm()
        advanceUntilIdle()

        vm.connectServer("filesystem")
        advanceUntilIdle()

        coVerify { api.connectMcpServer(testServer, "filesystem") }
    }

    @Test
    fun `disconnectServer success reloads servers`() = testScope.runTest {
        val servers = mapOf("filesystem" to TestFixtures.testMcpStatus())
        coEvery { api.listMcpServers(testServer) } returns servers
        coEvery { api.disconnectMcpServer(testServer, "filesystem") } returns true

        val vm = createVm()
        advanceUntilIdle()

        vm.disconnectServer("filesystem")
        advanceUntilIdle()

        coVerify { api.disconnectMcpServer(testServer, "filesystem") }
    }

    @Test
    fun `removeAuth success reloads servers`() = testScope.runTest {
        coEvery { api.listMcpServers(testServer) } returns emptyMap()
        coEvery { api.removeMcpAuth(testServer, "github") } returns true

        val vm = createVm()
        advanceUntilIdle()

        vm.removeAuth("github")
        advanceUntilIdle()

        coVerify { api.removeMcpAuth(testServer, "github") }
    }

    @Test
    fun `removeAuth failure sets error`() = testScope.runTest {
        coEvery { api.listMcpServers(testServer) } returns emptyMap()
        coEvery { api.removeMcpAuth(testServer, "github") } throws RuntimeException("Remove failed")

        val vm = createVm()
        advanceUntilIdle()

        vm.removeAuth("github")
        advanceUntilIdle()

        assertEquals("Remove failed", vm.uiState.value.error)
    }

    @Test
    fun `showAddDialog sets showAddDialog true`() = testScope.runTest {
        coEvery { api.listMcpServers(testServer) } returns emptyMap()

        val vm = createVm()
        advanceUntilIdle()

        vm.showAddDialog()

        assertTrue(vm.uiState.value.showAddDialog)
    }

    @Test
    fun `dismissAddDialog sets showAddDialog false`() = testScope.runTest {
        coEvery { api.listMcpServers(testServer) } returns emptyMap()

        val vm = createVm()
        advanceUntilIdle()

        vm.showAddDialog()
        assertTrue(vm.uiState.value.showAddDialog)

        vm.dismissAddDialog()
        assertFalse(vm.uiState.value.showAddDialog)
    }

    @Test
    fun `clearError clears error state`() = testScope.runTest {
        coEvery { api.listMcpServers(testServer) } throws RuntimeException("some error")

        val vm = createVm()
        advanceUntilIdle()

        assertEquals("some error", vm.uiState.value.error)

        vm.clearError()

        assertNull(vm.uiState.value.error)
    }
}
