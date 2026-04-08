package me.xiaok.opencode.ui.screens.home

import android.content.Intent
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.di.ServiceModule
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val cacheRepository = mockk<CacheRepository>(relaxed = true)

    private val serversFlow = MutableStateFlow<List<ServerConnection>>(emptyList())
    private val connectionStatesFlow = MutableStateFlow<Map<String, ServerRepository.ConnectionState>>(emptyMap())

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        every { serverRepository.servers } returns serversFlow
        every { serverRepository.connectionStates } returns connectionStatesFlow
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(): HomeViewModel {
        val context = RuntimeEnvironment.getApplication()
        return HomeViewModel(context, serverRepository, cacheRepository)
    }

    @Test
    fun `uiState initial state is empty`() = testScope.runTest {
        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.servers.isEmpty())
        assertTrue(state.connectionStates.isEmpty())
        assertNull(state.isConnecting)

        collectJob.cancel()
    }

    @Test
    fun `uiState combines servers and connection states`() = testScope.runTest {
        val server = TestFixtures.testServerConnection()
        val states = mapOf("server_local" to ServerRepository.ConnectionState.CONNECTED)

        serversFlow.value = listOf(server)
        connectionStatesFlow.value = states

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.servers.size)
        assertEquals(server, state.servers[0])
        assertEquals(states, state.connectionStates)
        assertNull(state.isConnecting)

        collectJob.cancel()
    }

    @Test
    fun `uiState reflects isConnecting from connection states`() = testScope.runTest {
        connectionStatesFlow.value = mapOf(
            "server_1" to ServerRepository.ConnectionState.CONNECTING,
            "server_2" to ServerRepository.ConnectionState.CONNECTED,
        )

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("server_1", state.isConnecting)

        collectJob.cancel()
    }

    @Test
    fun `uiState isConnecting is null when no server is connecting`() = testScope.runTest {
        connectionStatesFlow.value = mapOf(
            "server_1" to ServerRepository.ConnectionState.CONNECTED,
            "server_2" to ServerRepository.ConnectionState.DISCONNECTED,
        )

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNull(vm.uiState.value.isConnecting)

        collectJob.cancel()
    }

    @Test
    fun `uiState reflects ERROR connection state`() = testScope.runTest {
        connectionStatesFlow.value = mapOf(
            "server_1" to ServerRepository.ConnectionState.ERROR("timeout"),
        )

        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.connectionStates["server_1"] is ServerRepository.ConnectionState.ERROR)
        assertNull(state.isConnecting)

        collectJob.cancel()
    }

    @Test
    fun `uiState updates when servers flow changes`() = testScope.runTest {
        val vm = createViewModel()
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.servers.isEmpty())

        val server1 = TestFixtures.testServerConnection(id = "s1")
        val server2 = TestFixtures.testServerConnection(id = "s2")
        serversFlow.value = listOf(server1, server2)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.servers.size)
        assertEquals("s1", state.servers[0].id)
        assertEquals("s2", state.servers[1].id)

        collectJob.cancel()
    }

    @Test
    fun `addServer delegates to repository`() = testScope.runTest {
        val vm = createViewModel()
        val server = TestFixtures.testServerConnection()

        vm.addServer(server)
        advanceUntilIdle()

        coVerify { serverRepository.addServer(server) }
    }

    @Test
    fun `updateServer delegates to repository`() = testScope.runTest {
        val vm = createViewModel()
        val server = TestFixtures.testServerConnection(name = "Updated")

        vm.updateServer(server)
        advanceUntilIdle()

        coVerify { serverRepository.updateServer(server) }
    }

    @Test
    fun `removeServer delegates to repository`() = testScope.runTest {
        val vm = createViewModel()

        vm.removeServer("server_local")
        advanceUntilIdle()

        coVerify { serverRepository.removeServer("server_local") }
    }

    @Test
    fun `connect starts foreground service with connect intent`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = mockk<Intent>(relaxed = true)

        mockkObject(ServiceModule)
        every { ServiceModule.connectIntent(any(), any()) } returns intent

        val vm = HomeViewModel(context, serverRepository, cacheRepository)
        vm.connect("server_local")

        verify { ServiceModule.connectIntent(context, "server_local") }
        verify { context.startForegroundService(intent) }

        unmockkObject(ServiceModule)
    }

    @Test
    fun `disconnect starts service with disconnect intent`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = mockk<Intent>(relaxed = true)

        mockkObject(ServiceModule)
        every { ServiceModule.disconnectIntent(any(), any()) } returns intent

        val vm = HomeViewModel(context, serverRepository, cacheRepository)
        vm.disconnect("server_local")

        verify { ServiceModule.disconnectIntent(context, "server_local") }
        verify { context.startService(intent) }

        unmockkObject(ServiceModule)
    }
}
