package me.xiaok.opencode.ui.screens.home

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.local.db.entity.SessionEntity
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.di.ServiceModule
import me.xiaok.opencode.domain.model.ServerConnection
import javax.inject.Inject

data class RecentSessionItem(
    val session: SessionEntity,
    val serverName: String,
    val serverId: String,
    val isConnected: Boolean,
)

data class HomeUiState(
    val servers: List<ServerConnection> = emptyList(),
    val connectionStates: Map<String, ServerRepository.ConnectionState> = emptyMap(),
    val serverVersions: Map<String, String> = emptyMap(),
    val isConnecting: String? = null,  // serverId currently connecting
    val recentSessions: List<RecentSessionItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverRepository: ServerRepository,
    private val cacheRepository: CacheRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        serverRepository.servers,
        serverRepository.connectionStates,
        serverRepository.serverVersions,
        cacheRepository.getRecentSessions(limit = 7),
    ) { servers, connectionStates, serverVersions, recentEntities ->
        HomeUiState(
            servers = servers,
            connectionStates = connectionStates,
            serverVersions = serverVersions,
            isConnecting = connectionStates.entries
                .firstOrNull { it.value == ServerRepository.ConnectionState.CONNECTING }
                ?.key,
            recentSessions = recentEntities.mapNotNull { entity ->
                val server = servers.find { it.id == entity.serverId } ?: return@mapNotNull null
                RecentSessionItem(
                    session = entity,
                    serverName = server.name,
                    serverId = entity.serverId,
                    isConnected = connectionStates[entity.serverId] == ServerRepository.ConnectionState.CONNECTED,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun addServer(server: ServerConnection) {
        viewModelScope.launch {
            serverRepository.addServer(server)
        }
    }

    fun updateServer(server: ServerConnection) {
        viewModelScope.launch {
            serverRepository.updateServer(server)
        }
    }

    fun removeServer(serverId: String) {
        viewModelScope.launch {
            serverRepository.removeServer(serverId)
        }
    }

    /**
     * Connect to a server by starting the ForegroundService with ACTION_CONNECT.
     * The service handles the actual connection (health check → SSE).
     */
    fun connect(serverId: String) {
        val intent = ServiceModule.connectIntent(context, serverId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Disconnect from a server by sending ACTION_DISCONNECT to the service.
     * The service handles stopping SSE and clearing state.
     */
    fun disconnect(serverId: String) {
        val intent = ServiceModule.disconnectIntent(context, serverId)
        context.startService(intent)
    }
}
