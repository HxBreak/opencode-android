package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.xiaok.opencode.data.repository.ServerRepository
import javax.inject.Inject

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    val serverName: StateFlow<String> = serverRepository.servers.map { servers ->
        servers.find { it.id == serverId }?.name ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
