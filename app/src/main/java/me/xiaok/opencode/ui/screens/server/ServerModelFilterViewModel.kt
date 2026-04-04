package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.Provider
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
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    private val _hiddenProviders = MutableStateFlow<Set<String>>(emptySet())
    private val _hiddenModels = MutableStateFlow<Set<String>>(emptySet())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ModelFilterUiState> = _providers
        .combine(_hiddenProviders) { providers, hiddenProviders ->
            ModelFilterPartialState(providers, hiddenProviders = hiddenProviders)
        }.combine(_hiddenModels) { partial, hiddenModels ->
            partial.copy(hiddenModels = hiddenModels)
        }.combine(_searchQuery) { partial, searchQuery ->
            ModelFilterUiState(
                providers = partial.providers,
                hiddenProviders = partial.hiddenProviders,
                hiddenModels = partial.hiddenModels,
                searchQuery = searchQuery,
            )
        }.combine(_isLoading) { state, isLoading ->
            state.copy(isLoading = isLoading)
        }.combine(_error) { state, error ->
            state.copy(error = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelFilterUiState())

    private data class ModelFilterPartialState(
        val providers: List<Provider>,
        val hiddenProviders: Set<String> = emptySet(),
        val hiddenModels: Set<String> = emptySet(),
    )

    init {
        loadProviders()
        loadHiddenModels()
        loadHiddenProviders()
    }

    private fun loadHiddenModels() {
        viewModelScope.launch {
            settingsRepository.getHiddenModels(serverId).collect { saved ->
                _hiddenModels.value = saved
            }
        }
    }

    private fun loadHiddenProviders() {
        viewModelScope.launch {
            settingsRepository.getHiddenProviders(serverId).collect { saved ->
                _hiddenProviders.value = saved
            }
        }
    }

    fun loadProviders() {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            if (server == null) {
                _error.value = "Server not found"
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            try {
                val providerList = api.getProviders(server)
                _providers.value = providerList.all.filter { it.id in providerList.connected.toSet() }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load providers"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleModelVisibility(modelId: String) {
        val updated = _hiddenModels.value.toMutableSet().apply {
            if (contains(modelId)) remove(modelId) else add(modelId)
        }
        _hiddenModels.value = updated
        viewModelScope.launch {
            settingsRepository.setHiddenModels(serverId, updated)
        }
    }

    fun toggleProviderVisibility(providerId: String) {
        val updated = _hiddenProviders.value.toMutableSet().apply {
            if (contains(providerId)) remove(providerId) else add(providerId)
        }
        _hiddenProviders.value = updated
        viewModelScope.launch {
            settingsRepository.setHiddenProviders(serverId, updated)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
