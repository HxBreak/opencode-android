package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.MetadataCache
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import me.xiaok.opencode.utils.ErrorCollector
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerProvidersViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private val api: OpenCodeApi = mockk()
    private val metadataCache: MetadataCache = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val errorCollector: ErrorCollector = mockk(relaxed = true)
    private val testServer = TestFixtures.testServerConnection()

    /** Advance both testScope and viewModelScope's dispatcher scheduler */
    private fun advanceAll() {
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
    }

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

    private fun createVm(): ServerProvidersViewModel {
        every { serverRepository.getServer("test_server") } returns testServer
        val vm = ServerProvidersViewModel(
            SavedStateHandle(mapOf("serverId" to "test_server")),
            api,
            metadataCache,
            serverRepository,
            errorCollector,
        )
        testScope.backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `loadProviders success updates state with providers`() = testScope.runTest {
        val providerList = TestFixtures.testProviderList()
        // Setup mocks BEFORE creating ViewModel
        every { serverRepository.getServer("test_server") } returns testServer
        coEvery { metadataCache.getProviders("test_server", testServer) } returns providerList

        // Create ViewModel directly (init will be called immediately)
        val vm = ServerProvidersViewModel(
            SavedStateHandle(mapOf("serverId" to "test_server")),
            api,
            metadataCache,
            serverRepository,
            errorCollector,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceAll()

        val state = vm.uiState.value
        assertEquals(providerList, state.providers)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `loadProviders failure sets error`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } throws RuntimeException("Network error")

        val vm = createVm()
        advanceAll()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Network error", state.error)
    }

    @Test
    fun `loadProviders when server not found returns early`() = testScope.runTest {
        every { serverRepository.getServer("test_server") } returns null

        val vm = createVm()
        advanceAll()

        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadProviders loads auth methods in parallel`() = testScope.runTest {
        val providerList = TestFixtures.testProviderList()
        val authMethods = JsonObject(mapOf("anthropic" to JsonPrimitive("api_key")))
        coEvery { metadataCache.getProviders("test_server", testServer) } returns providerList
        coEvery { api.getProviderAuthMethods(testServer) } returns authMethods

        val vm = createVm()
        advanceAll()

        assertEquals(authMethods, vm.uiState.value.authMethods)
    }

    @Test
    fun `connectWithApiKey calls setAuth and reloads`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { metadataCache.refreshProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { api.setAuth(testServer, "anthropic", any()) } returns true

        val vm = createVm()
        advanceAll()

        vm.connectWithApiKey("anthropic", "sk-test-key")
        advanceAll()

        coVerify { api.setAuth(testServer, "anthropic", any()) }
        coVerify { metadataCache.refreshProviders("test_server", testServer) }
    }

    @Test
    fun `connectWithApiKey failure sets error`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { api.setAuth(testServer, any(), any()) } throws RuntimeException("Auth failed")

        val vm = createVm()
        advanceAll()

        vm.connectWithApiKey("anthropic", "sk-test-key")
        advanceAll()

        assertEquals("Auth failed", vm.uiState.value.error)
    }

    @Test
    fun `disconnectProvider calls removeAuth and reloads`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { metadataCache.refreshProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { api.removeAuth(testServer, "anthropic") } returns true

        val vm = createVm()
        advanceAll()

        vm.disconnectProvider("anthropic")
        advanceAll()

        coVerify { api.removeAuth(testServer, "anthropic") }
        coVerify { metadataCache.refreshProviders("test_server", testServer) }
    }

    @Test
    fun `startOAuth sets oauth url and instructions`() = testScope.runTest {
        val oauthResult = JsonObject(mapOf(
            "url" to JsonPrimitive("https://auth.example.com/authorize"),
            "instructions" to JsonPrimitive("Visit the URL to authorize"),
        ))
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { api.authorizeOAuth(testServer, "anthropic", 0) } returns oauthResult

        val vm = createVm()
        advanceAll()

        vm.startOAuth("anthropic", 0)
        advanceAll()

        assertEquals("https://auth.example.com/authorize", vm.uiState.value.oauthUrl)
        assertEquals("Visit the URL to authorize", vm.uiState.value.oauthInstructions)
    }

    @Test
    fun `completeOAuth clears oauth state and reloads`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { metadataCache.refreshProviders("test_server", testServer) } returns TestFixtures.testProviderList()
        coEvery { api.completeOAuth(testServer, "anthropic", 0, "auth-code") } returns true

        val vm = createVm()
        advanceAll()

        vm.completeOAuth("anthropic", 0, "auth-code")
        advanceAll()

        assertNull(vm.uiState.value.oauthUrl)
        assertNull(vm.uiState.value.oauthInstructions)
    }

    @Test
    fun `clearOAuthState clears url and instructions`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()

        val vm = createVm()
        advanceAll()

        vm.clearOAuthState()

        assertNull(vm.uiState.value.oauthUrl)
        assertNull(vm.uiState.value.oauthInstructions)
    }

    @Test
    fun `clearError clears error state`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } throws RuntimeException("some error")

        val vm = createVm()
        advanceAll()

        assertNotNull(vm.uiState.value.error)

        vm.clearError()
        advanceAll()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `setSearchQuery updates searchQuery in state`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()

        val vm = createVm()
        advanceAll()

        assertEquals("", vm.uiState.value.searchQuery)

        vm.setSearchQuery("anthropic")
        advanceAll()

        assertEquals("anthropic", vm.uiState.value.searchQuery)
    }

    @Test
    fun `setSearchQuery with empty string clears search`() = testScope.runTest {
        coEvery { metadataCache.getProviders("test_server", testServer) } returns TestFixtures.testProviderList()

        val vm = createVm()
        advanceAll()

        vm.setSearchQuery("anthropic")
        advanceAll()
        assertEquals("anthropic", vm.uiState.value.searchQuery)

        vm.setSearchQuery("")
        advanceAll()
        assertEquals("", vm.uiState.value.searchQuery)
    }
}
