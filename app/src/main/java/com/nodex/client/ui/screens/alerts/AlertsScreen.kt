package com.nodex.client.ui.screens.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.domain.model.*
import com.nodex.client.ui.components.*
import com.nodex.client.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

import com.nodex.client.ui.screens.overview.NoServerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(viewModel: AppViewModel, onNavigateToAddServer: () -> Unit = {}) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val alertsMap by viewModel.alerts.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val errors by viewModel.serverErrors.collectAsStateWithLifecycle()
    val lastUpdatedMap by viewModel.lastUpdated.collectAsStateWithLifecycle()

    if (servers.isEmpty()) {
        NoServerState(onNavigateToAddServer)
        return
    }

    val serverId = selectedId ?: return
    val alerts = alertsMap[serverId] ?: emptyList()
    val connectionState = connectionStates[serverId] ?: ConnectionState.Disconnected

    var searchText by remember { mutableStateOf("") }
    var selectedAlert by remember { mutableStateOf<AlertItem?>(null) }
    var showSudoPrompt by remember { mutableStateOf(false) }

    val activeAlerts = alerts.filter { !it.isResolved }
    val resolvedAlerts = alerts.filter { it.isResolved }

    val filteredAlerts = activeAlerts.filter { alert ->
        searchText.isBlank() ||
            alert.title.contains(searchText, ignoreCase = true) ||
            (alert.serviceName?.contains(searchText, ignoreCase = true) == true) ||
            alert.message.contains(searchText, ignoreCase = true)
    }.sortedByDescending { it.lastSeen }

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { viewModel.refreshNow(); delay(1000); isRefreshing = false }
    }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true }) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Failed services and critical system warnings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Error / connection status
        val errorMessage = errors[serverId]
        if (errorMessage != null) {
            ErrorBanner(errorMessage, onRetry = { viewModel.refreshNow() })
        } else {
            ConnectionBanner(connectionState)
        }
        LastUpdatedText(lastUpdatedMap[serverId])

        // Journal access info
        val hasSudo = viewModel.hasSudoPassword(serverId)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasSudo) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Journal Access",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (hasSudo) "Enhanced — using sudo for full system logs"
                        else "Limited — sudo access needed for full system logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasSudo) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                if (!hasSudo) {
                    TextButton(onClick = { showSudoPrompt = true }) {
                        Text("Set Up")
                    }
                }
                Icon(
                    imageVector = if (hasSudo) Icons.Default.VerifiedUser else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (hasSudo) StatusGreen else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search alerts") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Summary
        SectionHeader("Alerts ${activeAlerts.size} active · ${resolvedAlerts.size} resolved")

        if (filteredAlerts.isEmpty()) {
            EmptyState("No active alerts", "Everything looks healthy right now.")
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    filteredAlerts.forEachIndexed { index, alert ->
                        AlertRow(alert) { selectedAlert = alert }
                        if (index < filteredAlerts.size - 1) {
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
        com.nodex.client.ui.screens.services.SudoSetupDialog(
            onConfirm = { password ->
                viewModel.setSudoPassword(serverId, password)
                showSudoPrompt = false
            },
            onDismiss = { showSudoPrompt = false }
        )
    }

    selectedAlert?.let { alert ->
        AlertDetailSheet(alert = alert, onDismiss = { selectedAlert = null })
    }
}

@Composable
private fun AlertRow(alert: AlertItem, onClick: () -> Unit) {
    val (iconColor) = when (alert.severity) {
        AlertSeverity.CRITICAL -> StatusRed
        AlertSeverity.WARNING -> StatusYellow
        AlertSeverity.INFO -> NodexBlue
    } to Unit

    Surface(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = iconColor,
                modifier = Modifier.size(10.dp)
            ) {}
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    buildString {
                        append(alert.serviceName ?: "System")
                        append(" · ")
                        append(formatDate(alert.lastSeen))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(
                alert.severity.name.lowercase().replaceFirstChar { it.uppercase() },
                iconColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertDetailSheet(alert: AlertItem, onDismiss: () -> Unit) {
    var showRawLog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(alert.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val severityColor = when (alert.severity) {
                    AlertSeverity.CRITICAL -> StatusRed
                    AlertSeverity.WARNING -> StatusYellow
                    AlertSeverity.INFO -> NodexBlue
                }
                StatusChip(alert.severity.name.lowercase().replaceFirstChar { it.uppercase() }, severityColor)
                StatusChip(alert.category.name.lowercase().replaceFirstChar { it.uppercase() }, NodexBlue)
            }

            // Status section
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow("Service", alert.serviceName ?: "System")
                    DetailRow("First Seen", formatDate(alert.firstSeen))
                    DetailRow("Last Seen", formatDate(alert.lastSeen))
                    DetailRow("Occurrences", alert.occurrenceCount.toString())
                }
            }

            // Related logs
            if (alert.relatedLogs.isNotEmpty()) {
                Text("Recent Log Lines", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        alert.relatedLogs.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Raw log toggle
            if (alert.rawLog.isNotBlank()) {
                TextButton(onClick = { showRawLog = !showRawLog }) {
                    Text(if (showRawLog) "Hide Raw Log" else "Show Raw Log")
                }
                if (showRawLog) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text(
                            alert.rawLog,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.US)
    return sdf.format(Date(timestamp))
}
