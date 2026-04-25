package com.nodex.client.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.DistroFamily
import com.nodex.client.domain.model.ServerPowerAction
import com.nodex.client.ui.components.NodexDetailScaffold
import com.nodex.client.ui.components.NodexSectionTitle
import com.nodex.client.ui.serveredit.ServerEditorForm
import com.nodex.client.ui.serveredit.ServerEditorState
import com.nodex.client.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    serverId: String,
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    addServerViewModel: com.nodex.client.ui.screens.server.AddServerViewModel = hiltViewModel()
) {
    val servers by appViewModel.servers.collectAsStateWithLifecycle()
    val server = servers.find { it.id == serverId } ?: run {
        onBack()
        return
    }
    val savedKeys by addServerViewModel.savedKeys.collectAsStateWithLifecycle()
    val keyImportError by addServerViewModel.keyImportError.collectAsStateWithLifecycle()
    val testState by addServerViewModel.testState.collectAsStateWithLifecycle()

    var state by rememberSaveable(serverId, stateSaver = ServerEditorState.Saver) {
        mutableStateOf(
            ServerEditorState.fromServerConfig(
                server = server,
                hasSavedPassword = appViewModel.hasPassword(serverId)
            )
        )
    }
    var sudoPassword by rememberSaveable(serverId) { mutableStateOf("") }
    var distroMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var lookbackMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showPowerDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(savedKeys, state.selectedKeyId) {
        if (state.selectedKeyId != null && state.selectedKeyLabel.isBlank()) {
            val key = savedKeys.firstOrNull { it.id == state.selectedKeyId }
            if (key != null) {
                state = state.copy(selectedKeyLabel = "${key.name} · ${key.fingerprint}")
            }
        }
    }

    NodexDetailScaffold(title = server.name.ifBlank { server.hostname }, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ServerEditorForm(
                state = state,
                onStateChange = { state = it },
                savedKeys = savedKeys,
                keyImportError = keyImportError,
                testState = testState,
                primaryButtonText = "Save Changes",
                onTestConnection = {
                    addServerViewModel.testConnection(
                        server = state.toServerConfig(server.id),
                        password = state.password.takeIf {
                            state.authType == AuthType.PASSWORD && it.isNotBlank()
                        }
                    )
                },
                onSave = {
                    appViewModel.updateServer(state.toServerConfig(server.id))
                    if (state.authType == AuthType.PASSWORD) {
                        if (state.password.isNotBlank()) {
                            appViewModel.setPassword(server.id, state.password)
                        }
                    } else {
                        appViewModel.setPassword(server.id, null)
                    }
                    if (sudoPassword.isNotBlank()) {
                        appViewModel.setSudoPassword(server.id, sudoPassword)
                    }
                    onBack()
                },
                onImportKey = addServerViewModel::importKey
            )

            HorizontalDivider()

            NodexSectionTitle("Server")
            SettingSwitchRow(
                title = "Favorite",
                subtitle = "Show this server first in the switcher and overview snapshot.",
                checked = state.isFavorite,
                onCheckedChange = { state = state.copy(isFavorite = it) }
            )
            SettingSwitchRow(
                title = "Auto-connect",
                subtitle = "Keep normal monitoring behavior for this server.",
                checked = state.autoConnect,
                onCheckedChange = { state = state.copy(autoConnect = it) }
            )
            SettingSwitchRow(
                title = "Public IP Lookup",
                subtitle = "Optional external lookup during slow polls.",
                checked = state.publicIPEnabled,
                onCheckedChange = { state = state.copy(publicIPEnabled = it) }
            )

            LabeledMenuRow(
                label = "Distribution",
                value = state.distroFamily.displayName,
                expanded = distroMenuExpanded,
                onExpandedChange = { distroMenuExpanded = it }
            ) {
                DistroFamily.entries.forEach { distro ->
                    DropdownMenuItem(
                        text = { Text(distro.displayName) },
                        onClick = {
                            state = state.copy(distro = distro.name.lowercase())
                            distroMenuExpanded = false
                        }
                    )
                }
            }

            StepperRow(
                label = "Poll Interval",
                value = "${state.pollIntervalSeconds}s",
                onDecrement = {
                    state = state.copy(pollIntervalSeconds = (state.pollIntervalSeconds - 1).coerceAtLeast(2))
                },
                onIncrement = {
                    state = state.copy(pollIntervalSeconds = (state.pollIntervalSeconds + 1).coerceAtMost(120))
                }
            )

            LabeledMenuRow(
                label = "Alert Lookback",
                value = "${state.alertLookbackMinutes}m",
                expanded = lookbackMenuExpanded,
                onExpandedChange = { lookbackMenuExpanded = it }
            ) {
                listOf(15, 30, 60, 120).forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text("$minutes min") },
                        onClick = {
                            state = state.copy(alertLookbackMinutes = minutes)
                            lookbackMenuExpanded = false
                        }
                    )
                }
            }

            HorizontalDivider()

            NodexSectionTitle("Sudo")
            Text(
                "Save a sudo password for service actions, Docker sudo fallback, and power actions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = sudoPassword,
                onValueChange = { sudoPassword = it },
                label = {
                    Text(
                        if (appViewModel.hasSudoPassword(server.id)) {
                            "Update Sudo Password"
                        } else {
                            "Sudo Password"
                        }
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (appViewModel.hasSudoPassword(server.id)) {
                TextButton(onClick = { appViewModel.setSudoPassword(server.id, null) }) {
                    Text("Remove Saved Sudo Password")
                }
            }

            HorizontalDivider()

            NodexSectionTitle("Power Actions")
            OutlinedButton(
                onClick = { showPowerDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Power Actions")
            }

            HorizontalDivider()

            NodexSectionTitle("Danger Zone")
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Server")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Server") },
            text = { Text("Remove ${server.name.ifBlank { server.hostname }}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appViewModel.deleteServer(server.id)
                        showDeleteConfirm = false
                        onBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPowerDialog) {
        ServerDetailPowerDialog(
            serverName = server.name.ifBlank { server.hostname },
            onDismiss = { showPowerDialog = false },
            onAction = { action ->
                showPowerDialog = false
                appViewModel.serverPowerAction(server.id, action)
            }
        )
    }
}

private val ServerEditorState.distroFamily: DistroFamily
    get() = DistroFamily.fromOsId(distro)

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDecrement) { Text("-") }
            Text(value)
            OutlinedButton(onClick = onIncrement) { Text("+") }
        }
    }
}

@Composable
private fun LabeledMenuRow(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            OutlinedButton(onClick = { onExpandedChange(true) }) {
                Text(value)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
                content()
            }
        }
    }
}

@Composable
private fun ServerDetailPowerDialog(
    serverName: String,
    onDismiss: () -> Unit,
    onAction: (ServerPowerAction) -> Unit
) {
    var confirmAction by rememberSaveable { mutableStateOf<ServerPowerAction?>(null) }

    if (confirmAction != null) {
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Confirm ${confirmAction!!.displayName}") },
            text = { Text("Run ${confirmAction!!.displayName.lowercase()} on $serverName?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(confirmAction!!)
                        confirmAction = null
                    }
                ) {
                    Text("Run", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Power Actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerPowerAction.entries.forEach { action ->
                        OutlinedButton(
                            onClick = { confirmAction = action },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(action.displayName)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}
