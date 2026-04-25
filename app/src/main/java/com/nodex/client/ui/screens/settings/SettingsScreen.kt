package com.nodex.client.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.BuildConfig
import com.nodex.client.data.prefs.UserPreferences
import com.nodex.client.domain.model.ConnectionState
import com.nodex.client.domain.model.ServerConfig
import com.nodex.client.ui.components.StatusGreen
import com.nodex.client.ui.components.StatusRed
import com.nodex.client.ui.components.StatusYellow
import com.nodex.client.ui.components.NodexSectionTitle
import com.nodex.client.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    appViewModel: AppViewModel? = null,
    onNavigateToAddServer: () -> Unit = {},
    onNavigateToServerDetail: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToHostKeys: () -> Unit = {},
    onNavigateToSshKeyLibrary: () -> Unit = {}
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle(initialValue = UserPreferences.Theme.SYSTEM)
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle(initialValue = false)
    val servers by appViewModel?.servers?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val selectedId by appViewModel?.selectedServerId?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val connectionStates by appViewModel?.connectionStates?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyMap()) }

    var serverToDelete by remember { mutableStateOf<ServerConfig?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val activeServer = servers.find { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Status")
        if (activeServer != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        activeServer.name.ifBlank { activeServer.hostname },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${activeServer.username}@${activeServer.hostname}:${activeServer.port}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when (connectionStates[activeServer.id]) {
                            is ConnectionState.Connected -> "Live"
                            is ConnectionState.Connecting -> "Connecting"
                            is ConnectionState.Error -> "Offline"
                            else -> "Offline"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text("No server selected", style = MaterialTheme.typography.bodyMedium)
        }

        SectionTitle("Server")
        if (servers.isEmpty()) {
            Text(
                "No servers configured",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        servers.forEach { server ->
            ServerRow(
                server = server,
                isActive = server.id == selectedId,
                connectionState = connectionStates[server.id],
                hasSudo = appViewModel?.hasSudoPassword(server.id) == true,
                onSelect = { appViewModel?.selectServer(server.id) },
                onEdit = { onNavigateToServerDetail(server.id) },
                onFavoriteToggle = {
                    appViewModel?.updateServer(server.copy(isFavorite = !server.isFavorite))
                },
                onDelete = { serverToDelete = server }
            )
        }
        Button(
            onClick = onNavigateToAddServer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Server")
        }

        if (activeServer != null) {
            HorizontalDivider()
            SectionTitle("Monitoring")
            SettingSwitchRow(
                title = "Auto-connect",
                subtitle = "Keep normal monitoring behavior for the selected server.",
                checked = activeServer.autoConnect,
                onCheckedChange = { appViewModel?.updateServer(activeServer.copy(autoConnect = it)) }
            )
            SettingSwitchRow(
                title = "Public IP Lookup",
                subtitle = "Optional external lookup during slow polls.",
                checked = activeServer.publicIPEnabled,
                onCheckedChange = { appViewModel?.updateServer(activeServer.copy(publicIPEnabled = it)) }
            )
            Text(
                "Poll interval, alert lookback, auth, distro, sudo, and power actions live in each server detail screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()
        SectionTitle("Tools")
        OutlinedButton(onClick = onNavigateToHistory, modifier = Modifier.fillMaxWidth()) {
            Text("Metrics History")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToHostKeys, modifier = Modifier.fillMaxWidth()) {
            Text("Known Hosts")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToSshKeyLibrary, modifier = Modifier.fillMaxWidth()) {
            Text("SSH Key Library")
        }

        HorizontalDivider()
        SectionTitle("Appearance")
        ThemeOptionRow("System Default", theme == UserPreferences.Theme.SYSTEM) {
            viewModel.setTheme(UserPreferences.Theme.SYSTEM)
        }
        ThemeOptionRow("Light Mode", theme == UserPreferences.Theme.LIGHT) {
            viewModel.setTheme(UserPreferences.Theme.LIGHT)
        }
        ThemeOptionRow("Dark Mode", theme == UserPreferences.Theme.DARK) {
            viewModel.setTheme(UserPreferences.Theme.DARK)
        }

        if (isDemoMode) {
            HorizontalDivider()
            SectionTitle("Demo Mode")
            Text(
                "Demo mode is enabled for review. Server connections are simulated.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = { viewModel.exitDemoMode() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exit Demo Mode")
            }
        }

        HorizontalDivider()
        SectionTitle("Legal & Reset")
        Text("Privacy Policy · Terms of Use · Contact", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
        ) {
            Text("Reset App")
        }
    }

    serverToDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Delete Server") },
            text = { Text("Remove ${server.name.ifBlank { server.hostname }}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        appViewModel?.deleteServer(server.id)
                        serverToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset App") },
            text = { Text("This will delete all servers, metrics history, credentials, and trusted host keys, and return to onboarding.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetApp()
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ServerRow(
    server: ServerConfig,
    isActive: Boolean,
    connectionState: ConnectionState?,
    hasSudo: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val indicatorColor = when (connectionState) {
                is ConnectionState.Connected -> StatusGreen
                is ConnectionState.Connecting -> StatusYellow
                is ConnectionState.Error -> StatusRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = indicatorColor,
                modifier = Modifier.width(10.dp).height(10.dp)
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        server.name.ifBlank { server.hostname },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "${server.username}@${server.hostname}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasSudo) {
                    Text(
                        "Sudo enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (server.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (server.isFavorite) StatusYellow else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit server")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete server", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

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
private fun SectionTitle(text: String) {
    NodexSectionTitle(text)
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}
