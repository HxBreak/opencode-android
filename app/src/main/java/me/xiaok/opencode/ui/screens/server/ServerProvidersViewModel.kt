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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.domain.model.ProviderList
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject

data class ProvidersUiState(
    val providers: ProviderList = ProviderList(),
    val authMethods: JsonElement? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val oauthUrl: String? = null,
    val oauthInstructions: String? = null,
    val searchQuery: String = "",
)

@HiltViewModel
class ServerProvidersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val serverRepository: ServerRepository,
    private val errorCollector: ErrorCollector,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"]
        ?: throw IllegalArgumentException("serverId is required")

    private val _providers = MutableStateFlow(ProviderList())
    private val _authMethods = MutableStateFlow<JsonElement?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _oauthUrl = MutableStateFlow<String?>(null)
    private val _oauthInstructions = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ProvidersUiState> = _providers
        .combine(_authMethods) { providers, authMethods ->
            ProvidersPartialState(providers, authMethods = authMethods)
        }.combine(_isLoading) { partial, isLoading ->
            partial.copy(isLoading = isLoading)
        }.combine(_error) { partial, error ->
            partial.copy(error = error)
        }.combine(_oauthUrl) { partial, oauthUrl ->
            partial.copy(oauthUrl = oauthUrl)
        }.combine(_oauthInstructions) { partial, oauthInstructions ->
            partial.copy(oauthInstructions = oauthInstructions)
        }.combine(_searchQuery) { partial, searchQuery ->
            ProvidersUiState(
                providers = partial.providers,
                authMethods = partial.authMethods,
                isLoading = partial.isLoading,
                error = partial.error,
                oauthUrl = partial.oauthUrl,
                oauthInstructions = partial.oauthInstructions,
                searchQuery = searchQuery,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProvidersUiState())

    private data class ProvidersPartialState(
        val providers: ProviderList = ProviderList(),
        val authMethods: JsonElement? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val oauthUrl: String? = null,
        val oauthInstructions: String? = null,
    )

    init {
        loadProviders()
    }

    fun loadProviders() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                val providers = api.getProviders(server)
                _providers.value = providers
                _isLoading.value = false

                // Also load auth methods in parallel
                launch {
                    try {
                        val authMethods = api.getProviderAuthMethods(server)
                        _authMethods.value = authMethods
                    } catch (_: Exception) {
                        // Auth methods are optional — don't fail the whole screen
                    }
                }
            } catch (e: Exception) {
                errorCollector.logError(e, "ServerProviders")
                _isLoading.value = false
                _error.value = e.message
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
                errorCollector.logError(e, "ServerProviders")
                _error.value = e.message
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
                errorCollector.logError(e, "ServerProviders")
                _error.value = e.message
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
                _oauthUrl.value = url
                _oauthInstructions.value = instructions
            } catch (e: Exception) {
                errorCollector.logError(e, "ServerProviders")
                _error.value = e.message
            }
        }
    }

    fun completeOAuth(providerId: String, methodIndex: Int, code: String) {
        viewModelScope.launch {
            try {
                val server = serverRepository.getServer(serverId) ?: return@launch
                api.completeOAuth(server, providerId, methodIndex, code)
                _oauthUrl.value = null
                _oauthInstructions.value = null
                loadProviders()
            } catch (e: Exception) {
                errorCollector.logError(e, "ServerProviders")
                _error.value = e.message
            }
        }
    }

    fun clearOAuthState() {
        _oauthUrl.value = null
        _oauthInstructions.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
