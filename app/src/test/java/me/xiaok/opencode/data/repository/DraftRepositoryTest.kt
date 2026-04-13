package me.xiaok.opencode.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.domain.model.ChatDraft
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DraftRepositoryTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var repository: DraftRepository
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() = runTest {
        // Clear DataStore files to ensure test isolation
        val dataStoreDir = File(context.filesDir, "datastore/drafts")
        if (dataStoreDir.exists()) {
            dataStoreDir.listFiles()?.forEach { it.delete() }
        }
        repository = DraftRepository(context)
        // Clear all drafts to start fresh
        repository.clearAllDrafts()
    }

    // === getDraft returns null for new session ===

    @Test
    fun `getDraft returns null for new session`() = runTest {
        assertNull(repository.getDraft("session_new").first())
    }

    @Test
    fun `getDraft returns null for empty sessionId`() = runTest {
        assertNull(repository.getDraft("").first())
    }

    // === saveDraft with ChatDraft ===

    @Test
    fun `saveDraft with ChatDraft persists and reads back`() = runTest {
        val draft = TestFixtures.testChatDraft()
        repository.saveDraft("session1", draft)
        val result = repository.getDraft("session1").first()
        assertNotNull(result)
        assertEquals("Help me fix this bug", result!!.text)
        assertEquals("code", result.selectedAgent)
        assertEquals("anthropic", result.selectedModel!!.providerID)
        assertEquals("claude-3-sonnet", result.selectedModel.modelID)
        assertEquals("default", result.selectedVariant)
    }

    @Test
    fun `saveDraft with minimal ChatDraft persists text only`() = runTest {
        val draft = ChatDraft(text = "hello")
        repository.saveDraft("session1", draft)
        val result = repository.getDraft("session1").first()
        assertNotNull(result)
        assertEquals("hello", result!!.text)
        assertNull(result.selectedAgent)
        assertNull(result.selectedModel)
        assertNull(result.selectedVariant)
        assertEquals(emptyList<String>(), result.imageUris)
    }

    @Test
    fun `saveDraft with full ChatDraft preserves all fields`() = runTest {
        val draft = ChatDraft(
            text = "fix the bug",
            selectedAgent = "explore",
            selectedModel = ModelRef(providerID = "openai", modelID = "gpt-4"),
            selectedVariant = "fast",
            imageUris = listOf("content://image1", "content://image2"),
            timestamp = 1712000000000L
        )
        repository.saveDraft("session_full", draft)
        val result = repository.getDraft("session_full").first()
        assertNotNull(result)
        assertEquals("fix the bug", result!!.text)
        assertEquals("explore", result.selectedAgent)
        assertEquals("openai", result.selectedModel!!.providerID)
        assertEquals("gpt-4", result.selectedModel.modelID)
        assertEquals("fast", result.selectedVariant)
        assertEquals(listOf("content://image1", "content://image2"), result.imageUris)
    }

    @Test
    fun `drafts are isolated per session`() = runTest {
        val draft1 = ChatDraft(text = "session 1 draft")
        val draft2 = ChatDraft(text = "session 2 draft")
        repository.saveDraft("session1", draft1)
        repository.saveDraft("session2", draft2)
        assertEquals("session 1 draft", repository.getDraft("session1").first()!!.text)
        assertEquals("session 2 draft", repository.getDraft("session2").first()!!.text)
    }

    // === saveDraft with legacy String ===

    @Test
    fun `saveDraft with legacy String saves as ChatDraft`() = runTest {
        repository.saveDraft("session_legacy", "legacy text")
        val result = repository.getDraft("session_legacy").first()
        assertNotNull(result)
        assertEquals("legacy text", result!!.text)
        assertNull(result.selectedAgent)
        assertNull(result.selectedModel)
    }

    @Test
    fun `saveDraft with blank legacy String clears draft`() = runTest {
        repository.saveDraft("session_blank", ChatDraft(text = "existing"))
        repository.saveDraft("session_blank", "")
        assertNull(repository.getDraft("session_blank").first())
    }

    @Test
    fun `saveDraft with whitespace-only legacy String clears draft`() = runTest {
        repository.saveDraft("session_ws", ChatDraft(text = "existing"))
        repository.saveDraft("session_ws", "   ")
        assertNull(repository.getDraft("session_ws").first())
    }

    // === Backward compatibility: plain text stored directly ===

    @Test
    fun `getDraft returns ChatDraft with text when stored value is plain text`() = runTest {
        // Simulate old-style storage: write plain text directly to DataStore
        writePlainText("draft_session_old", "old plain text")
        val result = repository.getDraft("session_old").first()
        assertNotNull(result)
        assertEquals("old plain text", result!!.text)
        assertNull(result.selectedAgent)
        assertNull(result.selectedModel)
    }

    // === clearDraft ===

    @Test
    fun `clearDraft removes single draft`() = runTest {
        repository.saveDraft("session1", ChatDraft(text = "keep"))
        repository.saveDraft("session2", ChatDraft(text = "remove"))
        repository.clearDraft("session2")
        assertEquals("keep", repository.getDraft("session1").first()!!.text)
        assertNull(repository.getDraft("session2").first())
    }

    @Test
    fun `clearDraft on non-existent session is no-op`() = runTest {
        // Should not throw
        repository.clearDraft("non_existent")
        assertNull(repository.getDraft("non_existent").first())
    }

    // === clearAllDrafts ===

    @Test
    fun `clearAllDrafts removes all drafts`() = runTest {
        repository.saveDraft("s1", ChatDraft(text = "draft1"))
        repository.saveDraft("s2", ChatDraft(text = "draft2"))
        repository.saveDraft("s3", ChatDraft(text = "draft3"))
        repository.clearAllDrafts()
        assertNull(repository.getDraft("s1").first())
        assertNull(repository.getDraft("s2").first())
        assertNull(repository.getDraft("s3").first())
    }

    @Test
    fun `clearAllDrafts when no drafts is no-op`() = runTest {
        // Should not throw
        repository.clearAllDrafts()
        assertNull(repository.getDraft("any").first())
    }

    // === Overwrite behavior ===

    @Test
    fun `saveDraft overwrites existing draft for same session`() = runTest {
        repository.saveDraft("session1", ChatDraft(text = "first"))
        repository.saveDraft("session1", ChatDraft(text = "second"))
        assertEquals("second", repository.getDraft("session1").first()!!.text)
    }

    @Test
    fun `saveDraft legacy String overwrites ChatDraft`() = runTest {
        repository.saveDraft("session1", ChatDraft(text = "structured", selectedAgent = "code"))
        repository.saveDraft("session1", "legacy override")
        val result = repository.getDraft("session1").first()
        assertEquals("legacy override", result!!.text)
    }

    // === Helper to write plain text directly to DataStore ===

    private suspend fun writePlainText(keySuffix: String, value: String) {
        val dataStoreField = DraftRepository::class.java.getDeclaredField("dataStore")
        dataStoreField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val dataStore = dataStoreField.get(repository) as DataStore<Preferences>
        dataStore.edit { it[stringPreferencesKey(keySuffix)] = value }
    }
}
