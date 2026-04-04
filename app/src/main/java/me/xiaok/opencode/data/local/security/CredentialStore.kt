package me.xiaok.opencode.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.xiaok.opencode.domain.model.ServerConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "opencode_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun saveServer(server: ServerConnection) = withContext(Dispatchers.IO) {
        val servers = loadServers().toMutableList()
        val existingIndex = servers.indexOfFirst { it.id == server.id }
        if (existingIndex >= 0) {
            servers[existingIndex] = server
        } else {
            servers.add(server)
        }
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(servers)).apply()
    }

    suspend fun deleteServer(serverId: String) = withContext(Dispatchers.IO) {
        val servers = loadServers().filter { it.id != serverId }
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(servers)).apply()
    }

    suspend fun loadServers(): List<ServerConnection> = withContext(Dispatchers.IO) {
        val jsonStr = prefs.getString(KEY_SERVERS, null) ?: return@withContext emptyList()
        try {
            json.decodeFromString<List<ServerConnection>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_SERVERS = "servers"
    }
}
