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

    // Dynamic color
    val dynamicColor: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[DYNAMIC_COLOR] ?: true }
    suspend fun setDynamicColor(value: Boolean) = dataStore.edit { it[DYNAMIC_COLOR] = value }

    // AMOLED dark
    val amoledDark: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[AMOLED_DARK] ?: false }
    suspend fun setAmoledDark(value: Boolean) = dataStore.edit { it[AMOLED_DARK] = value }

    // Reconnect mode: "aggressive", "normal", "conservative"
    val reconnectMode: Flow<String> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[RECONNECT_MODE] ?: "normal" }
    suspend fun setReconnectMode(value: String) = dataStore.edit { it[RECONNECT_MODE] = value }

    // Chat font size: "small", "medium", "large"
    val chatFontSize: Flow<String> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[CHAT_FONT_SIZE] ?: "medium" }
    suspend fun setChatFontSize(value: String) = dataStore.edit { it[CHAT_FONT_SIZE] = value }

    // Compact messages
    val compactMessages: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[COMPACT_MESSAGES] ?: false }
    suspend fun setCompactMessages(value: Boolean) = dataStore.edit { it[COMPACT_MESSAGES] = value }

    // Code word wrap
    val codeWordWrap: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[CODE_WORD_WRAP] ?: true }
    suspend fun setCodeWordWrap(value: Boolean) = dataStore.edit { it[CODE_WORD_WRAP] = value }

    // Collapse tools
    val collapseTools: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[COLLAPSE_TOOLS] ?: false }
    suspend fun setCollapseTools(value: Boolean) = dataStore.edit { it[COLLAPSE_TOOLS] = value }

    // Initial messages count (25-200)
    val initialMessages: Flow<Int> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[INITIAL_MESSAGES] ?: 50 }
    suspend fun setInitialMessages(value: Int) = dataStore.edit { it[INITIAL_MESSAGES] = value }

    // Confirm send
    val confirmSend: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[CONFIRM_SEND] ?: false }
    suspend fun setConfirmSend(value: Boolean) = dataStore.edit { it[CONFIRM_SEND] = value }

    // Haptic feedback
    val hapticFeedback: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[HAPTIC_FEEDBACK] ?: true }
    suspend fun setHapticFeedback(value: Boolean) = dataStore.edit { it[HAPTIC_FEEDBACK] = value }

    // Image compress
    val imageCompress: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[IMAGE_COMPRESS] ?: true }
    suspend fun setImageCompress(value: Boolean) = dataStore.edit { it[IMAGE_COMPRESS] = value }

    // Keep screen on
    val keepScreenOn: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[KEEP_SCREEN_ON] ?: false }
    suspend fun setKeepScreenOn(value: Boolean) = dataStore.edit { it[KEEP_SCREEN_ON] = value }

    // Image max side
    val imageMaxSide: Flow<Int> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[IMAGE_MAX_SIDE] ?: 2048 }
    suspend fun setImageMaxSide(value: Int) = dataStore.edit { it[IMAGE_MAX_SIDE] = value }

    // Image WebP quality
    val imageWebPQuality: Flow<Int> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[IMAGE_WEBP_QUALITY] ?: 70 }
    suspend fun setImageWebPQuality(value: Int) = dataStore.edit { it[IMAGE_WEBP_QUALITY] = value }

    // Terminal font size
    val terminalFontSize: Flow<Int> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[TERMINAL_FONT_SIZE] ?: 12 }
    suspend fun setTerminalFontSize(value: Int) = dataStore.edit { it[TERMINAL_FONT_SIZE] = value }

    // Notifications silent mode
    val notificationsSilent: Flow<Boolean> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[NOTIFICATIONS_SILENT] ?: false }
    suspend fun setNotificationsSilent(value: Boolean) = dataStore.edit { it[NOTIFICATIONS_SILENT] = value }

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

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        private val RECONNECT_MODE = stringPreferencesKey("reconnect_mode")
        private val CHAT_FONT_SIZE = stringPreferencesKey("chat_font_size")
        private val COMPACT_MESSAGES = booleanPreferencesKey("compact_messages")
        private val CODE_WORD_WRAP = booleanPreferencesKey("code_word_wrap")
        private val COLLAPSE_TOOLS = booleanPreferencesKey("collapse_tools")
        private val INITIAL_MESSAGES = intPreferencesKey("initial_messages")
        private val CONFIRM_SEND = booleanPreferencesKey("confirm_send")
        private val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val IMAGE_COMPRESS = booleanPreferencesKey("image_compress")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val COLLAPSED_DIRS = stringPreferencesKey("collapsed_directories")
        private val HIDDEN_MODELS = stringPreferencesKey("hidden_models")
        private val HIDDEN_PROVIDERS = stringPreferencesKey("hidden_providers")
        private val RECENT_MODELS = stringPreferencesKey("recent_models")
        private val RECENT_AGENTS = stringPreferencesKey("recent_agents")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val IMAGE_MAX_SIDE = intPreferencesKey("image_max_side")
        private val IMAGE_WEBP_QUALITY = intPreferencesKey("image_webp_quality")
        private val TERMINAL_FONT_SIZE = intPreferencesKey("terminal_font_size")
        private val NOTIFICATIONS_SILENT = booleanPreferencesKey("notifications_silent")
    }
}
