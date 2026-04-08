package me.xiaok.opencode.ui.screens.settings

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.utils.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private val testScope get() = coroutineRule.testScope

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cacheRepository: CacheRepository
    private lateinit var vm: SettingsViewModel

    private val themeFlow = MutableStateFlow("system")
    private val reconnectModeFlow = MutableStateFlow("normal")
    private val chatFontSizeFlow = MutableStateFlow("medium")
    private val initialMessagesFlow = MutableStateFlow(50)
    private val imageCompressFlow = MutableStateFlow(true)
    private val notificationsEnabledFlow = MutableStateFlow(true)

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        settingsRepository = mockk(relaxed = true)
        cacheRepository = mockk(relaxed = true)

        every { settingsRepository.theme } returns themeFlow
        every { settingsRepository.reconnectMode } returns reconnectModeFlow
        every { settingsRepository.chatFontSize } returns chatFontSizeFlow
        every { settingsRepository.initialMessages } returns initialMessagesFlow
        every { settingsRepository.imageCompress } returns imageCompressFlow
        every { settingsRepository.notificationsEnabled } returns notificationsEnabledFlow

        vm = SettingsViewModel(settingsRepository, cacheRepository)
    }

    @After
    fun teardown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun collectAndAdvance() {
        testScope.backgroundScope.launch { vm.uiState.collect {} }
        testScope.advanceUntilIdle()
    }

    @Test
    fun `uiState initial state has defaults`() {
        collectAndAdvance()
        val state = vm.uiState.value
        assertEquals("system", state.theme)
        assertEquals("normal", state.reconnectMode)
        assertEquals("medium", state.chatFontSize)
        assertEquals(50, state.initialMessages)
        assertTrue(state.imageCompress)
        assertTrue(state.notificationsEnabled)
    }

    @Test
    fun `uiState reflects theme change`() {
        collectAndAdvance()
        themeFlow.value = "dark"
        testScope.advanceUntilIdle()
        assertEquals("dark", vm.uiState.value.theme)
    }

    @Test
    fun `uiState reflects reconnectMode change`() {
        collectAndAdvance()
        reconnectModeFlow.value = "aggressive"
        testScope.advanceUntilIdle()
        assertEquals("aggressive", vm.uiState.value.reconnectMode)
    }

    @Test
    fun `uiState reflects chatFontSize change`() {
        collectAndAdvance()
        chatFontSizeFlow.value = "large"
        testScope.advanceUntilIdle()
        assertEquals("large", vm.uiState.value.chatFontSize)
    }

    @Test
    fun `uiState reflects initialMessages change`() {
        collectAndAdvance()
        initialMessagesFlow.value = 100
        testScope.advanceUntilIdle()
        assertEquals(100, vm.uiState.value.initialMessages)
    }

    @Test
    fun `uiState reflects imageCompress change`() {
        collectAndAdvance()
        imageCompressFlow.value = false
        testScope.advanceUntilIdle()
        assertFalse(vm.uiState.value.imageCompress)
    }

    @Test
    fun `uiState reflects notificationsEnabled change`() {
        collectAndAdvance()
        notificationsEnabledFlow.value = false
        testScope.advanceUntilIdle()
        assertFalse(vm.uiState.value.notificationsEnabled)
    }

    @Test
    fun `setTheme delegates to repository`() {
        vm.setTheme("dark")
        testScope.advanceUntilIdle()
        coVerify { settingsRepository.setTheme("dark") }
    }

    @Test
    fun `setChatFontSize delegates to repository`() {
        vm.setChatFontSize("large")
        testScope.advanceUntilIdle()
        coVerify { settingsRepository.setChatFontSize("large") }
    }

    @Test
    fun `setInitialMessages delegates to repository`() {
        vm.setInitialMessages(100)
        testScope.advanceUntilIdle()
        coVerify { settingsRepository.setInitialMessages(100) }
    }
}
