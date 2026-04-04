package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import javax.inject.Inject

data class ProjectConfigUiState(
    val configText: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
)

@HiltViewModel
class ProjectConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val json = Json { prettyPrint = true }

    private val _uiState = MutableStateFlow(ProjectConfigUiState())
    val uiState: StateFlow<ProjectConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val config = api.getProjectConfig(server)
                _uiState.update {
                    it.copy(
                        configText = json.encodeToString(JsonElement.serializer(), config),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateConfigText(text: String) {
        _uiState.update { it.copy(configText = text, saveSuccess = false) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val configElement = json.parseToJsonElement(_uiState.value.configText)
                val updated = api.patchProjectConfig(server, configElement)
                _uiState.update {
                    it.copy(
                        configText = json.encodeToString(JsonElement.serializer(), updated),
                        isSaving = false,
                        saveSuccess = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
