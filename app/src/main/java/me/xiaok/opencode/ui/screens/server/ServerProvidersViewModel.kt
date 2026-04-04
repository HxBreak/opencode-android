package me.xiaok.opencode.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.ProviderList
import javax.inject.Inject

data class ProvidersUiState(
    val providers: ProviderList = ProviderList(),
    val authMethods: JsonElement? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val oauthUrl: String? = null,
    val oauthInstructions: String? = null,
)

@HiltViewModel
class ServerProvidersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val providers = api.getProviders(server)
                _uiState.update { it.copy(providers = providers, isLoading = false) }

                // Also load auth methods in parallel
                launch {
                    try {
                        val authMethods = api.getProviderAuthMethods(server)
                        _uiState.update { it.copy(authMethods = authMethods) }
                    } catch (_: Exception) {
                        // Auth methods are optional — don't fail the whole screen
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun connectWithApiKey(providerId: String, apiKey: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val credentials = JsonObject(mapOf("apiKey" to JsonPrimitive(apiKey)))
                api.setAuth(server, providerId, credentials)
                // Reload providers to reflect the new connected state
                loadProviders()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun disconnectProvider(providerId: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.removeAuth(server, providerId)
                loadProviders()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun startOAuth(providerId: String, methodIndex: Int) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val result = api.authorizeOAuth(server, providerId, methodIndex)
                val url = result.jsonObject["url"]?.jsonPrimitive?.content
                val instructions = result.jsonObject["instructions"]?.jsonPrimitive?.content
                _uiState.update {
                    it.copy(oauthUrl = url, oauthInstructions = instructions)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun completeOAuth(providerId: String, methodIndex: Int, code: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.completeOAuth(server, providerId, methodIndex, code)
                _uiState.update { it.copy(oauthUrl = null, oauthInstructions = null) }
                loadProviders()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearOAuthState() {
        _uiState.update { it.copy(oauthUrl = null, oauthInstructions = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
