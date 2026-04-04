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
    private lateinit var vm: SettingsViewModel

    private val themeFlow = MutableStateFlow("system")
    private val dynamicColorFlow = MutableStateFlow(true)
    private val amoledDarkFlow = MutableStateFlow(false)
    private val reconnectModeFlow = MutableStateFlow("normal")
    private val chatFontSizeFlow = MutableStateFlow("medium")
    private val compactMessagesFlow = MutableStateFlow(false)
    private val codeWordWrapFlow = MutableStateFlow(true)
    private val collapseToolsFlow = MutableStateFlow(false)
    private val initialMessagesFlow = MutableStateFlow(50)
    private val confirmSendFlow = MutableStateFlow(false)
    private val hapticFeedbackFlow = MutableStateFlow(true)
    private val imageCompressFlow = MutableStateFlow(true)
    private val notificationsEnabledFlow = MutableStateFlow(true)
    private val keepScreenOnFlow = MutableStateFlow(false)
    private val imageMaxSideFlow = MutableStateFlow(2048)
    private val imageWebPQualityFlow = MutableStateFlow(70)
    private val terminalFontSizeFlow = MutableStateFlow(12)
    private val notificationsSilentFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        settingsRepository = mockk(relaxed = true)

        every { settingsRepository.theme } returns themeFlow
        every { settingsRepository.dynamicColor } returns dynamicColorFlow
        every { settingsRepository.amoledDark } returns amoledDarkFlow
        every { settingsRepository.reconnectMode } returns reconnectModeFlow
        every { settingsRepository.chatFontSize } returns chatFontSizeFlow
        every { settingsRepository.compactMessages } returns compactMessagesFlow
        every { settingsRepository.codeWordWrap } returns codeWordWrapFlow
        every { settingsRepository.collapseTools } returns collapseToolsFlow
        every { settingsRepository.initialMessages } returns initialMessagesFlow
        every { settingsRepository.confirmSend } returns confirmSendFlow
        every { settingsRepository.hapticFeedback } returns hapticFeedbackFlow
        every { settingsRepository.imageCompress } returns imageCompressFlow
        every { settingsRepository.notificationsEnabled } returns notificationsEnabledFlow
        every { settingsRepository.keepScreenOn } returns keepScreenOnFlow
        every { settingsRepository.imageMaxSide } returns imageMaxSideFlow
        every { settingsRepository.imageWebPQuality } returns imageWebPQualityFlow
        every { settingsRepository.terminalFontSize } returns terminalFontSizeFlow
        every { settingsRepository.notificationsSilent } returns notificationsSilentFlow

        vm = SettingsViewModel(settingsRepository)
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
        assertTrue(state.dynamicColor)
        assertFalse(state.amoledDark)
        assertEquals("normal", state.reconnectMode)
        assertEquals("medium", state.chatFontSize)
        assertFalse(state.compactMessages)
        assertTrue(state.codeWordWrap)
        assertFalse(state.collapseTools)
        assertEquals(50, state.initialMessages)
        assertFalse(state.confirmSend)
        assertTrue(state.hapticFeedback)
        assertTrue(state.imageCompress)
        assertTrue(state.notificationsEnabled)
        assertFalse(state.keepScreenOn)
        assertEquals(2048, state.imageMaxSide)
        assertEquals(70, state.imageWebPQuality)
        assertEquals(12, state.terminalFontSize)
        assertFalse(state.notificationsSilent)
    }

    @Test
    fun `uiState reflects theme change`() {
        collectAndAdvance()
        themeFlow.value = "dark"
        testScope.advanceUntilIdle()
        assertEquals("dark", vm.uiState.value.theme)
    }

    @Test
    fun `uiState reflects dynamicColor change`() {
        collectAndAdvance()
        dynamicColorFlow.value = false
        testScope.advanceUntilIdle()
        assertFalse(vm.uiState.value.dynamicColor)
    }

    @Test
    fun `uiState reflects amoledDark change`() {
        collectAndAdvance()
        amoledDarkFlow.value = true
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.amoledDark)
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
    fun `uiState reflects compactMessages change`() {
        collectAndAdvance()
        compactMessagesFlow.value = true
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.compactMessages)
    }

    @Test
    fun `uiState reflects codeWordWrap change`() {
        collectAndAdvance()
        codeWordWrapFlow.value = false
        testScope.advanceUntilIdle()
        assertFalse(vm.uiState.value.codeWordWrap)
    }

    @Test
    fun `uiState reflects collapseTools change`() {
        collectAndAdvance()
        collapseToolsFlow.value = true
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.collapseTools)
    }

    @Test
    fun `uiState reflects initialMessages change`() {
        collectAndAdvance()
        initialMessagesFlow.value = 100
        testScope.advanceUntilIdle()
        assertEquals(100, vm.uiState.value.initialMessages)
    }

    @Test
    fun `uiState reflects confirmSend change`() {
        collectAndAdvance()
        confirmSendFlow.value = true
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmSend)
    }

    @Test
    fun `uiState reflects hapticFeedback change`() {
        collectAndAdvance()
        hapticFeedbackFlow.value = false
        testScope.advanceUntilIdle()
        assertFalse(vm.uiState.value.hapticFeedback)
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
    fun `uiState reflects keepScreenOn change`() {
        collectAndAdvance()
        keepScreenOnFlow.value = true
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.keepScreenOn)
    }

    @Test
    fun `uiState reflects imageMaxSide change`() {
        collectAndAdvance()
        imageMaxSideFlow.value = 1024
        testScope.advanceUntilIdle()
        assertEquals(1024, vm.uiState.value.imageMaxSide)
    }

    @Test
    fun `uiState reflects imageWebPQuality change`() {
        collectAndAdvance()
        imageWebPQualityFlow.value = 85
        testScope.advanceUntilIdle()
        assertEquals(85, vm.uiState.value.imageWebPQuality)
    }

    @Test
    fun `uiState reflects terminalFontSize change`() {
        collectAndAdvance()
        terminalFontSizeFlow.value = 16
        testScope.advanceUntilIdle()
        assertEquals(16, vm.uiState.value.terminalFontSize)
    }

    @Test
    fun `uiState reflects notificationsSilent change`() {
        collectAndAdvance()
        notificationsSilentFlow.value = true
        testScope.advanceUntilIdle()
        assertTrue(vm.uiState.value.notificationsSilent)
    }

    @Test
    fun `setTheme delegates to repository`() {
        vm.setTheme("dark")
        testScope.advanceUntilIdle()
        coVerify { settingsRepository.setTheme("dark") }
    }

    @Test
    fun `setDynamicColor delegates to repository`() {
        vm.setDynamicColor(false)
        testScope.advanceUntilIdle()
        coVerify { settingsRepository.setDynamicColor(false) }
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

    @Test
    fun `setTerminalFontSize delegates to repository`() {
        vm.setTerminalFontSize(16)
        testScope.advanceUntilIdle()
        coVerify { settingsRepository.setTerminalFontSize(16) }
    }

    @Test
    fun `setNotificationsSilent delegates to repository`() {
        vm.setNotificationsSilent(true)
        testScope.advanceUntilIdle()
        coVerify { settingsRepository.setNotificationsSilent(true) }
    }
}
