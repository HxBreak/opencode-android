package me.xiaok.opencode.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.repository.CacheRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import javax.inject.Inject

data class SettingsUiState(
    val theme: String = "system",
    val reconnectMode: String = "normal",
    val chatFontSize: String = "medium",
    val initialMessages: Int = 50,
    val imageCompress: Boolean = true,
    val notificationsEnabled: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.theme,
        settingsRepository.reconnectMode,
        settingsRepository.chatFontSize,
        settingsRepository.initialMessages,
        settingsRepository.imageCompress,
        settingsRepository.notificationsEnabled,
    ) { values ->
        SettingsUiState(
            theme = values[0] as String,
            reconnectMode = values[1] as String,
            chatFontSize = values[2] as String,
            initialMessages = values[3] as Int,
            imageCompress = values[4] as Boolean,
            notificationsEnabled = values[5] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(value: String) {
        viewModelScope.launch { settingsRepository.setTheme(value) }
    }

    fun setReconnectMode(value: String) {
        viewModelScope.launch { settingsRepository.setReconnectMode(value) }
    }

    fun setChatFontSize(value: String) {
        viewModelScope.launch { settingsRepository.setChatFontSize(value) }
    }

    fun setInitialMessages(value: Int) {
        viewModelScope.launch { settingsRepository.setInitialMessages(value) }
    }

    fun setImageCompress(value: Boolean) {
        viewModelScope.launch { settingsRepository.setImageCompress(value) }
    }

    fun setNotificationsEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(value) }
    }

    fun clearCacheData() {
        viewModelScope.launch { cacheRepository.clearAllCacheData() }
    }
}
