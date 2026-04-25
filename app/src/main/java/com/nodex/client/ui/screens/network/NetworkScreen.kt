package com.nodex.client.ui.screens.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.domain.model.*
import com.nodex.client.ui.components.*
import com.nodex.client.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.util.Locale

import com.nodex.client.ui.screens.overview.NoServerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(viewModel: AppViewModel, onNavigateToAddServer: () -> Unit = {}) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val netMap by viewModel.networkInterfaces.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val publicIPMap by viewModel.publicIP.collectAsStateWithLifecycle()
    val detailMap by viewModel.interfaceDetails.collectAsStateWithLifecycle()
    val errors by viewModel.serverErrors.collectAsStateWithLifecycle()
    val lastUpdatedMap by viewModel.lastUpdated.collectAsStateWithLifecycle()

    if (servers.isEmpty()) {
        NoServerState(onNavigateToAddServer)
        return
    }

    val serverId = selectedId ?: return
    val interfaces = netMap[serverId] ?: emptyList()
    val interfaceDetails = detailMap[serverId] ?: emptyList()
    val connectionState = connectionStates[serverId] ?: ConnectionState.Disconnected
    val publicIP = publicIPMap[serverId]
    val server = servers.find { it.id == serverId }

    var selectedInterface by remember { mutableStateOf<InterfaceDetail?>(null) }
    val sheetState = rememberModalBottomSheetState()

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
        // Error / connection status
        val errorMessage = errors[serverId]
        if (errorMessage != null) {
            ErrorBanner(errorMessage, onRetry = { viewModel.refreshNow() })
        } else {
            ConnectionBanner(connectionState)
        }

        // Last updated
        LastUpdatedText(lastUpdatedMap[serverId])

        // Public IP
        SectionHeader("Public IP")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("IPv4", style = MaterialTheme.typography.bodyMedium)
                val ipText = when {
                    publicIP != null -> publicIP
                    connectionState is ConnectionState.Connected -> "Fetching…"
                    else -> "—"
                }
                Text(
                    ipText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (publicIP != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Streaming indicator
        StreamingIndicator(connectionState)

        // Interfaces section
        SectionHeader("Interfaces")

        if (interfaces.isEmpty()) {
            EmptyState("No interfaces", "Waiting for network data")
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    val sorted = interfaces.sortedByDescending { it.isDefaultRoute }
                    sorted.forEachIndexed { index, iface ->
                        InterfaceRow(
                            iface = iface,
                            onClick = {
                                selectedInterface = interfaceDetails.find { it.name == iface.name }
                            }
                        )
                        if (index < sorted.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
    } // PullToRefreshBox

    // Interface detail bottom sheet
    selectedInterface?.let { detail ->
        ModalBottomSheet(
            onDismissRequest = { selectedInterface = null },
            sheetState = sheetState
        ) {
            InterfaceDetailSheet(detail)
        }
    }
}

@Composable
private fun InterfaceDetailSheet(detail: InterfaceDetail) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(detail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("Status", detail.operState.replaceFirstChar { it.uppercase() })
                detail.ipv4?.let { DetailRow("IPv4", it) }
                detail.ipv6?.let { DetailRow("IPv6", it) }
                if (detail.macAddress.isNotBlank()) DetailRow("MAC", detail.macAddress)
                if (detail.mtu > 0) DetailRow("MTU", detail.mtu.toString())
                if (detail.speed.isNotBlank()) DetailRow("Link Speed", detail.speed)
                if (detail.isDefaultRoute) DetailRow("Default Route", "Yes")
            }
        }

        SectionHeader("Traffic")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("RX Total", formatBytes(detail.rxBytes))
                DetailRow("TX Total", formatBytes(detail.txBytes))
                DetailRow("RX Rate", formatBitRate(detail.rxBytesPerSec))
                DetailRow("TX Rate", formatBitRate(detail.txBytesPerSec))
            }
        }

        if (detail.rxErrors > 0 || detail.txErrors > 0 || detail.rxDropped > 0 || detail.txDropped > 0) {
            SectionHeader("Errors & Drops")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (detail.rxErrors > 0) DetailRow("RX Errors", detail.rxErrors.toString())
                    if (detail.txErrors > 0) DetailRow("TX Errors", detail.txErrors.toString())
                    if (detail.rxDropped > 0) DetailRow("RX Dropped", detail.rxDropped.toString())
                    if (detail.txDropped > 0) DetailRow("TX Dropped", detail.txDropped.toString())
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StreamingIndicator(state: ConnectionState) {
    val (text, color) = when (state) {
        is ConnectionState.Connected -> "Streaming active" to StatusGreen
        else -> "Streaming paused" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = color,
            modifier = Modifier.size(8.dp)
        ) {}
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun InterfaceRow(iface: NetworkInterfaceSample, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    iface.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (iface.isDefaultRoute) StatusChip("Default", NodexBlue)
            }
            iface.ipv4?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Text("No IPv4", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↑", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    formatBitRate(iface.txBytesPerSec),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (iface.txBytesPerSec > 1024) NodexBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    formatBitRate(iface.rxBytesPerSec),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (iface.rxBytesPerSec > 1024) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
