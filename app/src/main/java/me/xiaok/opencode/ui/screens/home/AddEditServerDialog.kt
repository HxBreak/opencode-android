package me.xiaok.opencode.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.xiaok.opencode.domain.model.ServerConnection
import java.util.UUID

// ---------------------------------------------------------------------------
// Add / Edit Server Dialog — simplified: Name+URL core, advanced options collapsible
// ---------------------------------------------------------------------------

@Composable
internal fun AddEditServerDialog(
    existingServer: ServerConnection?,
    onSave: (ServerConnection) -> Unit,
    onDismiss: () -> Unit,
) {
    val isEdit = existingServer != null

    var name by remember { mutableStateOf(existingServer?.name ?: "") }
    var url by remember { mutableStateOf(existingServer?.baseUrl ?: "") }
    var username by remember { mutableStateOf(existingServer?.username ?: "") }
    var password by remember { mutableStateOf(existingServer?.password ?: "") }
    var autoConnect by remember { mutableStateOf(existingServer?.autoConnect ?: true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val nameError = name.isBlank()
    val urlError = url.isBlank()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Edit Server" else "Add Server",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Core fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    isError = urlError,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                        )
                    },
                    supportingText = {
                        Text(
                            text = "http:// will be prepended if no scheme is provided",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Advanced toggle
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                ) {
                    Text(
                        text = if (showAdvanced) "Hide advanced" else "Advanced",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // Advanced fields
                if (showAdvanced) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Auto-connect",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalUrl = if (
                        url.isNotBlank() &&
                        !url.startsWith("http://", ignoreCase = true) &&
                        !url.startsWith("https://", ignoreCase = true)
                    ) {
                        "http://$url"
                    } else {
                        url
                    }

                    val server = ServerConnection(
                        id = existingServer?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        baseUrl = finalUrl.trim(),
                        username = username.trim(),
                        password = password.trim(),
                        autoConnect = autoConnect,
                    )
                    onSave(server)
                },
                enabled = !nameError && !urlError,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel")
            }
        },
    )
}
