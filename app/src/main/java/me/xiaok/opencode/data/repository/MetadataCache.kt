package me.xiaok.opencode.data.repository

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.domain.model.AgentConfig
import me.xiaok.opencode.domain.model.CommandInfo
import me.xiaok.opencode.domain.model.ProviderList
import me.xiaok.opencode.domain.model.ServerConnection

@Singleton
class MetadataCache @Inject constructor(
    private val api: OpenCodeApi,
) {
    private data class CachedEntry<T>(
        val data: T,
        val timestampMs: Long,
    )

    private val providersCache = mutableMapOf<String, CachedEntry<ProviderList>>()
    private val agentsCache = mutableMapOf<String, CachedEntry<List<AgentConfig>>>()
    private val commandsCache = mutableMapOf<String, CachedEntry<List<CommandInfo>>>()
    private val mutex = Mutex()

    suspend fun getProviders(serverId: String, server: ServerConnection): ProviderList {
        return getOrLoad(
            serverId = serverId,
            ttlMs = PROVIDERS_TTL_MS,
            cache = providersCache,
            loader = { api.getProviders(server) },
            label = "providers",
        )
    }

    suspend fun getAgents(serverId: String, server: ServerConnection): List<AgentConfig> {
        return getOrLoad(
            serverId = serverId,
            ttlMs = AGENTS_TTL_MS,
            cache = agentsCache,
            loader = { api.getAgents(server) },
            label = "agents",
        )
    }

    suspend fun getCommands(serverId: String, server: ServerConnection): List<CommandInfo> {
        return getOrLoad(
            serverId = serverId,
            ttlMs = COMMANDS_TTL_MS,
            cache = commandsCache,
            loader = { api.getCommands(server) },
            label = "commands",
        )
    }

    suspend fun refreshProviders(serverId: String, server: ServerConnection): ProviderList {
        return refresh(
            serverId = serverId,
            cache = providersCache,
            loader = { api.getProviders(server) },
            label = "providers",
        )
    }

    suspend fun refreshAgents(serverId: String, server: ServerConnection): List<AgentConfig> {
        return refresh(
            serverId = serverId,
            cache = agentsCache,
            loader = { api.getAgents(server) },
            label = "agents",
        )
    }

    suspend fun refreshCommands(serverId: String, server: ServerConnection): List<CommandInfo> {
        return refresh(
            serverId = serverId,
            cache = commandsCache,
            loader = { api.getCommands(server) },
            label = "commands",
        )
    }

    suspend fun invalidateServer(serverId: String) {
        mutex.withLock {
            providersCache.remove(serverId)
            agentsCache.remove(serverId)
            commandsCache.remove(serverId)
        }
    }

    suspend fun invalidateAll() {
        mutex.withLock {
            providersCache.clear()
            agentsCache.clear()
            commandsCache.clear()
        }
    }

    private suspend fun <T> getOrLoad(
        serverId: String,
        ttlMs: Long,
        cache: MutableMap<String, CachedEntry<T>>,
        loader: suspend () -> T,
        label: String,
    ): T {
        val cached = mutex.withLock { cache[serverId] }
        if (cached != null && !isExpired(cached.timestampMs, ttlMs)) {
            Log.d(TAG, "getOrLoad: returning cached $label for serverId=$serverId")
            return cached.data
        }

        return refresh(serverId, cache, loader, label)
    }

    private suspend fun <T> refresh(
        serverId: String,
        cache: MutableMap<String, CachedEntry<T>>,
        loader: suspend () -> T,
        label: String,
    ): T {
        val fresh = loader()
        mutex.withLock {
            cache[serverId] = CachedEntry(
                data = fresh,
                timestampMs = System.currentTimeMillis(),
            )
        }
        Log.d(TAG, "refresh: updated $label cache for serverId=$serverId")
        return fresh
    }

    private fun isExpired(timestampMs: Long, ttlMs: Long): Boolean {
        return System.currentTimeMillis() - timestampMs > ttlMs
    }

    companion object {
        private const val TAG = "MetadataCache"
        private const val PROVIDERS_TTL_MS = 5 * 60 * 1000L
        private const val AGENTS_TTL_MS = 5 * 60 * 1000L
        private const val COMMANDS_TTL_MS = 5 * 60 * 1000L
    }
}
