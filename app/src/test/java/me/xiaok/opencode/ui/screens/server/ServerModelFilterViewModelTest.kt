package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
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
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerModelFilterViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private val api: OpenCodeApi = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testServer = TestFixtures.testServerConnection()

    private val hiddenModelsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val hiddenProvidersFlow = MutableStateFlow<Set<String>>(emptySet())

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        every { settingsRepository.getHiddenModels("test_server") } returns hiddenModelsFlow
        every { settingsRepository.getHiddenProviders("test_server") } returns hiddenProvidersFlow
    }

    @After
    fun teardown() { unmockkStatic(android.util.Log::class) }

    private fun createVm(
        server: ServerConnection? = testServer
    ): ServerModelFilterViewModel {
        every { serverRepository.getServer("test_server") } returns server
        return ServerModelFilterViewModel(
            SavedStateHandle(mapOf("serverId" to "test_server")),
            api,
            serverRepository,
            settingsRepository,
        )
    }

    @Test
    fun `loadProviders success updates providers list`() = testScope.runTest {
        val providerList = TestFixtures.testProviderList()
        coEvery { api.getProviders(testServer) } returns providerList

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.providers.size)
        assertEquals("anthropic", state.providers.first().id)
    }

    @Test
    fun `loadProviders failure sets error`() = testScope.runTest {
        coEvery { api.getProviders(testServer) } throws RuntimeException("Network error")

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("Network error", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadProviders does not call api when server not found`() = testScope.runTest {
        val vm = createVm(server = null)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        coVerify(exactly = 0) { api.getProviders(any()) }
    }

    @Test
    fun `loadProviders filters to connected providers only`() = testScope.runTest {
        val connectedProvider = TestFixtures.testProvider(id = "anthropic")
        val disconnectedProvider = TestFixtures.testProvider(id = "openai", name = "OpenAI")
        val providerList = TestFixtures.testProviderList(
            all = listOf(connectedProvider, disconnectedProvider),
            connected = listOf("anthropic"),
        )
        coEvery { api.getProviders(testServer) } returns providerList

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val providers = vm.uiState.value.providers
        assertEquals(1, providers.size)
        assertEquals("anthropic", providers.first().id)
    }

    @Test
    fun `toggleModelVisibility adds model to hidden set`() = testScope.runTest {
        coEvery { api.getProviders(testServer) } returns TestFixtures.testProviderList()

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.toggleModelVisibility("claude-3-sonnet")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hiddenModels.contains("claude-3-sonnet"))
        coVerify { settingsRepository.setHiddenModels("test_server", setOf("claude-3-sonnet")) }
    }

    @Test
    fun `toggleModelVisibility removes model from hidden set`() = testScope.runTest {
        coEvery { api.getProviders(testServer) } returns TestFixtures.testProviderList()
        hiddenModelsFlow.value = setOf("claude-3-sonnet")

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.toggleModelVisibility("claude-3-sonnet")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hiddenModels.contains("claude-3-sonnet"))
        coVerify { settingsRepository.setHiddenModels("test_server", emptySet()) }
    }

    @Test
    fun `toggleProviderVisibility adds provider to hidden set`() = testScope.runTest {
        coEvery { api.getProviders(testServer) } returns TestFixtures.testProviderList()

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.toggleProviderVisibility("anthropic")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hiddenProviders.contains("anthropic"))
        coVerify { settingsRepository.setHiddenProviders("test_server", setOf("anthropic")) }
    }

    @Test
    fun `toggleProviderVisibility removes provider from hidden set`() = testScope.runTest {
        coEvery { api.getProviders(testServer) } returns TestFixtures.testProviderList()
        hiddenProvidersFlow.value = setOf("anthropic")

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.toggleProviderVisibility("anthropic")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hiddenProviders.contains("anthropic"))
        coVerify { settingsRepository.setHiddenProviders("test_server", emptySet()) }
    }

    @Test
    fun `setSearchQuery updates search query`() = testScope.runTest {
        coEvery { api.getProviders(testServer) } returns TestFixtures.testProviderList()

        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.setSearchQuery("claude")
        advanceUntilIdle()

        assertEquals("claude", vm.uiState.value.searchQuery)
    }
}
