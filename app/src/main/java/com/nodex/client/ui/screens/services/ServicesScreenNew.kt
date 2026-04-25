package com.nodex.client.ui.screens.services

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.domain.model.*
import com.nodex.client.ui.components.*
import com.nodex.client.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

import com.nodex.client.ui.screens.overview.NoServerState

enum class ServiceFilter { All, Running, Failed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    viewModel: AppViewModel,
    onNavigateToAddServer: () -> Unit = {}
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val servicesMap by viewModel.services.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val errors by viewModel.serverErrors.collectAsStateWithLifecycle()
    val lastUpdatedMap by viewModel.lastUpdated.collectAsStateWithLifecycle()

    if (servers.isEmpty()) {
        NoServerState(onNavigateToAddServer)
        return
    }

    val serverId = selectedId ?: return
    val allServices = servicesMap[serverId] ?: emptyList()
    val connectionState = connectionStates[serverId] ?: ConnectionState.Disconnected

    var filter by remember { mutableStateOf(ServiceFilter.Running) }
    var searchText by remember { mutableStateOf("") }
    var selectedService by remember { mutableStateOf<ServiceInfo?>(null) }
    var showSudoBanner by remember { mutableStateOf(true) }
    var showSudoPrompt by remember { mutableStateOf(false) }
    val hasSudo = viewModel.hasSudoPassword(serverId)

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { viewModel.refreshNow(); delay(1000); isRefreshing = false }
    }

    val filteredServices = allServices
        .filter { svc ->
            when (filter) {
                ServiceFilter.All -> true
                ServiceFilter.Running -> svc.state == ServiceState.Running
                ServiceFilter.Failed -> svc.state == ServiceState.Failed
            }
        }
        .filter { svc ->
            searchText.isBlank() ||
                svc.name.contains(searchText, ignoreCase = true) ||
                svc.description.contains(searchText, ignoreCase = true)
        }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true }) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Error / connection status
        val errorMessage = errors[serverId]
        if (errorMessage != null) {
            ErrorBanner(errorMessage, onRetry = { viewModel.refreshNow() })
        } else {
            ConnectionBanner(connectionState)
        }
        LastUpdatedText(lastUpdatedMap[serverId])

        // Sudo access banner
        if (!hasSudo && showSudoBanner) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sudo access recommended",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Required for starting, stopping, and managing services.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    TextButton(onClick = { showSudoPrompt = true }) {
                        Text("Set Up")
                    }
                }
            }
        }

        // Search
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search services") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Filter tabs
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ServiceFilter.entries.forEachIndexed { index, f ->
                SegmentedButton(
                    selected = filter == f,
                    onClick = { filter = f },
                    shape = SegmentedButtonDefaults.itemShape(index, ServiceFilter.entries.size)
                ) {
                    Text(f.name)
                }
            }
        }

        // Service list
        if (filteredServices.isEmpty()) {
            when {
                connectionState is ConnectionState.Disconnected ->
                    EmptyState("Not Connected", "Connect to a server to view services")
                connectionState is ConnectionState.Connecting ->
                    EmptyState("Connecting…", "Hang tight while we connect")
                else -> EmptyState("No services", "No services match this filter")
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    filteredServices.forEachIndexed { index, svc ->
                        ServiceRow(svc) { selectedService = svc }
                        if (index < filteredServices.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
    } // PullToRefreshBox

    // Sudo setup dialog
    if (showSudoPrompt) {
        SudoSetupDialog(
            onConfirm = { password ->
                viewModel.setSudoPassword(serverId, password)
                showSudoPrompt = false
            },
            onDismiss = { showSudoPrompt = false }
        )
    }

    // Service detail bottom sheet
    selectedService?.let { svc ->
        ServiceDetailSheet(
            service = svc,
            viewModel = viewModel,
            serverId = serverId,
            onDismiss = { selectedService = null }
        )
    }
}

@Composable
private fun ServiceRow(service: ServiceInfo, onClick: () -> Unit) {
    val statusText: String
    val statusColor: androidx.compose.ui.graphics.Color

    when (service.state) {
        ServiceState.Running -> { statusText = "Running"; statusColor = StatusGreen }
        ServiceState.Failed -> { statusText = "Failed"; statusColor = StatusRed }
        ServiceState.Stopped -> { statusText = "Stopped"; statusColor = MaterialTheme.colorScheme.onSurfaceVariant }
        is ServiceState.Other -> { statusText = service.state.raw; statusColor = MaterialTheme.colorScheme.onSurfaceVariant }
    }

    Surface(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(service.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (service.description.isNotBlank()) {
                    Text(service.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            StatusChip(statusText, statusColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceDetailSheet(
    service: ServiceInfo,
    viewModel: AppViewModel,
    serverId: String,
    onDismiss: () -> Unit
) {
    var actionHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showSudoDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ServiceAction?>(null) }

    fun addResult(result: String) {
        actionHistory = (listOf(result) + actionHistory).take(10)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(service.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Status details
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow("Load", service.loadState)
                    DetailRow("Active", service.activeState)
                    DetailRow("Sub", service.subState)
                    if (service.description.isNotBlank()) {
                        DetailRow("Description", service.description)
                    }
                }
            }

            Text("Quick Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ServiceActionButton("Start", ServiceAction.START, serverId, service.name, viewModel,
                    Modifier.weight(1f), onSudoNeeded = { pendingAction = ServiceAction.START; showSudoDialog = true }) { isLoading = it.first; it.second?.let { r -> addResult("▸ start: $r") } }
                ServiceActionButton("Stop", ServiceAction.STOP, serverId, service.name, viewModel,
                    Modifier.weight(1f), isDestructive = true, onSudoNeeded = { pendingAction = ServiceAction.STOP; showSudoDialog = true }) { isLoading = it.first; it.second?.let { r -> addResult("▸ stop: $r") } }
                ServiceActionButton("Restart", ServiceAction.RESTART, serverId, service.name, viewModel,
                    Modifier.weight(1f), onSudoNeeded = { pendingAction = ServiceAction.RESTART; showSudoDialog = true }) { isLoading = it.first; it.second?.let { r -> addResult("▸ restart: $r") } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ServiceActionButton("Enable", ServiceAction.ENABLE, serverId, service.name, viewModel,
                    Modifier.weight(1f), onSudoNeeded = { pendingAction = ServiceAction.ENABLE; showSudoDialog = true }) { isLoading = it.first; it.second?.let { r -> addResult("▸ enable: $r") } }
                ServiceActionButton("Disable", ServiceAction.DISABLE, serverId, service.name, viewModel,
                    Modifier.weight(1f), onSudoNeeded = { pendingAction = ServiceAction.DISABLE; showSudoDialog = true }) { isLoading = it.first; it.second?.let { r -> addResult("▸ disable: $r") } }
                ServiceActionButton("Status", ServiceAction.STATUS, serverId, service.name, viewModel,
                    Modifier.weight(1f)) { isLoading = it.first; it.second?.let { r -> addResult("▸ status: $r") } }
            }

            // Action history
            if (actionHistory.isNotEmpty()) {
                Text("Action History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        actionHistory.forEach { entry ->
                            Text(
                                entry,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Sudo password dialog
    if (showSudoDialog) {
        SudoPasswordDialog(
            onConfirm = { password ->
                showSudoDialog = false
                viewModel.setSudoPassword(serverId, password)
                pendingAction?.let { action ->
                    isLoading = true
                    viewModel.manageServiceWithSudo(serverId, service.name, action, password) { result ->
                        result.onSuccess { addResult("▸ ${action.name.lowercase()}: ${it.ifBlank { "Done" }}"); isLoading = false }
                            .onFailure { addResult("▸ ${action.name.lowercase()}: Error: ${it.message}"); isLoading = false }
                    }
                }
                pendingAction = null
            },
            onDismiss = { showSudoDialog = false; pendingAction = null }
        )
    }
}

@Composable
private fun SudoPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sudo Password Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This action requires elevated privileges. Enter the sudo password for this server.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ServiceActionButton(
    label: String,
    action: ServiceAction,
    serverId: String,
    serviceName: String,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    onSudoNeeded: (() -> Unit)? = null,
    onResult: (Pair<Boolean, String?>) -> Unit
) {
    val containerColor = if (isDestructive)
        StatusRed.copy(alpha = 0.12f)
    else
        MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (isDestructive) StatusRed else MaterialTheme.colorScheme.onSurface

    Button(
        onClick = {
            if (action.requiresSudo && !viewModel.hasSudoPassword(serverId) && onSudoNeeded != null) {
                onSudoNeeded()
            } else {
                onResult(true to null)
                viewModel.manageService(serverId, serviceName, action) { result ->
                    result.onSuccess { onResult(false to it.ifBlank { "Done" }) }
                        .onFailure { onResult(false to "Error: ${it.message}") }
                }
            }
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SudoSetupDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Up Sudo Access") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the sudo password for this server. It will be saved securely and used automatically.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Sudo Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
