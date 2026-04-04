package me.xiaok.opencode.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.xiaok.opencode.domain.model.ChatDraft
import javax.inject.Inject
import javax.inject.Singleton

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "drafts")

@Singleton
class DraftRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.draftDataStore
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get draft for a session with backward compatibility.
     * If stored value is not valid JSON (old text-only draft), return it as text only.
     */
    fun getDraft(sessionId: String): Flow<ChatDraft?> = dataStore.data.catch { emit(emptyPreferences()) }
        .map { prefs ->
            val jsonString = prefs[stringPreferencesKey("draft_$sessionId")]
            if (jsonString.isNullOrBlank()) {
                null
            } else {
                try {
                    json.decodeFromString<ChatDraft>(jsonString)
                } catch (e: Exception) {
                    // Backward compatibility: if it's not valid JSON, treat as old text-only draft
                    ChatDraft(text = jsonString)
                }
            }
        }

    suspend fun saveDraft(sessionId: String, draft: ChatDraft) {
        dataStore.edit {
            it[stringPreferencesKey("draft_$sessionId")] = json.encodeToString(draft)
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Saves text as a ChatDraft with only the text field.
     */
    suspend fun saveDraft(sessionId: String, text: String) {
        if (text.isBlank()) {
            clearDraft(sessionId)
        } else {
            saveDraft(sessionId, ChatDraft(text = text))
        }
    }

    suspend fun clearDraft(sessionId: String) {
        dataStore.edit { it.remove(stringPreferencesKey("draft_$sessionId")) }
    }

    suspend fun clearAllDrafts() {
        dataStore.edit { it.clear() }
    }
}
