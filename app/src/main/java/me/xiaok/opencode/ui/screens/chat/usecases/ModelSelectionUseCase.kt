package me.xiaok.opencode.ui.screens.chat.usecases

import android.util.Log
import me.xiaok.opencode.data.api.*
import me.xiaok.opencode.data.api.OpenCodeApi
import me.xiaok.opencode.data.repository.EventReducer
import me.xiaok.opencode.data.repository.MetadataCache
import me.xiaok.opencode.data.repository.ServerRepository
import me.xiaok.opencode.data.repository.SettingsRepository
import me.xiaok.opencode.domain.model.*
import me.xiaok.opencode.utils.ErrorCollector
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ModelSelectionUseCase @Inject constructor(
    private val api: OpenCodeApi,
    private val eventReducer: EventReducer,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val metadataCache: MetadataCache,
    private val errorCollector: ErrorCollector,
) {
    val rawProviders = MutableStateFlow<List<Provider>>(emptyList())
    val hiddenModels = MutableStateFlow<Set<String>>(emptySet())
    val hiddenProviders = MutableStateFlow<Set<String>>(emptySet())
    val providers = MutableStateFlow<List<Provider>>(emptyList())
    val agents = MutableStateFlow<List<AgentConfig>>(emptyList())
    val commands = MutableStateFlow<List<CommandInfo>>(emptyList())
    val selectedAgent = MutableStateFlow<String?>(null)
    val selectedModel = MutableStateFlow<ModelRef?>(null)
    val selectedVariant = MutableStateFlow<String?>(null)
    val providerDefaults = MutableStateFlow<Map<String, String>>(emptyMap())
    val configuredModel = MutableStateFlow<ModelRef?>(null)
    val savedModel = MutableStateFlow<ModelRef?>(null)
    val shareConfig = MutableStateFlow<String?>(null) // "manual" | "auto" | "disabled" | null (unknown)
    var modelDefaultsApplied = false

    suspend fun loadProviders(serverId: String, onError: (String) -> Unit = {}) {
        try {
            val server = serverRepository.getServer(serverId) ?: return
            val providerList = metadataCache.getProviders(serverId, server)
            val connected = providerList.connected.toSet()
            rawProviders.value = providerList.all.filter { it.id in connected }
            providerDefaults.value = providerList.default
            applyHiddenFilter()
            // Ensure model defaults are applied after providers are loaded.
            // This handles the race condition where loadConfiguredModel() may have
            // called tryApplyModelDefaults() before providers were available,
            // leaving selectedModel as null.
            tryApplyModelDefaults()
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            onError("Failed to load models: ${e.message}")
        }
    }

    fun applyHiddenFilter() {
        val hiddenProv = hiddenProviders.value
        val hiddenMod = hiddenModels.value
        val raw = rawProviders.value

        providers.value = raw
            .filter { it.id !in hiddenProv }
            .map { provider ->
                val filteredModels = provider.models.filterKeys { modelId ->
                    "${provider.id}/$modelId" !in hiddenMod
                }
                if (filteredModels.size == provider.models.size) provider else {
                    provider.copy(models = filteredModels)
                }
            }
            .filter { it.models.isNotEmpty() }

        val sel = selectedModel.value
        if (sel != null && !isValidModelRef(sel)) {
            selectedModel.value = null
            selectedVariant.value = null
            modelDefaultsApplied = false
            tryApplyModelDefaults()
        }
    }

    suspend fun loadAgents(serverId: String, onError: (String) -> Unit = {}) {
        try {
            val server = serverRepository.getServer(serverId) ?: return
            val agentList = metadataCache.getAgents(serverId, server)
            agents.value = agentList

            if (selectedAgent.value == null) {
                val savedAgent = settingsRepository.getRecentAgent(serverId).first()
                val visibleAgents = agentList.filter { !it.hidden && it.mode != "subagent" }
                if (savedAgent != null && visibleAgents.any { it.name == savedAgent }) {
                    selectedAgent.value = savedAgent
                } else {
                    selectedAgent.value = visibleAgents.firstOrNull()?.name
                }
            }
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            onError("Failed to load agents: ${e.message}")
        }
    }

    suspend fun loadCommands(serverId: String) {
        try {
            val server = serverRepository.getServer(serverId)
            if (server == null) {
                Log.e(TAG, "loadCommands: server not found for serverId=$serverId")
                return
            }
            val commandList = metadataCache.getCommands(serverId, server)
            Log.d(TAG, "loadCommands: loaded ${commandList.size} commands")
            commands.value = commandList
        } catch (e: Exception) {
            Log.e(TAG, "loadCommands: FAILED", e)
        }
    }

    suspend fun selectAgent(serverId: String, agent: String?) {
        selectedAgent.value = agent
        if (agent != null) {
            settingsRepository.setRecentAgent(serverId, agent)
        }
    }

    suspend fun selectModel(serverId: String, model: ModelRef?) {
        selectedModel.value = model
        modelDefaultsApplied = true
        if (model != null) {
            val modelVariants = providers.value
                .find { it.id == model.providerID }
                ?.models?.get(model.modelID)
                ?.variantNames ?: emptyList()
            val currentVariant = selectedVariant.value
            if (modelVariants.isNotEmpty()) {
                if (currentVariant == null || currentVariant !in modelVariants) {
                    val saved = settingsRepository.getRecentVariant(serverId, model).first()
                    if (saved != null && saved in modelVariants) {
                        selectedVariant.value = saved
                    } else {
                        selectedVariant.value = modelVariants[modelVariants.size / 2]
                    }
                }
            } else {
                selectedVariant.value = null
            }
            settingsRepository.setRecentModel(serverId, model)
        } else {
            selectedVariant.value = null
        }
    }

    suspend fun selectVariant(serverId: String, variant: String?) {
        selectedVariant.value = variant
        val model = selectedModel.value
        if (model != null) {
            if (variant != null) {
                settingsRepository.setRecentVariant(serverId, model, variant)
            } else {
                settingsRepository.clearRecentVariant(serverId, model)
            }
        }
    }

    suspend fun loadConfiguredModel(serverId: String, onError: (String) -> Unit = {}) {
        settingsRepository.getRecentModel(serverId).first()?.let { savedModel.value = it }
        try {
            val server = serverRepository.getServer(serverId) ?: return
            val configJson = api.getConfig(server)
            val modelStr = configJson.jsonObject["model"]?.jsonPrimitive?.content
            if (modelStr != null && modelStr.contains("/")) {
                val parts = modelStr.split("/", limit = 2)
                if (parts.size == 2) {
                    configuredModel.value = ModelRef(
                        providerID = parts[0],
                        modelID = parts[1],
                    )
                }
            }
            val shareStr = configJson.jsonObject["share"]?.jsonPrimitive?.content
            if (shareStr != null) {
                shareConfig.value = shareStr
            }
            tryApplyModelDefaults()
        } catch (e: Exception) {
            errorCollector.logError(e, "Chat")
            onError("Failed to load server config: ${e.message}")
        }
    }

    fun tryApplyModelDefaults() {
        if (modelDefaultsApplied) return
        if (selectedModel.value != null) {
            modelDefaultsApplied = true
            return
        }

        val modelToApply: ModelRef? = when {
            savedModel.value?.let { isValidModelRef(it) } == true -> savedModel.value
            configuredModel.value?.let { isValidModelRef(it) } == true -> configuredModel.value
            else -> {
                val defaults = providerDefaults.value
                if (defaults.isNotEmpty()) {
                    val provs = providers.value
                    defaults.entries.firstOrNull { entry ->
                        provs.any { it.id == entry.key && it.models.containsKey(entry.value) }
                    }?.let { ModelRef(providerID = it.key, modelID = it.value) }
                } else null
            }
        }

        if (modelToApply != null) {
            selectedModel.value = modelToApply
            modelDefaultsApplied = true
        }
    }

    fun isValidModelRef(ref: ModelRef): Boolean {
        if (ref.providerID.isBlank() || ref.modelID.isBlank()) return false
        return providers.value.any { it.id == ref.providerID && it.models.containsKey(ref.modelID) }
    }

    fun observeHiddenFilter(serverId: String, scope: CoroutineScope) {
        scope.launch {
            settingsRepository.getHiddenModels(serverId).collect { hidden ->
                hiddenModels.value = hidden
                applyHiddenFilter()
            }
        }
        scope.launch {
            settingsRepository.getHiddenProviders(serverId).collect { hidden ->
                hiddenProviders.value = hidden
                applyHiddenFilter()
            }
        }
    }

    companion object {
        private const val TAG = "ModelSelectionUseCase"
    }
}
