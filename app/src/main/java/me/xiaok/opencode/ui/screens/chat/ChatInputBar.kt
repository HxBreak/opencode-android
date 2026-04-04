package me.xiaok.opencode.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import me.xiaok.opencode.domain.model.AgentConfig
import me.xiaok.opencode.domain.model.BuiltInCommand
import me.xiaok.opencode.domain.model.BuiltInCommands
import me.xiaok.opencode.domain.model.CommandInfo
import me.xiaok.opencode.domain.model.ModelRef
import me.xiaok.opencode.domain.model.Provider
import me.xiaok.opencode.domain.model.SessionStatus

// ---------------------------------------------------------------------------
// Chat Input Bar
// ---------------------------------------------------------------------------

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sessionStatus: SessionStatus,
    isSending: Boolean = false,
    sessionTitle: String,
    contextUsagePercent: Int = 0,
    totalTokens: Long = 0L,
    totalCost: Double = 0.0,
    conversationTurns: Int = 0,
    agents: List<AgentConfig> = emptyList(),
    selectedAgent: String? = null,
    onAgentSelected: (String?) -> Unit = {},
    providers: List<Provider> = emptyList(),
    selectedModel: ModelRef? = null,
    onModelSelected: (ModelRef?) -> Unit = {},
    variants: List<String> = listOf("fast", "think", "agentic"),
    selectedVariant: String? = null,
    onVariantSelected: (String?) -> Unit = {},
    attachedImages: List<ChatViewModel.AttachedImage> = emptyList(),
    onAttachImage: () -> Unit = {},
    onRemoveImage: (Int) -> Unit = {},
    commands: List<CommandInfo> = emptyList(),
    onBuiltInCommand: (BuiltInCommand) -> Unit = {},
    onSearchFiles: suspend (String) -> List<String> = { emptyList() },
    modifier: Modifier = Modifier,
) {
    val isBusy = sessionStatus != SessionStatus.IDLE || isSending
    val canSend = (text.isNotBlank() || attachedImages.isNotEmpty()) && !isBusy

    // Detect @ and / triggers from text input (synchronous derivation)
    val trimmed = text.trimStart()
    val isAtMode = trimmed.startsWith("@")
    val isCommandMode = !isAtMode && trimmed.startsWith("/") && trimmed.count { it == '/' } == 1 && !trimmed.contains(" ")

    // File popup state
    var showFilePopup by remember { mutableStateOf(false) }
    var fileQuery by remember { mutableStateOf("") }
    var fileResults by remember { mutableStateOf<List<String>>(emptyList()) }

    // Sync popup visibility from text
    LaunchedEffect(isAtMode, isCommandMode) {
        when {
            isAtMode -> {
                showFilePopup = true
                fileQuery = trimmed.removePrefix("@").trim()
            }
            isCommandMode -> showFilePopup = false
            else -> showFilePopup = false
        }
    }

    // Fetch file results when @ popup is active
    LaunchedEffect(fileQuery, showFilePopup) {
        if (showFilePopup) {
            fileResults = onSearchFiles(fileQuery)
        }
    }

    // Filter commands synchronously from text — no intermediate state
    val commandQuery = if (isCommandMode) trimmed.removePrefix("/").trim() else ""
    val filteredBuiltin = if (!isCommandMode) emptyList()
        else BuiltInCommands.filter(commandQuery)
    val filteredServerCommands = if (!isCommandMode) emptyList()
        else if (commandQuery.isBlank()) commands
        else commands.filter {
            it.name.contains(commandQuery, ignoreCase = true)
        }
    val hasAnyCommands = isCommandMode && (filteredBuiltin.isNotEmpty() || filteredServerCommands.isNotEmpty())

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // 1. Status row
            StatusRow(
                status = sessionStatus,
                contextUsagePercent = contextUsagePercent,
                totalTokens = totalTokens,
                totalCost = totalCost,
                conversationTurns = conversationTurns,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Selector row
            SelectorRow(
                agents = agents,
                selectedAgent = selectedAgent,
                onAgentSelected = onAgentSelected,
                providers = providers,
                selectedModel = selectedModel,
                onModelSelected = onModelSelected,
                variants = variants,
                selectedVariant = selectedVariant,
                onVariantSelected = onVariantSelected,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Image attachment previews
            if (attachedImages.isNotEmpty()) {
                ImagePreviewRow(
                    images = attachedImages,
                    onRemove = onRemoveImage,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 3.5 Command suggestions (above input row)
            AnimatedVisibility(
                visible = hasAnyCommands,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        // Built-in commands first
                        items(filteredBuiltin.size) { index ->
                            val cmd = filteredBuiltin[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onBuiltInCommand(cmd)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "/${cmd.id}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            text = "built-in",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                                            ),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                if (cmd.description.isNotBlank()) {
                                    Text(
                                        text = cmd.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        // Separator between built-in and server commands
                        if (filteredBuiltin.isNotEmpty() && filteredServerCommands.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }

                        // Server commands
                        items(filteredServerCommands.size) { index ->
                            val cmd = filteredServerCommands[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onTextChange("/${cmd.name} ")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "/${cmd.name}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (cmd.description.isNotBlank()) {
                                    Text(
                                        text = cmd.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 4. Input row
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Attach image button
                IconButton(
                    onClick = onAttachImage,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = when {
                                isBusy -> "Waiting for response..."
                                text.startsWith("!") -> "Shell command..."
                                else -> "Type @ for files, / for commands..."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (text.startsWith("!")) {
                            Color(0xFFFFA000)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        },
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { if (canSend) onSend() },
                    enabled = canSend,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (text.trimStart().startsWith("!") && canSend) {
                            Color(0xFFFFA000)
                        } else if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
            }
        }
    }

    // @ File mention popup
    if (showFilePopup && fileResults.isNotEmpty()) {
        Popup(
            alignment = Alignment.BottomStart,
            offset = IntOffset(12, -8),
            properties = PopupProperties(focusable = false),
            onDismissRequest = { showFilePopup = false },
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .heightIn(max = 200.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    items(fileResults.size) { index ->
                        val filePath = fileResults[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val insertText = if (text.trimStart().startsWith("@")) {
                                        text.replaceFirst(Regex("^\\s*@"), "") + filePath
                                    } else {
                                        filePath
                                    }
                                    onTextChange(insertText)
                                    showFilePopup = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = filePath,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Image preview row
// ---------------------------------------------------------------------------

@Composable
private fun ImagePreviewRow(
    images: List<ChatViewModel.AttachedImage>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(images) { index, image ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )

                // Remove button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status row — working state pulse + context usage + turns + tokens + cost
// ---------------------------------------------------------------------------

@Composable
private fun StatusRow(
    status: SessionStatus,
    contextUsagePercent: Int,
    totalTokens: Long,
    totalCost: Double,
    conversationTurns: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status indicator
        when (status) {
            SessionStatus.IDLE -> {
                StatusDot(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Idle",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SessionStatus.BUSY -> {
                PulsingDot(color = Color(0xFF4CAF50))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Working",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color(0xFF4CAF50),
                )
            }
            SessionStatus.RETRY -> {
                PulsingDot(color = Color(0xFFFFA000))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Retrying",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color(0xFFFFA000),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Conversation turns
        if (conversationTurns > 0) {
            Text(
                text = "$conversationTurns turns",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Token count
        if (totalTokens > 0) {
            Text(
                text = formatTokenCount(totalTokens),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totalCost > 0.0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$${String.format("%.2f", totalCost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Context usage display
        if (contextUsagePercent > 0) {
            val ctxColor = when {
                contextUsagePercent >= 90 -> MaterialTheme.colorScheme.error
                contextUsagePercent >= 70 -> Color(0xFFFFA000)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "Ctx: $contextUsagePercent%",
                style = MaterialTheme.typography.labelSmall,
                color = ctxColor,
            )
        }
    }
}

private fun formatTokenCount(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000.0)
        else -> "$tokens"
    }
}

// ---------------------------------------------------------------------------
// Selector row — Agent / Model / Variant chips
// ---------------------------------------------------------------------------

@Composable
private fun SelectorRow(
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
        // Agent selector
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

        // Model selector — opens grouped model picker dialog
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

        // Variant selector — cycles through fast/think/agentic
        VariantChip(
            variants = variants,
            selectedVariant = selectedVariant,
            onVariantSelected = onVariantSelected,
        )
    }
}

// ---------------------------------------------------------------------------
// Selector chip — compact dropdown button
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Model selector chip — opens grouped ModelPickerDialog
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Variant chip — cycles through fast/think/agentic on tap
// ---------------------------------------------------------------------------

@Composable
private fun VariantChip(
    variants: List<String>,
    selectedVariant: String?,
    onVariantSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayText = when (selectedVariant) {
        "fast" -> "Fast"
        "think" -> "Think"
        "agentic" -> "Agentic"
        else -> null
    }

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
                            // Cycled through all → back to null (auto)
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
                text = displayText ?: "Speed",
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

// ---------------------------------------------------------------------------
// Status indicators
// ---------------------------------------------------------------------------

@Composable
private fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = pulseAlpha)),
    )
}
