package me.xiaok.opencode.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProviderList(
    val all: List<Provider> = emptyList(),
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList(),
)

@Serializable
data class Provider(
    val id: String = "",
    val name: String = "",
    val source: String = "",
    val env: List<String> = emptyList(),
    val models: Map<String, Model> = emptyMap(),
)

@Serializable
data class Model(
    val id: String = "",
    val name: String = "",
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val cost: ModelCost = ModelCost(),
    val limit: ModelLimits = ModelLimits(),
    val variants: Map<String, JsonElement> = emptyMap(),
) {
    /** Variant keys available for this model (e.g. "low", "medium", "high"). */
    val variantNames: List<String> get() = variants.keys.toList()
}

@Serializable
data class ModelCapabilities(
    val reasoning: Boolean = false,
    val toolcall: Boolean = false,
    val attachment: Boolean = false,
)

@Serializable
data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
)

@Serializable
data class ModelLimits(
    val context: Long = 0L,
    val output: Long = 0L,
)
