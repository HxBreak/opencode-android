package me.xiaok.opencode.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Static state bridge for passing text between ChatScreen and FullScreenEditor
 * without involving navigation arguments.
 *
 * The caller writes [initialText] before navigating, then observes [resultText]
 * as a Compose [MutableState] after popping back.  [consumeResult] clears the result after reading.
 */
object FullScreenEditorState {
    var initialText: String by mutableStateOf("")
        private set
    var resultText: String? by mutableStateOf(null)
        private set

    fun prepare(text: String) {
        initialText = text
        resultText = null
    }

    fun setResult(text: String) {
        resultText = text
    }

    fun consumeResult(): String? {
        val result = resultText
        resultText = null
        return result
    }
}

/**
 * Route-level composable wired into NavGraph.
 * Reads [FullScreenEditorState.initialText] and writes back to
 * [FullScreenEditorState.setResult] on back/send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenEditorRoute(
    onNavigateBack: () -> Unit,
) {
    val initialText = FullScreenEditorState.initialText
    var text by rememberSaveable { mutableStateOf(initialText) }

    fun confirm() {
        FullScreenEditorState.setResult(text)
        onNavigateBack()
    }

    BackHandler(onBack = { confirm() })

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .imePadding(),
        ) {
            TopAppBar(
                title = { Text("Edit message") },
                navigationIcon = {
                    IconButton(onClick = { confirm() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { confirm() },
                        enabled = text.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (text.isNotBlank()) {
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
                            tint = if (text.isNotBlank()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        Text(
                            text = "Type your message...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
