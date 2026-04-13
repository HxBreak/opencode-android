package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.AgentConfig
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.domain.model.Provider

@Composable
internal fun SelectorRow(
    agents: List<AgentConfig>,
    selectedAgent: String?,
    onAgentSelected: (String?) -> Unit,
    providers: List<Provider>,
    selectedModel: ModelRef?,
    onModelSelected: (ModelRef?) -> Unit,
    variants: List<String>,
    selectedVariant: String?,
    onVariantSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val visibleAgents = remember(agents) {
            agents.filter { !it.hidden && it.mode != "subagent" }
        }
        SelectorChip(
            label = selectedAgent ?: visibleAgents.firstOrNull()?.name ?: "Agent",
            items = visibleAgents.map { it.name },
            selectedItem = selectedAgent,
            onSelect = onAgentSelected,
            modifier = Modifier.weight(1f),
        )

        val allModels = remember(providers) {
            providers.flatMap { provider ->
                provider.models.entries.map { (modelId, model) ->
                    Triple(provider.id, modelId, model.name.ifEmpty { modelId })
                }
            }
        }

        val modelLabel = selectedModel?.let { ref ->
            val provider = providers.find { it.id == ref.providerID }
            val modelEntry = allModels.find { it.first == ref.providerID && it.second == ref.modelID }
            if (provider != null && modelEntry != null) {
                "${modelEntry.third} · ${provider.name}"
            } else {
                "${ref.modelID} · ${ref.providerID}"
            }
        } ?: allModels.firstOrNull()?.let { entry ->
            val provider = providers.find { it.id == entry.first }
            if (provider != null) "${entry.third} · ${provider.name}" else entry.third
        } ?: "Model"

        var showModelPicker by remember { mutableStateOf(false) }

        ModelSelectorChip(
            label = modelLabel,
            selectedModel = selectedModel,
            onClick = { showModelPicker = true },
            modifier = Modifier.weight(1f),
        )

        if (showModelPicker) {
            ModelPickerDialog(
                providers = providers,
                selectedModel = selectedModel,
                onModelSelected = onModelSelected,
                onDismiss = { showModelPicker = false },
            )
        }

        if (variants.isNotEmpty()) {
            VariantChip(
                variants = variants,
                selectedVariant = selectedVariant,
                onVariantSelected = onVariantSelected,
            )
        }
    }
}

@Composable
private fun SelectorChip(
    label: String,
    items: List<String>,
    selectedItem: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    displayNames: List<String> = items,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (selectedItem != null) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selectedItem != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 220.dp),
        ) {
            items.forEachIndexed { index, item ->
                val displayName = displayNames.getOrElse(index) { item }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (item == selectedItem) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorChip(
    label: String,
    selectedModel: ModelRef?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selectedModel != null) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selectedModel != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantChip(
    variants: List<String>,
    selectedVariant: String?,
    onVariantSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayText = selectedVariant?.replaceFirstChar { it.uppercase() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selectedVariant != null) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clickable {
                    if (variants.isEmpty()) return@clickable
                    if (selectedVariant == null) {
                        onVariantSelected(variants.first())
                    } else {
                        val currentIndex = variants.indexOf(selectedVariant)
                        if (currentIndex >= 0 && currentIndex < variants.lastIndex) {
                            onVariantSelected(variants[currentIndex + 1])
                        } else {
                            onVariantSelected(null)
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (selectedVariant != null) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = displayText ?: "Variant",
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedVariant != null) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
