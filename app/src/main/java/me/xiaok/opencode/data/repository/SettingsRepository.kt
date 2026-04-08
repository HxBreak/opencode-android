package me.xiaok.opencode.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import me.xiaok.opencode.domain.model.ModelRef
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    // Theme: "system", "light", "dark"
    val theme: Flow<String> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[THEME] ?: "system" }
    suspend fun setTheme(value: String) = dataStore.edit { it[THEME] = value }

    // Reconnect mode: "aggressive", "normal", "conservative"
    val reconnectMode: Flow<String> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[RECONNECT_MODE] ?: "normal" }
    suspend fun setReconnectMode(value: String) = dataStore.edit { it[RECONNECT_MODE] = value }

    // Chat font size: "small", "medium", "large"
    val chatFontSize: Flow<String> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[CHAT_FONT_SIZE] ?: "medium" }
    suspend fun setChatFontSize(value: String) = dataStore.edit { it[CHAT_FONT_SIZE] = value }

    // Initial messages count (25-200)
    val initialMessages: Flow<Int> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[INITIAL_MESSAGES] ?: 50 }
    suspend fun setInitialMessages(value: Int) = dataStore.edit { it[INITIAL_MESSAGES] = value }

    // Image compress
    val imageCompress: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[IMAGE_COMPRESS] ?: true }
    suspend fun setImageCompress(value: Boolean) = dataStore.edit { it[IMAGE_COMPRESS] = value }

    // Notifications enabled
    val notificationsEnabled: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[NOTIFICATIONS_ENABLED] ?: true }
    suspend fun setNotificationsEnabled(value: Boolean) = dataStore.edit { it[NOTIFICATIONS_ENABLED] = value }

    // Collapsed directories in file browser (comma-separated)
    val collapsedDirectories: Flow<Set<String>> = dataStore.data.catch { emit(emptyPreferences()) }.map {
        it[COLLAPSED_DIRS]?.split(",")?.filter { s -> s.isNotEmpty() }?.toSet() ?: emptySet() 
    }
    suspend fun setCollapsedDirectories(value: Set<String>) = dataStore.edit { 
        it[COLLAPSED_DIRS] = value.joinToString(",") 
    }

    // Hidden models per server — stored as "serverId:modelId,serverId:modelId,..."
    fun getHiddenModels(serverId: String): Flow<Set<String>> = dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
        val raw = prefs[HIDDEN_MODELS] ?: ""
        raw.split(",")
            .filter { it.startsWith("$serverId:") }
            .map { it.removePrefix("$serverId:") }
            .filter { it.isNotEmpty() }
            .toSet()
    }
    suspend fun setHiddenModels(serverId: String, models: Set<String>) = dataStore.edit { prefs ->
        val raw = prefs[HIDDEN_MODELS] ?: ""
        val existing = raw.split(",").filter { it.isNotEmpty() && !it.startsWith("$serverId:") }
        val newEntries = models.map { "$serverId:$it" }
        prefs[HIDDEN_MODELS] = (existing + newEntries).joinToString(",")
    }

    // Hidden providers per server — stored as "serverId:providerId,serverId:providerId,..."
    fun getHiddenProviders(serverId: String): Flow<Set<String>> = dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
        val raw = prefs[HIDDEN_PROVIDERS] ?: ""
        raw.split(",")
            .filter { it.startsWith("$serverId:") }
            .map { it.removePrefix("$serverId:") }
            .filter { it.isNotEmpty() }
            .toSet()
    }
    suspend fun setHiddenProviders(serverId: String, providers: Set<String>) = dataStore.edit { prefs ->
        val raw = prefs[HIDDEN_PROVIDERS] ?: ""
        val existing = raw.split(",").filter { it.isNotEmpty() && !it.startsWith("$serverId:") }
        val newEntries = providers.map { "$serverId:$it" }
        prefs[HIDDEN_PROVIDERS] = (existing + newEntries).joinToString(",")
    }

    // Recent model selection per server — stored as "serverId:providerId/modelId"
    fun getRecentModel(serverId: String): Flow<ModelRef?> = dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
        val raw = prefs[RECENT_MODELS] ?: ""
        val entry = raw.split(",").find { it.startsWith("$serverId:") }
        val value = entry?.removePrefix("$serverId:") ?: return@map null
        val parts = value.split("/", limit = 2)
        if (parts.size == 2) ModelRef(providerID = parts[0], modelID = parts[1]) else null
    }
    suspend fun setRecentModel(serverId: String, model: ModelRef) = dataStore.edit { prefs ->
        val raw = prefs[RECENT_MODELS] ?: ""
        val existing = raw.split(",").filter { it.isNotEmpty() && !it.startsWith("$serverId:") }
        prefs[RECENT_MODELS] = (existing + "$serverId:${model.providerID}/${model.modelID}").joinToString(",")
    }

    // Recent agent selection per server — stored as "serverId:agentName"
    fun getRecentAgent(serverId: String): Flow<String?> = dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
        val raw = prefs[RECENT_AGENTS] ?: ""
        val entry = raw.split(",").find { it.startsWith("$serverId:") }
        entry?.removePrefix("$serverId:")
    }
    suspend fun setRecentAgent(serverId: String, agent: String) = dataStore.edit { prefs ->
        val raw = prefs[RECENT_AGENTS] ?: ""
        val existing = raw.split(",").filter { it.isNotEmpty() && !it.startsWith("$serverId:") }
        prefs[RECENT_AGENTS] = (existing + "$serverId:$agent").joinToString(",")
    }

    // Recent variant selection per server+model — stored as "serverId:providerId/modelId:variant"
    fun getRecentVariant(serverId: String, model: ModelRef): Flow<String?> = dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
        val raw = prefs[RECENT_VARIANTS] ?: ""
        val key = "$serverId:${model.providerID}/${model.modelID}"
        val entry = raw.split(",").find { it.startsWith("$key:") }
        entry?.removePrefix("$key:")
    }
    suspend fun setRecentVariant(serverId: String, model: ModelRef, variant: String) = dataStore.edit { prefs ->
        val raw = prefs[RECENT_VARIANTS] ?: ""
        val key = "$serverId:${model.providerID}/${model.modelID}"
        val existing = raw.split(",").filter { it.isNotEmpty() && !it.startsWith("$key:") }
        prefs[RECENT_VARIANTS] = (existing + "$key:$variant").joinToString(",")
    }
    suspend fun clearRecentVariant(serverId: String, model: ModelRef) = dataStore.edit { prefs ->
        val raw = prefs[RECENT_VARIANTS] ?: ""
        val key = "$serverId:${model.providerID}/${model.modelID}"
        val existing = raw.split(",").filter { it.isNotEmpty() && !it.startsWith("$key:") }
        prefs[RECENT_VARIANTS] = existing.joinToString(",")
    }

    // Local projects per server — stored as "serverId:directoryPath\nserverId:directoryPath\n..."
    // Each entry is a directory path added by the user through the directory browser.
    fun getLocalProjects(serverId: String): Flow<List<String>> = dataStore.data.catch { emit(emptyPreferences()) }.map { prefs ->
        val raw = prefs[LOCAL_PROJECTS] ?: ""
        raw.split("\n")
            .filter { it.startsWith("$serverId:") }
            .map { it.removePrefix("$serverId:") }
            .filter { it.isNotEmpty() }
    }
    suspend fun addLocalProject(serverId: String, directory: String) = dataStore.edit { prefs ->
        val raw = prefs[LOCAL_PROJECTS] ?: ""
        val entries = raw.split("\n").filter { it.isNotEmpty() }.toMutableList()
        val entry = "$serverId:$directory"
        if (entry !in entries) {
            entries.add(entry)
            prefs[LOCAL_PROJECTS] = entries.joinToString("\n")
        }
    }
    suspend fun removeLocalProject(serverId: String, directory: String) = dataStore.edit { prefs ->
        val raw = prefs[LOCAL_PROJECTS] ?: ""
        val entries = raw.split("\n").filter { it.isNotEmpty() && it != "$serverId:$directory" }
        prefs[LOCAL_PROJECTS] = entries.joinToString("\n")
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val RECONNECT_MODE = stringPreferencesKey("reconnect_mode")
        private val CHAT_FONT_SIZE = stringPreferencesKey("chat_font_size")
        private val INITIAL_MESSAGES = intPreferencesKey("initial_messages")
        private val IMAGE_COMPRESS = booleanPreferencesKey("image_compress")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val COLLAPSED_DIRS = stringPreferencesKey("collapsed_directories")
        private val HIDDEN_MODELS = stringPreferencesKey("hidden_models")
        private val HIDDEN_PROVIDERS = stringPreferencesKey("hidden_providers")
        private val RECENT_MODELS = stringPreferencesKey("recent_models")
        private val RECENT_AGENTS = stringPreferencesKey("recent_agents")
        private val RECENT_VARIANTS = stringPreferencesKey("recent_variants")
        private val LOCAL_PROJECTS = stringPreferencesKey("local_projects")
    }
}
