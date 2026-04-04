package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.Model
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.domain.model.PermissionRequest
import me.xiaok.opencode.domain.model.Provider
import me.xiaok.opencode.domain.model.QuestionRequest

// ---------------------------------------------------------------------------
// Permission Dialog
// ---------------------------------------------------------------------------

@Composable
fun PermissionDialog(
    request: PermissionRequest,
    onReply: (permissionId: String, reply: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Permission Request",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = request.permission.ifEmpty { "Allow tool execution?" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (request.patterns.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = request.patterns.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onReply(request.id, "reject")
                    onDismiss()
                }) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
                FilledTonalButton(onClick = {
                    onReply(request.id, "once")
                    onDismiss()
                }) {
                    Text("Allow Once")
                }
                Button(onClick = {
                    onReply(request.id, "always")
                    onDismiss()
                }) {
                    Text("Always")
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Question Card — inline card rendered at bottom of message list
// (follows OC Remote's pattern: options directly selectable, no dialog popup)
// ---------------------------------------------------------------------------

/**
 * Inline question card displayed at the bottom of the chat message list.
 *
 * - Single-select: tapping an option immediately submits the answer.
 * - Multi-select: checkboxes + Submit button.
 * - Custom answer: inline text field with send button.
 */
@Composable
fun QuestionCard(
    question: QuestionRequest,
    onSubmit: (answers: List<List<String>>) -> Unit,
    onReject: () -> Unit,
    isSubmitting: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isSingle = question.questions.size == 1 && !question.questions.first().multiple

    // Locked = locally submitted OR server-side submitting (waiting for SSE confirmation)
    var localSubmitted by remember { mutableStateOf(false) }
    val isLocked = localSubmitted || isSubmitting

    // Track answers per question (index → selected labels)
    val answersPerQuestion = remember {
        mutableStateListOf<List<String>>().apply {
            repeat(question.questions.size) { add(emptyList()) }
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor,
                )
                Text(
                    text = "Question",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Question sections
            question.questions.forEachIndexed { index, q ->
                if (q.header.isNotEmpty()) {
                    Text(
                        text = q.header,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = q.question,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )

                Spacer(Modifier.height(2.dp))

                if (q.multiple) {
                    // ── Multi-select: checkboxes ──
                    val selectedLabels = remember { mutableStateListOf<String>() }
                    q.options.forEach { option ->
                        val checked = option.label in selectedLabels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (checked) accentColor.copy(alpha = 0.12f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                .toggleable(
                                    value = checked,
                                    enabled = !isLocked,
                                    role = androidx.compose.ui.semantics.Role.Checkbox,
                                    onValueChange = {
                                        if (it) selectedLabels.add(option.label) else selectedLabels.remove(option.label)
                                        if (index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = selectedLabels.toList()
                                        }
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = accentColor,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                ),
                            )
                            Column {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (option.description.isNotEmpty()) {
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Single-select: tappable option rows ──
                    q.options.forEach { option ->
                        val isSelected = index < answersPerQuestion.size && option.label in answersPerQuestion[index]
                        Surface(
                            onClick = {
                                if (!isLocked) {
                                    if (isSingle) {
                                        // Immediate submit for single-question single-select
                                        localSubmitted = true
                                        onSubmit(listOf(listOf(option.label)))
                                    } else {
                                        if (index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = listOf(option.label)
                                        }
                                    }
                                }
                            },
                            enabled = !isLocked,
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) accentColor else accentColor.copy(alpha = 0.7f),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (option.description.isNotEmpty()) {
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // "Type your own answer" — inline text field
                if (q.custom) {
                    val currentAnswers = if (index < answersPerQuestion.size) answersPerQuestion[index] else emptyList()
                    val customAnswer = currentAnswers.firstOrNull { ans -> q.options.none { it.label == ans } }

                    if (customAnswer != null) {
                        // Show selected custom answer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = accentColor,
                                )
                                Text(
                                    text = customAnswer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accentColor,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        if (!isLocked && index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = emptyList()
                                        }
                                    },
                                    enabled = !isLocked,
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp),
                                        tint = accentColor.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    } else {
                        var isEditingCustom by remember { mutableStateOf(false) }
                        var customText by remember { mutableStateOf("") }

                        if (!isEditingCustom) {
                            Surface(
                                onClick = { isEditingCustom = true },
                                enabled = !isLocked,
                                shape = RoundedCornerShape(8.dp),
                                color = androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = accentColor.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = "Type your own answer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accentColor.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                enabled = !isLocked,
                                placeholder = {
                                    Text(
                                        "Type answer…",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                trailingIcon = {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val trimmed = customText.trim()
                                                if (trimmed.isNotBlank()) {
                                                    if (isSingle) {
                                                        localSubmitted = true
                                                        onSubmit(listOf(listOf(trimmed)))
                                                    } else {
                                                        if (index < answersPerQuestion.size) {
                                                            answersPerQuestion[index] = listOf(trimmed)
                                                        }
                                                        isEditingCustom = false
                                                        customText = ""
                                                    }
                                                }
                                            },
                                            enabled = customText.isNotBlank() && !isLocked,
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Submit",
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        IconButton(onClick = {
                                            isEditingCustom = false
                                            customText = ""
                                        }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Bottom actions: Reject + Submit (multi-select only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = {
                        localSubmitted = true
                        onReject()
                    },
                    enabled = !isLocked,
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.labelMedium)
                }
                if (!isSingle) {
                    Button(
                        onClick = {
                            localSubmitted = true
                            onSubmit(answersPerQuestion.map { it.toList() })
                        },
                        enabled = answersPerQuestion.any { it.isNotEmpty() } && !isLocked,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text("Submit", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

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
    val filteredProviders = remember(searchQuery, providers) {
        if (searchQuery.isBlank()) {
            providers
        } else {
            providers.filter { provider ->
                provider.name.contains(searchQuery, ignoreCase = true)
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
