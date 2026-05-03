package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.Part
import me.xiaok.opencode.domain.model.PermissionRequest
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
// Share URL Dialog — shown after /share command succeeds
// ---------------------------------------------------------------------------

@Composable
fun ShareUrlDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Session shared",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Share link created successfully.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(url))
                                copied = true
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (copied) "Copied" else "Copy",
                                modifier = Modifier.size(16.dp),
                                tint = if (copied) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(url))
                    onDismiss()
                },
            ) {
                Text("Copy & Close")
            }
        },
    )
}

class DialogTriggers {
    var revertMessageId by mutableStateOf<String?>(null)
        private set
    var deleteMessageId by mutableStateOf<String?>(null)
        private set
    var showRename by mutableStateOf(false)
        private set

    fun showRevert(messageId: String) { revertMessageId = messageId }
    fun clearRevert() { revertMessageId = null }
    fun showDelete(messageId: String) { deleteMessageId = messageId }
    fun clearDelete() { deleteMessageId = null }
    fun triggerRename() { showRename = true }
    fun clearRename() { showRename = false }
}

@Composable
internal fun ChatDialogHost(
    content: ChatContentState,
    callbacks: ChatCallbacks,
    dialogTriggers: DialogTriggers,
) {
    content.permissions.firstOrNull()?.let { request ->
        PermissionDialog(
            request = request,
            onReply = callbacks.onReplyPermission,
            onDismiss = {},
        )
    }

    dialogTriggers.revertMessageId?.let { messageId ->
        val turn = content.turns.find { it.userMessage.id == messageId }
        val messagePreview = turn?.userMessage?.parts
            ?.filterIsInstance<Part.Text>()
            ?.firstOrNull()
            ?.text
            ?: ""

        RevertConfirmationDialog(
            messagePreview = messagePreview,
            onConfirm = { callbacks.onRevertSession(messageId) },
            onDismiss = { dialogTriggers.clearRevert() },
        )
    }

    if (dialogTriggers.showRename) {
        RenameSessionDialog(
            currentTitle = content.session?.title?.ifEmpty { "Chat" } ?: "Chat",
            onConfirm = { newTitle -> callbacks.onRenameSession(newTitle) },
            onDismiss = { dialogTriggers.clearRename() },
        )
    }

    dialogTriggers.deleteMessageId?.let { messageId ->
        AlertDialog(
            onDismissRequest = { dialogTriggers.clearDelete() },
            title = { Text("Delete message") },
            text = { Text("Are you sure you want to delete this message? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        callbacks.onDeleteMessage(messageId)
                        dialogTriggers.clearDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogTriggers.clearDelete() }) {
                    Text("Cancel")
                }
            },
        )
    }
}
