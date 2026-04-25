package com.nodex.client.ui.serveredit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nodex.client.core.data.local.SshKeyEntity
import com.nodex.client.domain.model.AuthType
import com.nodex.client.ui.screens.server.ConnectionTestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditorForm(
    state: ServerEditorState,
    onStateChange: (ServerEditorState) -> Unit,
    savedKeys: List<SshKeyEntity>,
    keyImportError: String?,
    testState: ConnectionTestState,
    primaryButtonText: String,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onImportKey: (
        name: String,
        keyText: String,
        passphrase: String?,
        onComplete: (Result<SshKeyEntity>) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val validation = state.validation()
    var pendingKeyText by remember { mutableStateOf<String?>(null) }
    var pendingKeyName by remember { mutableStateOf("") }
    var pendingKeyPassphrase by remember { mutableStateOf("") }
    var showSavedKeysDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val keyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val keyText = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (keyText != null) {
                pendingKeyText = keyText
                pendingKeyName = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?')
                    ?: "Imported Key"
                pendingKeyPassphrase = ""
                showImportDialog = true
            }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { onStateChange(state.copy(name = it)) },
            label = { Text("Server Name") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "serverEditor.name" },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.hostname,
            onValueChange = { onStateChange(state.copy(hostname = it)) },
            label = { Text("Hostname / IP") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "serverEditor.host" },
            singleLine = true,
            isError = validation.hostError != null
        )
        validation.hostError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.portText,
            onValueChange = { onStateChange(state.copy(portText = it)) },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "serverEditor.port" },
            singleLine = true,
            isError = validation.portError != null
        )
        validation.portError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.username,
            onValueChange = { onStateChange(state.copy(username = it)) },
            label = { Text("Username") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "serverEditor.username" },
            singleLine = true,
            isError = validation.usernameError != null
        )
        validation.usernameError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Authentication", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuthChoiceButton(
                selected = state.authType == AuthType.PASSWORD,
                label = "Password",
                modifier = Modifier.weight(1f),
                onClick = { onStateChange(state.copy(authType = AuthType.PASSWORD)) }
            )
            AuthChoiceButton(
                selected = state.authType == AuthType.KEY_DATA,
                label = "SSH Key",
                modifier = Modifier.weight(1f),
                onClick = { onStateChange(state.copy(authType = AuthType.KEY_DATA)) }
            )
            AuthChoiceButton(
                selected = state.authType == AuthType.NONE,
                label = "Later",
                modifier = Modifier.weight(1f),
                onClick = { onStateChange(state.copy(authType = AuthType.NONE)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (state.authType) {
            AuthType.PASSWORD -> {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onStateChange(state.copy(password = it)) },
                    label = {
                        Text(if (state.hasSavedPassword) "Password (leave blank to keep current)" else "Password")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "serverEditor.password" },
                    singleLine = true,
                    isError = validation.authError != null
                )
            }

            AuthType.KEY_DATA -> {
                OutlinedButton(
                    onClick = { keyPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "serverEditor.importKey" }
                ) {
                    Text("Import Private Key")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showSavedKeysDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "serverEditor.chooseKey" },
                    enabled = savedKeys.isNotEmpty()
                ) {
                    Text(if (savedKeys.isEmpty()) "No Saved Keys Yet" else "Choose from Library")
                }
                if (state.selectedKeyLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Selected: ${state.selectedKeyLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AuthType.NONE -> {
                Text(
                    "You can save this server now and add credentials later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AuthType.KEY_FILE -> Unit
        }

        validation.authError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        keyImportError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onTestConnection,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "serverEditor.testConnection" },
            enabled = validation.canSave && !testState.isTesting
        ) {
            Text(if (testState.isTesting) "Testing..." else "Test Connection")
        }

        testState.message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (testState.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "serverEditor.save" },
            enabled = validation.canSave
        ) {
            Text(primaryButtonText)
        }

        footer()
    }

    if (showSavedKeysDialog) {
        AlertDialog(
            onDismissRequest = { showSavedKeysDialog = false },
            title = { Text("SSH Key Library") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (savedKeys.isEmpty()) {
                        Text("Import a private key first.")
                    } else {
                        savedKeys.forEach { key ->
                            OutlinedButton(
                                onClick = {
                                    onStateChange(
                                        state.copy(
                                            selectedKeyId = key.id,
                                            selectedKeyLabel = "${key.name} · ${key.fingerprint}"
                                        )
                                    )
                                    showSavedKeysDialog = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { testTag = "serverEditor.savedKey.${key.name}" }
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(key.name)
                                    Text(
                                        key.fingerprint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedKeysDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showImportDialog && pendingKeyText != null) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingKeyText = null
            },
            title = { Text("Import SSH Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pendingKeyName,
                        onValueChange = { pendingKeyName = it },
                        label = { Text("Key Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pendingKeyPassphrase,
                        onValueChange = { pendingKeyPassphrase = it },
                        label = { Text("Passphrase (if needed)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val keyText = pendingKeyText ?: return@TextButton
                        onImportKey(
                            pendingKeyName,
                            keyText,
                            pendingKeyPassphrase
                        ) { result ->
                            result.onSuccess { key ->
                                onStateChange(
                                    state.copy(
                                        authType = AuthType.KEY_DATA,
                                        selectedKeyId = key.id,
                                        selectedKeyLabel = "${key.name} · ${key.fingerprint}"
                                    )
                                )
                                showImportDialog = false
                                pendingKeyText = null
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        pendingKeyText = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AuthChoiceButton(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}
