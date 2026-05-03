package me.xiaok.opencode.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Expand
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.xiaok.opencode.domain.model.BuiltInCommands
import me.xiaok.opencode.domain.model.MentionItem
import me.xiaok.opencode.domain.model.SessionStatus

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sessionStatus: SessionStatus,
    isSending: Boolean = false,
    sessionTitle: String,
    stats: ChatStatsState,
    selection: ChatSelectionState,
    attachedImages: List<AttachedImage>,
    callbacks: ChatCallbacks,
    mentionDisplayTexts: Set<String>,
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isBusy = sessionStatus !is SessionStatus.Idle || isSending
    val canSend = (text.isNotBlank() || attachedImages.isNotEmpty()) && !isBusy

    val cursorPosition = text.length
    val atDetection = detectAtMention(text, cursorPosition)
    val trimmed = text.trimStart()
    val isCommandMode = atDetection == null && trimmed.startsWith("/") && trimmed.count { it == '/' } == 1

    var showMentionPopup by remember { mutableStateOf(false) }
    var mentionQuery by remember { mutableStateOf("") }
    var mentionStartIndex by remember { mutableStateOf(-1) }
    var fileResults by remember { mutableStateOf<List<String>>(emptyList()) }

    // Tracks when user clicked a server command, to dismiss the command popup.
    // Reset when text changes from user input (text != dismissedCommandText).
    var commandPopupDismissed by remember { mutableStateOf(false) }
    var dismissedCommandText by remember { mutableStateOf("") }

    LaunchedEffect(atDetection) {
        if (atDetection != null) {
            showMentionPopup = true
            mentionQuery = atDetection.query
            mentionStartIndex = atDetection.startIndex
        } else {
            showMentionPopup = false
        }
    }

    LaunchedEffect(isCommandMode) {
        if (isCommandMode) showMentionPopup = false
    }

    val filteredAgents = if (atDetection != null) {
        val q = atDetection.query
        if (q.isBlank()) {
            selection.agents.filter { !it.hidden && it.mode != "subagent" }
        } else {
            selection.agents.filter { !it.hidden && it.mode != "subagent" && it.name.contains(q, ignoreCase = true) }
        }
    } else emptyList()

    LaunchedEffect(mentionQuery, showMentionPopup) {
        if (showMentionPopup) {
            fileResults = callbacks.onSearchFiles(mentionQuery)
        } else {
            fileResults = emptyList()
        }
    }

    val commandQuery = if (isCommandMode) text.trimStart().removePrefix("/").trim() else ""
    val filteredBuiltin = if (!isCommandMode) emptyList()
        else BuiltInCommands.filter(commandQuery)
            .filter { cmd ->
                if (selection.shareDisabled) cmd.id !in listOf("share", "unshare") else true
            }
    val filteredServerCommands = if (!isCommandMode) emptyList()
        else if (commandQuery.isBlank()) selection.commands
        else selection.commands.filter {
            it.name.contains(commandQuery, ignoreCase = true)
        }
    val commandPopupVisible = isCommandMode && !(commandPopupDismissed && text == dismissedCommandText)
    val hasAnyCommands = commandPopupVisible && (filteredBuiltin.isNotEmpty() || filteredServerCommands.isNotEmpty())

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            StatusRow(
                status = sessionStatus,
                contextUsagePercent = stats.contextUsagePercent,
                totalTokens = stats.totalTokens,
                totalCost = stats.totalCost,
                conversationTurns = stats.conversationTurns,
            )

            Spacer(modifier = Modifier.height(6.dp))

            SelectorRow(
                agents = selection.agents,
                selectedAgent = selection.selectedAgent,
                onAgentSelected = callbacks.onAgentSelected,
                providers = selection.providers,
                selectedModel = selection.selectedModel,
                onModelSelected = callbacks.onModelSelected,
                variants = selection.selectedModel?.let { ref ->
                    selection.providers
                        .find { it.id == ref.providerID }
                        ?.models?.get(ref.modelID)
                        ?.variantNames ?: emptyList()
                } ?: emptyList(),
                selectedVariant = selection.selectedVariant,
                onVariantSelected = callbacks.onVariantSelected,
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (attachedImages.isNotEmpty()) {
                ImagePreviewRow(
                    images = attachedImages,
                    onRemove = callbacks.onRemoveImage,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

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
                        items(filteredBuiltin.size) { index ->
                            val cmd = filteredBuiltin[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        callbacks.onBuiltInCommand(cmd)
                                        onTextChange("")
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

                        if (filteredBuiltin.isNotEmpty() && filteredServerCommands.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }

                        items(filteredServerCommands.size) { index ->
                            val cmd = filteredServerCommands[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newText = "/${cmd.name} "
                                        commandPopupDismissed = true
                                        dismissedCommandText = newText
                                        onTextChange(newText)
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

            AnimatedVisibility(
                visible = showMentionPopup && mentionStartIndex >= 0 && (filteredAgents.isNotEmpty() || fileResults.isNotEmpty()),
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                MentionPopupContent(
                    agents = filteredAgents,
                    files = fileResults,
                    query = mentionQuery,
                    onSelect = { mentionItem ->
                        val atIndex = mentionStartIndex
                        val queryEnd = atIndex + 1 + mentionQuery.length
                        val before = text.substring(0, atIndex)
                        val insertText = mentionItem.displayText + " "
                        val after = if (queryEnd < text.length) text.substring(queryEnd) else ""
                        val newText = before + insertText + after
                        val newEnd = atIndex + insertText.length

                        val positioned = when (mentionItem) {
                            is MentionItem.FileMention -> mentionItem.copy(
                                start = atIndex,
                                end = newEnd - 1,
                            )
                            is MentionItem.AgentMention -> mentionItem.copy(
                                start = atIndex,
                                end = newEnd - 1,
                            )
                        }
                        onTextChange(newText)
                        callbacks.onMentionSelect(positioned, atIndex, newEnd - 1)
                        showMentionPopup = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            val isMultiline = text.contains('\n')
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    if (isMultiline) {
                        IconButton(
                            onClick = onExpand,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Expand,
                                contentDescription = "Full screen edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    IconButton(onClick = callbacks.onAttachImage) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = TextFieldValue(text, TextRange(text.length)),
                    onValueChange = { onTextChange(it.text) },
                    modifier = Modifier.weight(1f).testTag("chat_input"),
                    visualTransformation = if (mentionDisplayTexts.isNotEmpty()) {
                        MentionTransformation(mentionDisplayTexts)
                    } else {
                        VisualTransformation.None
                    },
                    placeholder = {
                        Text(
                            text = when {
                                isBusy -> "Waiting..."
                                text.startsWith("!") -> "Shell command..."
                                else -> "Message..."
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
                    modifier = Modifier.padding(bottom = 4.dp).testTag("btn_send"),
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
}

@Composable
private fun ImagePreviewRow(
    images: List<AttachedImage>,
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
