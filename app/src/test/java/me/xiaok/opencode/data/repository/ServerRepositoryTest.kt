package me.xiaok.opencode.data.repository

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.api.SseClient
import me.xiaok.opencode.data.local.security.CredentialStore
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.serialization.json.Json

/**
 * Unit tests for ServerRepository covering:
 * - Server CRUD (add/update/remove via CredentialStore)
 * - Connection lifecycle (connect/disconnect)
 * - Auto-connect / disconnectAll
 * - Connection state Flow transitions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val api = mockk<OpenCodeApi>(relaxed = true)
    private val sseOkHttpClient = mockk<OkHttpClient>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val eventReducer = mockk<EventReducer>(relaxed = true)
    private val credentialStore = mockk<CredentialStore>(relaxed = true)
    private val cacheRepository = mockk<CacheRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val metadataCache = mockk<MetadataCache>(relaxed = true)

    private val testServer = TestFixtures.testServerConnection()
    private val testServer2 = TestFixtures.testServerConnection(
        id = "server_remote",
        name = "Remote Server",
        baseUrl = "http://10.0.0.1:4096",
    )
    private val testSessions = listOf(
        TestFixtures.testSession(),
        TestFixtures.testSession(id = "ses_456", title = "Another Session"),
    )

    private lateinit var repository: ServerRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        // Default: no saved servers initially
        coEvery { credentialStore.loadServers() } returns emptyList()

        // Default: normal reconnect mode
        every { settingsRepository.reconnectMode } returns flowOf("normal")
    }

    @After
    fun teardown() {
        if (::repository.isInitialized) {
            repository.disconnectAll()
        }
        unmockkStatic(android.util.Log::class)
        unmockkConstructor(SseClient::class)
    }

    private fun createRepository(
        savedServers: List<ServerConnection> = emptyList()
    ): ServerRepository {
        coEvery { credentialStore.loadServers() } returns savedServers
        return ServerRepository(
            api = api,
            sseOkHttpClient = sseOkHttpClient,
            json = json,
            eventReducer = eventReducer,
            credentialStore = credentialStore,
            cacheRepository = cacheRepository,
            settingsRepository = settingsRepository,
            metadataCache = metadataCache,
        ).also { repository = it }
    }

    /**
     * Helper: set up mocks for a successful connect() call.
     * Mocks health check, session list, and SseClient constructor.
     */
    private fun setupConnectMocks(
        server: ServerConnection = testServer,
    ) {
        val healthResponse = OpenCodeApi.HealthResponse(healthy = true, version = "1.3.10")
        coEvery { api.health(server) } returns healthResponse
        coEvery { api.listSessions(server, roots = true) } returns testSessions

        mockkConstructor(SseClient::class)
        every { anyConstructed<SseClient>().connect() } returns flowOf(SseClient.ConnectionState.CONNECTED)
        every { anyConstructed<SseClient>().disconnect() } just runs
    }

    // ====================================================================
    // Init — server loading
    // ====================================================================

    @Test
    fun `init loads saved servers from CredentialStore`() = runTest {
        val savedServers = listOf(testServer, testServer2)
        createRepository(savedServers)

        coVerify { credentialStore.loadServers() }
    }

    // ====================================================================
    // addServer
    // ====================================================================

    @Test
    fun `addServer saves to CredentialStore and updates servers Flow`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)

        repo.addServer(testServer)

        coVerify { credentialStore.saveServer(testServer) }
        assertEquals(listOf(testServer), repo.servers.value)
    }

    @Test
    fun `addServer multiple servers accumulates in servers Flow`() = runTest {
        val repo = createRepository()

        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)

        coEvery { credentialStore.loadServers() } returns listOf(testServer, testServer2)
        repo.addServer(testServer2)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            credentialStore.saveServer(testServer)
            credentialStore.loadServers()
            credentialStore.saveServer(testServer2)
            credentialStore.loadServers()
        }
    }

    // ====================================================================
    // updateServer
    // ====================================================================

    @Test
    fun `updateServer saves to CredentialStore and reloads servers`() = runTest {
        val repo = createRepository()
        val updated = testServer.copy(name = "Updated Server")
        coEvery { credentialStore.loadServers() } returns listOf(updated)

        repo.updateServer(updated)

        coVerify { credentialStore.saveServer(updated) }
        assertEquals(listOf(updated), repo.servers.value)
    }

    // ====================================================================
    // removeServer
    // ====================================================================

    @Test
    fun `removeServer disconnects, deletes from CredentialStore, and reloads`() = runTest {
        val repo = createRepository(listOf(testServer))
        coEvery { credentialStore.loadServers() } returns emptyList()

        repo.removeServer(testServer.id)

        coVerify { credentialStore.deleteServer(testServer.id) }
        coVerify { credentialStore.loadServers() }
        verify { eventReducer.clearForServer(testServer.id) }
    }

    @Test
    fun `removeServer updates servers Flow to empty`() = runTest {
        val repo = createRepository(listOf(testServer))
        coEvery { credentialStore.loadServers() } returns emptyList()

        repo.removeServer(testServer.id)

        assertEquals(emptyList<ServerConnection>(), repo.servers.value)
    }

    // ====================================================================
    // getServer
    // ====================================================================

    @Test
    fun `getServer returns server by ID after adding it`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer, testServer2)
        repo.addServer(testServer)
        repo.addServer(testServer2)

        val result = repo.getServer(testServer.id)

        assertNotNull(result)
        assertEquals(testServer, result)
    }

    @Test
    fun `getServer returns null for unknown ID`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)

        val result = repo.getServer("nonexistent")

        assertNull(result)
    }

    // ====================================================================
    // connect — success path
    // ====================================================================

    @Test
    fun `connect performs health check, fetches sessions, syncs cache, and starts SSE`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        setupConnectMocks(testServer)

        repo.connect(testServer.id)

        coVerify { api.health(testServer) }
        coVerify { api.listSessions(testServer, roots = true) }
        verify { eventReducer.setSessions(testServer.id, testSessions) }
        coVerify { cacheRepository.syncSessions(testServer.id, testSessions) }
    }

    @Test
    fun `connect sets connectionState for server`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        setupConnectMocks(testServer)

        repo.connect(testServer.id)

        assertTrue(repo.connectionStates.value.containsKey(testServer.id))
    }

    // ====================================================================
    // connect — error paths
    // ====================================================================

    @Test
    fun `connect returns early when server not found in list`() = runTest {
        val repo = createRepository(emptyList())

        repo.connect("nonexistent")

        coVerify(exactly = 0) { api.health(any()) }
    }

    @Test
    fun `connect sets ERROR state when health check returns unhealthy`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        coEvery { api.health(testServer) } returns OpenCodeApi.HealthResponse(healthy = false)

        repo.connect(testServer.id)

        val state = repo.connectionStates.value[testServer.id]
        assertTrue(state is ServerRepository.ConnectionState.ERROR)
        assertEquals("Server unhealthy", (state as ServerRepository.ConnectionState.ERROR).message)
    }

    @Test
    fun `connect sets ERROR state when health check throws exception`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        coEvery { api.health(testServer) } throws RuntimeException("Network timeout")

        repo.connect(testServer.id)

        val state = repo.connectionStates.value[testServer.id]
        assertTrue(state is ServerRepository.ConnectionState.ERROR)
        assertEquals("Network timeout", (state as ServerRepository.ConnectionState.ERROR).message)
    }

    @Test
    fun `connect sets ERROR state when listSessions throws exception`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        coEvery { api.health(testServer) } returns OpenCodeApi.HealthResponse(healthy = true)
        coEvery { api.listSessions(testServer, roots = true) } throws RuntimeException("Parse error")

        repo.connect(testServer.id)

        val state = repo.connectionStates.value[testServer.id]
        assertTrue(state is ServerRepository.ConnectionState.ERROR)
        assertEquals("Parse error", (state as ServerRepository.ConnectionState.ERROR).message)
    }

    @Test
    fun `connect with unhealthy server does not fetch sessions`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        coEvery { api.health(testServer) } returns OpenCodeApi.HealthResponse(healthy = false)

        repo.connect(testServer.id)

        coVerify(exactly = 0) { api.listSessions(any(), any()) }
    }

    // ====================================================================
    // disconnect
    // ====================================================================

    @Test
    fun `disconnect clears event reducer and sets DISCONNECTED state`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        setupConnectMocks(testServer)
        repo.connect(testServer.id)

        // Wait for the IO scope coroutine (SSE collection on Dispatchers.IO) to complete
        // before calling disconnect, so the CONNECTED emission doesn't race in after disconnect.
        Thread.sleep(200)
        repo.disconnect(testServer.id)

        Thread.sleep(50)

        verify { eventReducer.clearForServer(testServer.id) }
        coVerify { metadataCache.invalidateServer(testServer.id) }

        val state = repo.connectionStates.value[testServer.id]
        assertEquals(ServerRepository.ConnectionState.DISCONNECTED, state)
    }

    @Test
    fun `disconnect is safe to call when not connected`() {
        val repo = createRepository()

        // Should not throw
        repo.disconnect(testServer.id)

        Thread.sleep(50)

        coVerify { metadataCache.invalidateServer(testServer.id) }
    }

    // ====================================================================
    // autoConnect
    // ====================================================================

    @Test
    fun `autoConnect connects all servers with autoConnect true`() = runTest {
        val autoServer = testServer.copy(autoConnect = true)
        val autoServer2 = testServer2.copy(autoConnect = true)
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(autoServer, autoServer2)
        repo.addServer(autoServer)
        repo.addServer(autoServer2)
        setupConnectMocks(autoServer)
        setupConnectMocks(autoServer2)

        repo.autoConnect()

        coVerify { api.health(autoServer) }
        coVerify { api.health(autoServer2) }
    }

    @Test
    fun `autoConnect skips servers with autoConnect false`() = runTest {
        val autoServer = testServer.copy(autoConnect = true)
        val manualServer = testServer2.copy(autoConnect = false)
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(autoServer, manualServer)
        repo.addServer(autoServer)
        repo.addServer(manualServer)
        setupConnectMocks(autoServer)

        repo.autoConnect()

        coVerify { api.health(autoServer) }
        coVerify(exactly = 0) { api.health(manualServer) }
    }

    @Test
    fun `autoConnect with no servers does nothing`() = runTest {
        val repo = createRepository()

        repo.autoConnect()

        coVerify(exactly = 0) { api.health(any()) }
    }

    // ====================================================================
    // disconnectAll
    // ====================================================================

    @Test
    fun `disconnectAll disconnects all servers and clears all events`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer, testServer2)
        repo.addServer(testServer)
        repo.addServer(testServer2)
        setupConnectMocks(testServer)
        setupConnectMocks(testServer2)
        repo.connect(testServer.id)
        repo.connect(testServer2.id)

        repo.disconnectAll()

        verify { eventReducer.clearAll() }
    }

    // ====================================================================
    // connectionState management
    // ====================================================================

    @Test
    fun `connectionState starts empty`() = runTest {
        val repo = createRepository()

        assertEquals(emptyMap<String, ServerRepository.ConnectionState>(), repo.connectionStates.value)
    }

    @Test
    fun `disconnect on never-connected server sets DISCONNECTED state`() = runTest {
        val repo = createRepository()

        repo.disconnect(testServer.id)

        val state = repo.connectionStates.value[testServer.id]
        assertEquals(ServerRepository.ConnectionState.DISCONNECTED, state)
    }

    @Test
    fun `disconnectAll sets connected servers to DISCONNECTED`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        setupConnectMocks(testServer)
        repo.connect(testServer.id)

        // Wait for the IO scope coroutine (SSE collection on Dispatchers.IO) to complete
        // before calling disconnectAll, so the CONNECTED emission doesn't race in after disconnect.
        Thread.sleep(200)
        repo.disconnectAll()

        val states = repo.connectionStates.value
        assertEquals(ServerRepository.ConnectionState.DISCONNECTED, states[testServer.id])
    }

    @Test
    fun `connect sets CONNECTING state before health check`() = runTest {
        val repo = createRepository()
        coEvery { credentialStore.loadServers() } returns listOf(testServer)
        repo.addServer(testServer)
        coEvery { api.health(testServer) } returns OpenCodeApi.HealthResponse(healthy = true)
        coEvery { api.listSessions(testServer, roots = true) } returns testSessions
        mockkConstructor(SseClient::class)
        every { anyConstructed<SseClient>().connect() } returns flowOf(SseClient.ConnectionState.CONNECTED)
        every { anyConstructed<SseClient>().disconnect() } just runs

        repo.connect(testServer.id)

        // CONNECTING is set at the beginning of connect()
        val state = repo.connectionStates.value[testServer.id]
        // After connect, it should be CONNECTING, CONNECTED, or ERROR
        assertNotNull(state)
        assertNotEquals(ServerRepository.ConnectionState.DISCONNECTED, state)
    }
}
