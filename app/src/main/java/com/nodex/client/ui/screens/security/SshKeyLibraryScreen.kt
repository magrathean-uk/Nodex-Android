package com.nodex.client.ui.screens.security

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.core.data.local.SshKeyEntity
import com.nodex.client.ui.components.EmptyState
import com.nodex.client.ui.components.NodexDetailScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshKeyLibraryScreen(
    viewModel: SshKeyLibraryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<SshKeyEntity?>(null) }
    var pendingKeyText by remember { mutableStateOf<String?>(null) }
    var pendingKeyName by remember { mutableStateOf("") }
    var pendingPassphrase by remember { mutableStateOf("") }
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
                pendingPassphrase = ""
                viewModel.clearImportError()
                showImportDialog = true
            }
        }
    }

    NodexDetailScaffold(
        title = "SSH Key Library",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { keyPicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.Key, contentDescription = "Import key")
            }
        }
    ) { innerPadding ->
        if (keys.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                EmptyState(
                    title = "No Saved Keys",
                    subtitle = "Import private keys here and reuse them across multiple servers."
                )
                importError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item("header") {
                    Text(
                        "Private keys saved securely for reuse across servers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                importError?.let { error ->
                    item("error") {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                items(keys, key = { it.id }) { key ->
                    KeyRow(
                        key = key,
                        onDelete = { pendingDelete = key }
                    )
                }
            }
        }
    }

    pendingDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Key") },
            text = { Text("Delete \"${key.name}\"? Servers using it will lose access until a new key is selected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteKey(key.id)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pendingPassphrase,
                        onValueChange = { pendingPassphrase = it },
                        label = { Text("Passphrase (if needed)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val keyText = pendingKeyText ?: return@TextButton
                        viewModel.importKey(
                            name = pendingKeyName,
                            keyText = keyText,
                            passphrase = pendingPassphrase
                        ) { result ->
                            result.onSuccess {
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
private fun KeyRow(
    key: SshKeyEntity,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    key.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    key.keyType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    key.fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete key")
            }
        }
    }
}
