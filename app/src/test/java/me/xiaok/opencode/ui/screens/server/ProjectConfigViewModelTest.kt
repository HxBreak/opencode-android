package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.fixtures.TestFixtures
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectConfigViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private lateinit var api: OpenCodeApi
    private lateinit var serverRepository: ServerRepository
    private val testServer = TestFixtures.testServerConnection(id = "server_1")
    private lateinit var vm: ProjectConfigViewModel

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        api = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)

        every { serverRepository.getServer("server_1") } returns testServer
        coEvery { api.getProjectConfig(testServer) } returns JsonObject(
            mapOf("model" to JsonPrimitive("claude-3-sonnet"))
        )
    }

    @After
    fun teardown() { unmockkStatic(android.util.Log::class) }

    private fun createVm(): ProjectConfigViewModel {
        return ProjectConfigViewModel(
            SavedStateHandle(mapOf("serverId" to "server_1")),
            api,
            serverRepository,
        )
    }

    @Test
    fun `loadConfig loads config and sets configText`() {
        vm = createVm()
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.configText.contains("claude-3-sonnet"))
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadConfig sets error on API failure`() {
        coEvery { api.getProjectConfig(testServer) } throws RuntimeException("Network error")
        vm = createVm()
        testScope.advanceUntilIdle()
        assertEquals("Network error", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadConfig keeps loading when server not found`() {
        every { serverRepository.getServer("server_1") } returns null
        vm = createVm()
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
        assertEquals("", vm.uiState.value.configText)
    }

    @Test
    fun `updateConfigText updates configText`() {
        vm = createVm()
        testScope.advanceUntilIdle()
        vm.updateConfigText("new config text")
        assertEquals("new config text", vm.uiState.value.configText)
    }

    @Test
    fun `saveConfig saves config via API`() {
        val updatedConfig = JsonObject(mapOf("model" to JsonPrimitive("gpt-4")))
        coEvery { api.patchProjectConfig(testServer, any()) } returns updatedConfig

        vm = createVm()
        testScope.advanceUntilIdle()
        vm.updateConfigText("""{"model":"gpt-4"}""")
        vm.saveConfig()
        testScope.advanceUntilIdle()

        assertTrue(vm.uiState.value.configText.contains("gpt-4"))
    }

    @Test
    fun `saveConfig sets error on API failure`() {
        coEvery { api.patchProjectConfig(testServer, any()) } throws RuntimeException("Save failed")

        vm = createVm()
        testScope.advanceUntilIdle()
        vm.updateConfigText("""{"key":"value"}""")
        vm.saveConfig()
        testScope.advanceUntilIdle()

        assertEquals("Save failed", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `saveConfig sets saveSuccess on success`() {
        val updatedConfig = JsonObject(mapOf("model" to JsonPrimitive("gpt-4")))
        coEvery { api.patchProjectConfig(testServer, any()) } returns updatedConfig

        vm = createVm()
        testScope.advanceUntilIdle()
        vm.updateConfigText("""{"model":"gpt-4"}""")
        vm.saveConfig()
        testScope.advanceUntilIdle()

        assertTrue(vm.uiState.value.saveSuccess)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `clearError clears error`() {
        coEvery { api.getProjectConfig(testServer) } throws RuntimeException("boom")
        vm = createVm()
        testScope.advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.error)
        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `clearSaveSuccess clears flag`() {
        val updatedConfig = JsonObject(mapOf("model" to JsonPrimitive("gpt-4")))
        coEvery { api.patchProjectConfig(testServer, any()) } returns updatedConfig

        vm = createVm()
        testScope.advanceUntilIdle()
        vm.updateConfigText("""{"model":"gpt-4"}""")
        vm.saveConfig()
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.saveSuccess)

        vm.clearSaveSuccess()
        assertFalse(vm.uiState.value.saveSuccess)
    }
}
