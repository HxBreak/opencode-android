package me.xiaok.opencode.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.api.SseClient
import me.xiaok.opencode.data.local.security.CredentialStore
import me.xiaok.opencode.domain.model.ServerConnection
import me.xiaok.opencode.domain.model.Session
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Manages all server connections.
 * Holds SseClient instances per server.
 * On connect: health check → start SSE → fetch initial state (sessions, statuses)
 * On disconnect: stop SSE → EventReducer.clearForServer()
 */
@Singleton
class ServerRepository @Inject constructor(
    private val api: OpenCodeApi,
    @Named("sse") private val sseOkHttpClient: OkHttpClient,
    private val json: Json,
    private val eventReducer: EventReducer,
    private val credentialStore: CredentialStore,
    private val cacheRepository: CacheRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sseClients = mutableMapOf<String, SseClient>()
    private val sseJobs = mutableMapOf<String, Job>()

    private val _servers = MutableStateFlow<List<ServerConnection>>(emptyList())
    val servers: StateFlow<List<ServerConnection>> = _servers.asStateFlow()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _serverVersions = MutableStateFlow<Map<String, String>>(emptyMap())
    val serverVersions: StateFlow<Map<String, String>> = _serverVersions.asStateFlow()

    init {
        // Load saved servers on creation
        scope.launch {
            _servers.value = credentialStore.loadServers()
        }
    }

    // === Server CRUD ===

    suspend fun addServer(server: ServerConnection) {
        credentialStore.saveServer(server)
        _servers.value = credentialStore.loadServers()
    }

    suspend fun updateServer(server: ServerConnection) {
        credentialStore.saveServer(server)
        _servers.value = credentialStore.loadServers()
    }

    suspend fun removeServer(serverId: String) {
        disconnect(serverId)
        credentialStore.deleteServer(serverId)
        _servers.value = credentialStore.loadServers()
    }

    // === Connection Lifecycle ===

    /**
     * Connect to a server:
     * 1. Health check → verify server is reachable
     * 2. Start SSE stream → receive real-time events
     * 3. Fetch initial state → sessions, statuses
     * 4. Sync to cache
     */
    suspend fun connect(serverId: String) {
        val server = _servers.value.find { it.id == serverId } ?: return
        updateConnectionState(serverId, ConnectionState.CONNECTING)

        try {
            // 1. Health check
            val health = api.health(server)
            if (!health.healthy) {
                updateConnectionState(serverId, ConnectionState.ERROR("Server unhealthy"))
                return
            }

            // Store server version from health check
            _serverVersions.value = _serverVersions.value.toMutableMap().apply {
                put(serverId, health.version)
            }

            // 2. Fetch initial state
            val sessions = api.listSessions(server, roots = true)
            eventReducer.setSessions(serverId, sessions)

            // 3. Sync to cache
            cacheRepository.syncSessions(serverId, sessions)

            // 4. Start SSE
            val reconnectDelay = when (settingsRepository.reconnectMode.first()) {
                "aggressive" -> 2_000L
                "conservative" -> 30_000L
                else -> 5_000L // normal
            }
            val sseClient = SseClient(
                server = server,
                okHttpClient = sseOkHttpClient,
                json = json,
                eventReducer = eventReducer,
                initialReconnectDelayMs = reconnectDelay,
            )
            sseClients[serverId] = sseClient

            val job = scope.launch {
                sseClient.connect().collect { state ->
                    when (state) {
                        is SseClient.ConnectionState.CONNECTED ->
                            updateConnectionState(serverId, ConnectionState.CONNECTED)
                        is SseClient.ConnectionState.DISCONNECTED ->
                            updateConnectionState(serverId, ConnectionState.DISCONNECTED)
                        is SseClient.ConnectionState.ERROR ->
                            updateConnectionState(serverId, ConnectionState.ERROR(state.message))
                        is SseClient.ConnectionState.RECONNECTING ->
                            updateConnectionState(serverId, ConnectionState.CONNECTING)
                    }
                }
            }
            sseJobs[serverId] = job

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ${server.name}", e)
            updateConnectionState(serverId, ConnectionState.ERROR(e.message ?: "Connection failed"))
        }
    }

    /** Disconnect from a server: stop SSE, clear state */
    fun disconnect(serverId: String) {
        sseClients.remove(serverId)?.disconnect()
        sseJobs.remove(serverId)?.cancel()
        eventReducer.clearForServer(serverId)
        updateConnectionState(serverId, ConnectionState.DISCONNECTED)
        _serverVersions.value = _serverVersions.value.toMutableMap().apply {
            remove(serverId)
        }
    }

    /** Auto-connect all servers marked as autoConnect */
    suspend fun autoConnect() {
        val autoServers = _servers.value.filter { it.autoConnect }
        autoServers.forEach { server ->
            scope.launch { connect(server.id) }
        }
    }

    /** Disconnect all servers */
    fun disconnectAll() {
        sseClients.keys.toList().forEach { disconnect(it) }
        eventReducer.clearAll()
    }

    /** Get a specific server by ID */
    fun getServer(serverId: String): ServerConnection? {
        return _servers.value.find { it.id == serverId }
    }

    private fun updateConnectionState(serverId: String, state: ConnectionState) {
        _connectionStates.value = _connectionStates.value.toMutableMap().apply {
            put(serverId, state)
        }
    }

    /**
     * Connection state for a server.
     */
    sealed class ConnectionState {
        data object DISCONNECTED : ConnectionState()
        data object CONNECTING : ConnectionState()
        data object CONNECTED : ConnectionState()
        data class ERROR(val message: String) : ConnectionState()
    }

    companion object {
        private const val TAG = "ServerRepository"
    }
}
