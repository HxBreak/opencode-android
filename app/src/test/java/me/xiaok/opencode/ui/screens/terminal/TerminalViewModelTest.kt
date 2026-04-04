package me.xiaok.opencode.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.api.WsClient
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import me.xiaok.opencode.utils.ErrorCollector
import okhttp3.WebSocket
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
class TerminalViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private val api = mockk<OpenCodeApi>(relaxed = true)
    private val wsClient = mockk<WsClient>(relaxed = true)
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

    private fun mockConnection(ptyId: String = "pty_123"): WsClient.InteractiveTerminalConnection {
        return WsClient.InteractiveTerminalConnection(
            output = flowOf("hello"),
            isConnected = MutableStateFlow(true),
            cursorFlow = MutableStateFlow(null),
            ptyId = ptyId,
            getWebSocket = { mockk(relaxed = true) },
        )
    }

    private fun createViewModel(sessionId: String? = null): TerminalViewModel {
        val map = mutableMapOf<String, Any?>("serverId" to "test_server")
        sessionId?.let { map["sessionId"] = it }
        val savedStateHandle = SavedStateHandle(map)
        return TerminalViewModel(savedStateHandle, api, wsClient, serverRepository, eventReducer, errorCollector)
    }

    @Test
    fun `startTerminal creates PTY and connects`() = testScope.runTest {
        val ptyInfo = TestFixtures.testPtyInfo(id = "pty_abc")
        coEvery { api.createPty(server, any(), any()) } returns ptyInfo
        every { wsClient.connectInteractive(any(), any(), any(), any(), any()) } returns mockConnection("pty_abc")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isConnected)
        assertFalse(state.isConnecting)
        assertEquals("pty_abc", state.ptyId)
        assertNull(state.error)

        collectJob.cancel()
    }

    @Test
    fun `startTerminal sets error when server not found`() = testScope.runTest {
        every { serverRepository.getServer("test_server") } returns null

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isConnected)
        assertEquals("Server not found", state.error)

        collectJob.cancel()
    }

    @Test
    fun `startTerminal sets error when createPty fails`() = testScope.runTest {
        coEvery { api.createPty(server, any(), any()) } throws RuntimeException("pty fail")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isConnected)
        assertEquals("pty fail", state.error)
        assertFalse(state.isConnecting)

        collectJob.cancel()
    }

    @Test
    fun `startTerminal sets error when ptyId is blank`() = testScope.runTest {
        val ptyInfo = TestFixtures.testPtyInfo(id = "")
        coEvery { api.createPty(server, any(), any()) } returns ptyInfo

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isConnected)
        assertEquals("Failed to create terminal session", state.error)

        collectJob.cancel()
    }

    @Test
    fun `startTerminal creates terminal state`() = testScope.runTest {
        val ptyInfo = TestFixtures.testPtyInfo(id = "pty_state")
        coEvery { api.createPty(server, any(), any()) } returns ptyInfo
        every { wsClient.connectInteractive(any(), any(), any(), any(), any()) } returns mockConnection("pty_state")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNotNull(vm.terminalState.value)

        collectJob.cancel()
    }

    @Test
    fun `stopTerminal disconnects and clears state`() = testScope.runTest {
        val ptyInfo = TestFixtures.testPtyInfo(id = "pty_stop")
        val connection = mockk<WsClient.InteractiveTerminalConnection>(relaxed = true)
        coEvery { api.createPty(server, any(), any()) } returns ptyInfo
        every { wsClient.connectInteractive(any(), any(), any(), any(), any()) } returns connection

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isConnected)

        vm.stopTerminal()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isConnected)
        assertNull(vm.terminalState.value)
        assertNull(vm.uiState.value.ptyId)
        verify { connection.disconnect() }

        collectJob.cancel()
    }

    @Test
    fun `sendTerminalInput delegates to connection`() = testScope.runTest {
        val ptyInfo = TestFixtures.testPtyInfo(id = "pty_send")
        val connection = mockk<WsClient.InteractiveTerminalConnection>(relaxed = true)
        coEvery { api.createPty(server, any(), any()) } returns ptyInfo
        every { wsClient.connectInteractive(any(), any(), any(), any(), any()) } returns connection
        every { connection.send("ls -la") } returns true

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.sendTerminalInput("ls -la")

        verify { connection.send("ls -la") }

        collectJob.cancel()
    }

    @Test
    fun `resizeTerminal calls api updatePty`() = testScope.runTest {
        val ptyInfo = TestFixtures.testPtyInfo(id = "pty_resize")
        val connection = mockk<WsClient.InteractiveTerminalConnection>(relaxed = true)
        every { connection.ptyId } returns "pty_resize"
        coEvery { api.createPty(server, any(), any()) } returns ptyInfo
        every { wsClient.connectInteractive(any(), any(), any(), any(), any()) } returns connection
        coEvery { api.updatePty(any(), any(), any(), any()) } returns ptyInfo

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.resizeTerminal(120, 40)
        advanceUntilIdle()

        coVerify { api.updatePty(server, "pty_resize", any(), any()) }

        collectJob.cancel()
    }

    @Test
    fun `dismissError clears error`() = testScope.runTest {
        coEvery { api.createPty(server, any(), any()) } throws RuntimeException("err")

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)

        vm.dismissError()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        collectJob.cancel()

    }
}
