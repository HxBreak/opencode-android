package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.Provider
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

data class ModelFilterUiState(
    val providers: List<Provider> = emptyList(),
    val hiddenProviders: Set<String> = emptySet(),
    val hiddenModels: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ServerModelFilterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val errorCollector: ErrorCollector,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val _uiState = MutableStateFlow(ModelFilterUiState())
    val uiState: StateFlow<ModelFilterUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
        loadHiddenModels()
        loadHiddenProviders()
    }

    private fun loadHiddenModels() {
        viewModelScope.launch {
            settingsRepository.getHiddenModels(serverId).collect { saved ->
                _uiState.value = _uiState.value.copy(hiddenModels = saved)
            }
        }
    }

    private fun loadHiddenProviders() {
        viewModelScope.launch {
            settingsRepository.getHiddenProviders(serverId).collect { saved ->
                _uiState.value = _uiState.value.copy(hiddenProviders = saved)
            }
        }
    }

    fun loadProviders() {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            if (server == null) {
                _uiState.value = _uiState.value.copy(error = "Server not found")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val providerList = api.getProviders(server)
                _uiState.value = _uiState.value.copy(
                    providers = providerList.all.filter { it.id in providerList.connected.toSet() }
                )
            } catch (e: Exception) {
                errorCollector.logError(e, "ServerModelFilter")
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to load providers")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Toggle visibility of a single model within a specific provider.
     * Keys are stored as "providerId/modelId" so models with the same ID
     * under different providers are tracked independently.
     */
    fun toggleModelVisibility(providerId: String, modelId: String) {
        val compositeKey = "$providerId/$modelId"
        val state = _uiState.updateAndGet { current ->
            val updated = current.hiddenModels.toMutableSet().apply {
                if (contains(compositeKey)) remove(compositeKey) else add(compositeKey)
            }
            current.copy(hiddenModels = updated)
        }
        viewModelScope.launch {
            settingsRepository.setHiddenModels(serverId, state.hiddenModels)
        }
    }

    /**
     * Toggle all models under a provider.
     * - If all models are hidden → show all (remove composite keys from hiddenModels)
     * - If all visible or partially visible → hide all (add composite keys to hiddenModels)
     * Pure batch helper — does NOT touch hiddenProviders.
     */
    fun toggleProviderVisibility(providerId: String) {
        val currentState = _uiState.value
        val provider = currentState.providers.find { it.id == providerId } ?: return

        val compositeKeys = provider.models.keys.map { modelId -> "$providerId/$modelId" }.toSet()
        val allHidden = compositeKeys.isNotEmpty() && compositeKeys.all { it in currentState.hiddenModels }

        val updatedModels = currentState.hiddenModels.toMutableSet().apply {
            if (allHidden) {
                removeAll(compositeKeys) // Show all
            } else {
                addAll(compositeKeys) // Hide all
            }
        }

        _uiState.value = currentState.copy(hiddenModels = updatedModels)

        viewModelScope.launch {
            settingsRepository.setHiddenModels(serverId, updatedModels)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
}
