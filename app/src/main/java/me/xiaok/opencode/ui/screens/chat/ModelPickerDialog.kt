package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.domain.model.Provider

// ---------------------------------------------------------------------------
// Model Picker Dialog
// ---------------------------------------------------------------------------

@Composable
fun ModelPickerDialog(
    providers: List<Provider>,
    selectedModel: ModelRef?,
    onModelSelected: (ModelRef?) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    // Filter at model level: match model.id or model.name (case-insensitive).
    // Provider is kept if it has any matching models, and only matching models are shown.
    val filteredProviders = remember(searchQuery, providers) {
        if (searchQuery.isBlank()) {
            providers
        } else {
            val q = searchQuery.lowercase()
            providers.mapNotNull { provider ->
                val matchingModels = provider.models.filter { (modelId, model) ->
                    modelId.lowercase().contains(q) || model.name.lowercase().contains(q)
                }
                if (matchingModels.isEmpty()) null else provider.copy(models = matchingModels)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Model",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search models") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { /* Filter applied by state */ }
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredProviders.forEach { provider ->
                        item {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }

                        items(provider.models.toList()) { (modelId, model) ->
                            ModelPickerItem(
                                name = model.name,
                                subtitle = "$modelId • ${provider.name}",
                                isSelected = selectedModel?.providerID == provider.id &&
                                             selectedModel?.modelID == modelId,
                                onClick = {
                                    onModelSelected(ModelRef(providerID = provider.id, modelID = modelId))
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ModelPickerItem(
    name: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Revert Confirmation Dialog
// ---------------------------------------------------------------------------

@Composable
fun RevertConfirmationDialog(
    messagePreview: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = "Revert Session?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This will revert the session to the selected message. All messages after this point will be removed.",
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (messagePreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reverting to:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = messagePreview.take(100) + if (messagePreview.length > 100) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text("Revert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Rename Session Dialog
// ---------------------------------------------------------------------------

@Composable
fun RenameSessionDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTitle by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
            )
        },
        title = {
            Text(
                text = "Rename Session",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("Session title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newTitle.isNotBlank()) {
                        onConfirm(newTitle.trim())
                        onDismiss()
                    }
                },
                enabled = newTitle.isNotBlank(),
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
