package me.xiaok.opencode.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.domain.model.ModelRef
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
class SettingsRepositoryTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var repository: SettingsRepository
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() = runTest {
        // Clear DataStore files to ensure test isolation
        val dataStoreDir = File(context.filesDir, "datastore/settings")
        if (dataStoreDir.exists()) {
            dataStoreDir.listFiles()?.forEach { it.delete() }
        }
        // Create a fresh repository (the delegate re-creates the DataStore)
        repository = SettingsRepository(context)
        // Clear all preferences via reflection to access the private dataStore
        clearDataStore()
    }

    private suspend fun clearDataStore() {
        val dataStoreField = SettingsRepository::class.java.getDeclaredField("dataStore")
        dataStoreField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val dataStore = dataStoreField.get(repository) as DataStore<Preferences>
        dataStore.edit { it.clear() }
    }

    // === Default value tests ===

    @Test
    fun `theme defaults to system`() = runTest {
        assertEquals("system", repository.theme.first())
    }

    @Test
    fun `reconnectMode defaults to normal`() = runTest {
        assertEquals("normal", repository.reconnectMode.first())
    }

    @Test
    fun `chatFontSize defaults to medium`() = runTest {
        assertEquals("medium", repository.chatFontSize.first())
    }

    @Test
    fun `initialMessages defaults to 50`() = runTest {
        assertEquals(50, repository.initialMessages.first())
    }

    @Test
    fun `imageCompress defaults to true`() = runTest {
        assertEquals(true, repository.imageCompress.first())
    }

    @Test
    fun `notificationsEnabled defaults to true`() = runTest {
        assertEquals(true, repository.notificationsEnabled.first())
    }

    @Test
    fun `collapsedDirectories defaults to empty set`() = runTest {
        assertEquals(emptySet<String>(), repository.collapsedDirectories.first())
    }

    // === Setter tests ===

    @Test
    fun `setTheme persists and reads back`() = runTest {
        repository.setTheme("dark")
        assertEquals("dark", repository.theme.first())
    }

    @Test
    fun `setReconnectMode persists and reads back`() = runTest {
        repository.setReconnectMode("aggressive")
        assertEquals("aggressive", repository.reconnectMode.first())
    }

    @Test
    fun `setChatFontSize persists and reads back`() = runTest {
        repository.setChatFontSize("large")
        assertEquals("large", repository.chatFontSize.first())
    }

    @Test
    fun `setInitialMessages persists and reads back`() = runTest {
        repository.setInitialMessages(100)
        assertEquals(100, repository.initialMessages.first())
    }

    @Test
    fun `setImageCompress persists and reads back`() = runTest {
        repository.setImageCompress(false)
        assertEquals(false, repository.imageCompress.first())
    }

    @Test
    fun `setNotificationsEnabled persists and reads back`() = runTest {
        repository.setNotificationsEnabled(false)
        assertEquals(false, repository.notificationsEnabled.first())
    }

    @Test
    fun `setCollapsedDirectories persists and reads back`() = runTest {
        val dirs = setOf("src/main", "src/test", "build")
        repository.setCollapsedDirectories(dirs)
        assertEquals(dirs, repository.collapsedDirectories.first())
    }

    @Test
    fun `setCollapsedDirectories with empty set clears value`() = runTest {
        repository.setCollapsedDirectories(setOf("a", "b"))
        repository.setCollapsedDirectories(emptySet())
        assertEquals(emptySet<String>(), repository.collapsedDirectories.first())
    }

    // === Per-server hidden models ===

    @Test
    fun `getHiddenModels returns empty set for new server`() = runTest {
        assertEquals(emptySet<String>(), repository.getHiddenModels("server1").first())
    }

    @Test
    fun `setHiddenModels persists and reads back for a server`() = runTest {
        val models = setOf("claude-3-sonnet", "gpt-4")
        repository.setHiddenModels("server1", models)
        assertEquals(models, repository.getHiddenModels("server1").first())
    }

    @Test
    fun `hidden models are isolated per server`() = runTest {
        val models1 = setOf("model-a")
        val models2 = setOf("model-b", "model-c")
        repository.setHiddenModels("server1", models1)
        repository.setHiddenModels("server2", models2)
        assertEquals(models1, repository.getHiddenModels("server1").first())
        assertEquals(models2, repository.getHiddenModels("server2").first())
    }

    @Test
    fun `setHiddenModels replaces existing models for same server`() = runTest {
        repository.setHiddenModels("server1", setOf("old-model"))
        repository.setHiddenModels("server1", setOf("new-model"))
        assertEquals(setOf("new-model"), repository.getHiddenModels("server1").first())
    }

    @Test
    fun `setHiddenModels with empty set clears models for server`() = runTest {
        repository.setHiddenModels("server1", setOf("model-a"))
        repository.setHiddenModels("server1", emptySet())
        assertEquals(emptySet<String>(), repository.getHiddenModels("server1").first())
    }

    // === Per-server hidden providers ===

    @Test
    fun `getHiddenProviders returns empty set for new server`() = runTest {
        assertEquals(emptySet<String>(), repository.getHiddenProviders("server1").first())
    }

    @Test
    fun `setHiddenProviders persists and reads back for a server`() = runTest {
        val providers = setOf("openai", "anthropic")
        repository.setHiddenProviders("server1", providers)
        assertEquals(providers, repository.getHiddenProviders("server1").first())
    }

    @Test
    fun `hidden providers are isolated per server`() = runTest {
        repository.setHiddenProviders("server1", setOf("provider-a"))
        repository.setHiddenProviders("server2", setOf("provider-b"))
        assertEquals(setOf("provider-a"), repository.getHiddenProviders("server1").first())
        assertEquals(setOf("provider-b"), repository.getHiddenProviders("server2").first())
    }

    // === Recent model ===

    @Test
    fun `getRecentModel returns null initially`() = runTest {
        assertNull(repository.getRecentModel("server1").first())
    }

    @Test
    fun `setRecentModel persists and reads back`() = runTest {
        val model = ModelRef(providerID = "anthropic", modelID = "claude-3-sonnet")
        repository.setRecentModel("server1", model)
        val result = repository.getRecentModel("server1").first()
        assertNotNull(result)
        assertEquals("anthropic", result!!.providerID)
        assertEquals("claude-3-sonnet", result.modelID)
    }

    @Test
    fun `recent models are isolated per server`() = runTest {
        val model1 = ModelRef(providerID = "anthropic", modelID = "claude-3-opus")
        val model2 = ModelRef(providerID = "openai", modelID = "gpt-4")
        repository.setRecentModel("server1", model1)
        repository.setRecentModel("server2", model2)
        val result1 = repository.getRecentModel("server1").first()
        val result2 = repository.getRecentModel("server2").first()
        assertEquals("claude-3-opus", result1!!.modelID)
        assertEquals("gpt-4", result2!!.modelID)
    }

    // === Recent agent ===

    @Test
    fun `getRecentAgent returns null initially`() = runTest {
        assertNull(repository.getRecentAgent("server1").first())
    }

    @Test
    fun `setRecentAgent persists and reads back`() = runTest {
        repository.setRecentAgent("server1", "code")
        assertEquals("code", repository.getRecentAgent("server1").first())
    }

    @Test
    fun `recent agents are isolated per server`() = runTest {
        repository.setRecentAgent("server1", "code")
        repository.setRecentAgent("server2", "explore")
        assertEquals("code", repository.getRecentAgent("server1").first())
        assertEquals("explore", repository.getRecentAgent("server2").first())
    }

    // === Overwrite behavior ===

    @Test
    fun `setting theme twice overwrites previous value`() = runTest {
        repository.setTheme("light")
        repository.setTheme("dark")
        assertEquals("dark", repository.theme.first())
    }

    @Test
    fun `setRecentModel overwrites previous model for same server`() = runTest {
        val model1 = ModelRef(providerID = "anthropic", modelID = "claude-3-sonnet")
        val model2 = ModelRef(providerID = "openai", modelID = "gpt-4o")
        repository.setRecentModel("server1", model1)
        repository.setRecentModel("server1", model2)
        val result = repository.getRecentModel("server1").first()
        assertEquals("gpt-4o", result!!.modelID)
    }
}
