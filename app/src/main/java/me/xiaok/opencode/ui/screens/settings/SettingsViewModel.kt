package me.xiaok.opencode.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.repository.SettingsRepository
import javax.inject.Inject

data class SettingsUiState(
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    val amoledDark: Boolean = false,
    val reconnectMode: String = "normal",
    val chatFontSize: String = "medium",
    val compactMessages: Boolean = false,
    val codeWordWrap: Boolean = true,
    val collapseTools: Boolean = false,
    val initialMessages: Int = 50,
    val confirmSend: Boolean = false,
    val hapticFeedback: Boolean = true,
    val imageCompress: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val imageMaxSide: Int = 2048,
    val imageWebPQuality: Int = 70,
    val terminalFontSize: Int = 12,
    val notificationsSilent: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.theme,
        settingsRepository.dynamicColor,
        settingsRepository.amoledDark,
        settingsRepository.reconnectMode,
        settingsRepository.chatFontSize,
        settingsRepository.compactMessages,
        settingsRepository.codeWordWrap,
        settingsRepository.collapseTools,
        settingsRepository.initialMessages,
        settingsRepository.confirmSend,
        settingsRepository.hapticFeedback,
        settingsRepository.imageCompress,
        settingsRepository.notificationsEnabled,
        settingsRepository.keepScreenOn,
        settingsRepository.imageMaxSide,
        settingsRepository.imageWebPQuality,
        settingsRepository.terminalFontSize,
        settingsRepository.notificationsSilent,
    ) { values ->
        SettingsUiState(
            theme = values[0] as String,
            dynamicColor = values[1] as Boolean,
            amoledDark = values[2] as Boolean,
            reconnectMode = values[3] as String,
            chatFontSize = values[4] as String,
            compactMessages = values[5] as Boolean,
            codeWordWrap = values[6] as Boolean,
            collapseTools = values[7] as Boolean,
            initialMessages = values[8] as Int,
            confirmSend = values[9] as Boolean,
            hapticFeedback = values[10] as Boolean,
            imageCompress = values[11] as Boolean,
            notificationsEnabled = values[12] as Boolean,
            keepScreenOn = values[13] as Boolean,
            imageMaxSide = values[14] as Int,
            imageWebPQuality = values[15] as Int,
            terminalFontSize = values[16] as Int,
            notificationsSilent = values[17] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(value: String) {
        viewModelScope.launch { settingsRepository.setTheme(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(value) }
    }

    fun setAmoledDark(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAmoledDark(value) }
    }

    fun setReconnectMode(value: String) {
        viewModelScope.launch { settingsRepository.setReconnectMode(value) }
    }

    fun setChatFontSize(value: String) {
        viewModelScope.launch { settingsRepository.setChatFontSize(value) }
    }

    fun setCompactMessages(value: Boolean) {
        viewModelScope.launch { settingsRepository.setCompactMessages(value) }
    }

    fun setCodeWordWrap(value: Boolean) {
        viewModelScope.launch { settingsRepository.setCodeWordWrap(value) }
    }

    fun setCollapseTools(value: Boolean) {
        viewModelScope.launch { settingsRepository.setCollapseTools(value) }
    }

    fun setInitialMessages(value: Int) {
        viewModelScope.launch { settingsRepository.setInitialMessages(value) }
    }

    fun setConfirmSend(value: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmSend(value) }
    }

    fun setHapticFeedback(value: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticFeedback(value) }
    }

    fun setImageCompress(value: Boolean) {
        viewModelScope.launch { settingsRepository.setImageCompress(value) }
    }

    fun setNotificationsEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(value) }
    }

    fun setImageMaxSide(value: Int) {
        viewModelScope.launch { settingsRepository.setImageMaxSide(value) }
    }

    fun setImageWebPQuality(value: Int) {
        viewModelScope.launch { settingsRepository.setImageWebPQuality(value) }
    }

    fun setTerminalFontSize(value: Int) {
        viewModelScope.launch { settingsRepository.setTerminalFontSize(value) }
    }

    fun setNotificationsSilent(value: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsSilent(value) }
    }
}
