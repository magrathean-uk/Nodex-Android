package com.nodex.client.ui.screens.overview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Dns
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.domain.model.*
import com.nodex.client.ui.components.*
import com.nodex.client.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(viewModel: AppViewModel, onNavigateToAddServer: () -> Unit = {}, onNavigateToAlerts: () -> Unit = {}) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val overviewMap by viewModel.overview.collectAsStateWithLifecycle()
    val processMap by viewModel.processes.collectAsStateWithLifecycle()
    val alertsMap by viewModel.alerts.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val quality by viewModel.connectionQuality.collectAsStateWithLifecycle()
    val errors by viewModel.serverErrors.collectAsStateWithLifecycle()
    val hwInfoMap by viewModel.hardwareInfo.collectAsStateWithLifecycle()
    val lastUpdatedMap by viewModel.lastUpdated.collectAsStateWithLifecycle()

    if (servers.isEmpty()) {
        NoServerState(onNavigateToAddServer)
        return
    }

    val serverId = selectedId ?: return
    val overview = overviewMap[serverId]
    val connectionState = connectionStates[serverId] ?: ConnectionState.Disconnected
    val processes = processMap[serverId] ?: emptyList()
    val alerts = alertsMap[serverId] ?: emptyList()
    val server = servers.find { it.id == serverId }
    val hwInfo = hwInfoMap[serverId]
    val capabilities = viewModel.getCapabilities(serverId)
    val errorMessage = errors[serverId]
    val lastUpdatedMs = lastUpdatedMap[serverId]
    val nowMs by produceState(initialValue = System.currentTimeMillis(), key1 = serverId, key2 = lastUpdatedMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val isStale = OverviewFreshness.isStale(
        lastUpdatedMs = lastUpdatedMs,
        nowMs = nowMs,
        pollIntervalSeconds = server?.pollIntervalSeconds ?: 2,
        connectionState = connectionState
    )

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
        // Server name & hostname header
        server?.let { s ->
            Text(
                s.name.ifBlank { s.hostname },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${s.username}@${s.hostname}:${s.port}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Header chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                server?.distroFamily?.let { distro ->
                    StatusChip(distro.displayName, NodexBlue)
                }
                ConnectionChip(connectionState)
                QualityChip(quality)
            }
            LastUpdatedText(lastUpdatedMs)
        }

        FleetSnapshotSection(
            servers = servers,
            selectedId = serverId,
            overviewMap = overviewMap,
            connectionStates = connectionStates
        )

        // Error banner
        if (errorMessage != null) {
            ErrorBanner(errorMessage, onRetry = { viewModel.refreshNow() })
        }

        // Connection banner (only for connecting/disconnected, not errors — ErrorBanner handles those)
        if (errorMessage == null) {
            ConnectionBanner(connectionState, onRetry = { viewModel.refreshNow() })
        }

        if (isStale) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = StatusYellow)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Data is stale", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Last update ${formatRelativeAge(nowMs - (lastUpdatedMs ?: nowMs))} ago. Pull to refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AlertsSummarySection(alerts = alerts, onNavigateToAlerts = onNavigateToAlerts)

        // Initial sync overlay — shimmer skeletons
        if (overview == null && connectionState is ConnectionState.Connected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    MetricGaugeSkeleton(modifier = Modifier.weight(1f))
                }
            }
            repeat(3) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 60.dp)
            }
        }

        if (overview != null) {
            // Metrics grid: CPU, Memory, Disk, Swap
            MetricsGrid(overview)

            // Storage volumes
            StorageSection(overview)

            // Hardware info
            HardwareInfoSection(hwInfo)

            // Top processes
            ProcessSection(processes)

            // Active sessions
            ActiveSessionsSection(hwInfo)

            // Enhanced tools
            EnhancedToolsCard(capabilities, server?.distroFamily)

            // System info
            SystemInfoSection(overview, quality, hwInfo)
        } else if (connectionState is ConnectionState.Connected) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (connectionState is ConnectionState.Error) {
            EmptyState("Connection Error", connectionState.message)
        } else if (connectionState is ConnectionState.Disconnected) {
            EmptyState("Disconnected", "Pull down to reconnect")
        }
    }
    } // PullToRefreshBox
}

@Composable
private fun FleetSnapshotSection(
    servers: List<ServerConfig>,
    selectedId: String,
    overviewMap: Map<String, OverviewMetrics>,
    connectionStates: Map<String, ConnectionState>
) {
    val targetServers = servers.filter { it.isFavorite }.ifEmpty { servers }.take(4)
    val title = if (servers.any { it.isFavorite }) "Favorites Snapshot" else "Fleet Snapshot"

    SectionHeader(title)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            targetServers.forEach { server ->
                val overview = overviewMap[server.id]
                val connectionState = connectionStates[server.id]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                server.name.ifBlank { server.hostname },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (server.id == selectedId) FontWeight.SemiBold else FontWeight.Medium
                            )
                            if (server.id == selectedId) {
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusChip("Current", NodexBlue)
                            }
                        }
                        Text(
                            when {
                                overview != null ->
                                    "CPU ${overview.cpuUsagePercent.toInt()}% · Mem ${overview.memUsagePercent.toInt()}%"
                                connectionState is ConnectionState.Connected -> "Live"
                                connectionState is ConnectionState.Connecting -> "Connecting"
                                connectionState is ConnectionState.Error -> "Offline"
                                else -> "Waiting for data"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val indicatorColor = when (connectionState) {
                        is ConnectionState.Connected -> StatusGreen
                        is ConnectionState.Connecting -> StatusYellow
                        is ConnectionState.Error -> StatusRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = indicatorColor,
                        modifier = Modifier.size(10.dp)
                    ) {}
                }
            }
        }
    }
}


@Composable
private fun AlertsSummarySection(alerts: List<AlertItem>, onNavigateToAlerts: () -> Unit) {
    SectionHeader("Alerts")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (alerts.isEmpty()) {
                Text(
                    "No recent alerts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val critical = alerts.count { it.severity == AlertSeverity.CRITICAL }
                val warning = alerts.count { it.severity == AlertSeverity.WARNING }
                Text(
                    buildString {
                        append("${alerts.size} recent alert")
                        if (alerts.size != 1) append("s")
                        if (critical > 0 || warning > 0) {
                            append(" · ")
                            if (critical > 0) append("$critical critical")
                            if (critical > 0 && warning > 0) append(" · ")
                            if (warning > 0) append("$warning warning")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                alerts.take(3).forEach { alert ->
                    Text(
                        "• ${alert.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            OutlinedButton(onClick = onNavigateToAlerts, modifier = Modifier.fillMaxWidth()) {
                Text("Open Alerts")
            }
        }
    }
}

@Composable
private fun ConnectionChip(state: ConnectionState) {
    val (text, color) = when (state) {
        is ConnectionState.Connected -> "Live" to StatusGreen
        is ConnectionState.Connecting -> "Connecting" to StatusYellow
        is ConnectionState.Disconnected -> "Offline" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionState.Error -> "Error" to StatusRed
    }
    StatusChip(text, color)
}

@Composable
private fun QualityChip(quality: ConnectionQuality) {
    val (color) = when (quality) {
        ConnectionQuality.EXCELLENT, ConnectionQuality.GOOD -> StatusGreen
        ConnectionQuality.FAIR -> StatusYellow
        ConnectionQuality.POOR -> StatusRed
        ConnectionQuality.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    } to Unit
    if (quality != ConnectionQuality.UNKNOWN) {
        StatusChip(quality.name.lowercase().replaceFirstChar { it.uppercase() }, color)
    }
}

@Composable
private fun MetricsGrid(overview: OverviewMetrics) {
    val memPercent = percentOf(overview.memUsedBytes, overview.memTotalBytes)
    val diskPercent = percentOf(overview.rootUsedBytes, overview.rootTotalBytes)
    val swapPercent = if (overview.swapTotalBytes > 0)
        percentOf(overview.swapUsedBytes, overview.swapTotalBytes) else 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricGauge("CPU", overview.cpuUsagePercent, modifier = Modifier.weight(1f))
        MetricGauge("Memory", memPercent, modifier = Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricGauge("Disk", diskPercent, modifier = Modifier.weight(1f))
        MetricGauge(
            "Swap",
            if (overview.swapTotalBytes > 0) swapPercent else 0.0,
            subtitle = if (overview.swapTotalBytes == 0L) "None" else null,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StorageSection(overview: OverviewMetrics) {
    SectionHeader("Storage Volumes")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DiskBar("Root volume", overview.rootUsedBytes, overview.rootTotalBytes)
            overview.volumes.filter { it.mountPoint != "/" }.forEach { vol ->
                DiskBar(vol.mountPoint, vol.usedBytes, vol.totalBytes)
            }
        }
    }
}

@Composable
private fun ProcessSection(processes: List<ProcessInfo>) {
    SectionHeader("Top Processes")
    if (processes.isEmpty()) {
        EmptyState("No process data", "Process list may require sudo permissions")
    } else {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                processes.sortedByDescending { it.cpuPercent }.take(10).forEachIndexed { index, proc ->
                    ProcessRow(proc)
                    if (index < minOf(processes.size, 10) - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(process: ProcessInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                process.command,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "PID ${process.pid} · ${process.user}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                String.format(Locale.US, "%.1f%% CPU", process.cpuPercent),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = if (process.cpuPercent > 50) StatusYellow else MaterialTheme.colorScheme.onSurface
            )
            Text(
                String.format(Locale.US, "%.1f%% MEM", process.memPercent),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SystemInfoSection(overview: OverviewMetrics, quality: ConnectionQuality, hwInfo: HardwareInfo?) {
    SectionHeader("System Info")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hwInfo?.let { hw ->
                if (hw.osPrettyName.isNotBlank()) InfoRow("OS", hw.osPrettyName)
                if (hw.kernelVersion.isNotBlank()) InfoRow("Kernel", hw.kernelVersion)
                if (hw.architecture.isNotBlank()) InfoRow("Architecture", hw.architecture)
            }
            InfoRow("Uptime", formatUptime(overview.uptimeSeconds))
            InfoRow("Processes", overview.processCount.toString())
            InfoRow("Load Average", String.format(
                Locale.US, "%.2f / %.2f / %.2f", overview.load1, overview.load5, overview.load15
            ))
            overview.cpuTemperature?.let {
                InfoRow("CPU Temperature", String.format(Locale.US, "%.0f°C", it.currentCelsius))
            }
            InfoRow("Connection Quality", quality.name.lowercase().replaceFirstChar { c -> c.uppercase() })
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun HardwareInfoSection(hwInfo: HardwareInfo?) {
    if (hwInfo == null || hwInfo.cpuModel.isBlank()) return
    SectionHeader("Hardware")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("CPU", hwInfo.cpuModel)
            if (hwInfo.cpuCores > 0) InfoRow("Cores / Threads", "${hwInfo.cpuCores} / ${hwInfo.cpuThreads}")

            if (hwInfo.blockDevices.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Block Devices", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                hwInfo.blockDevices.forEach { dev ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "/dev/${dev.name}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            buildString {
                                append(dev.size)
                                if (dev.model.isNotBlank()) append(" · ${dev.model}")
                                if (dev.transport.isNotBlank()) append(" (${dev.transport})")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionsSection(hwInfo: HardwareInfo?) {
    val sessions = hwInfo?.activeSessions ?: return
    if (sessions.isEmpty()) return
    SectionHeader("Active Sessions")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sessions.forEach { session ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${session.user} (${session.tty})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        session.loginTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EnhancedToolsCard(capabilities: ServerCapabilities?, distroFamily: DistroFamily?) {
    if (capabilities == null) return
    val missing = mutableListOf<String>()
    if (!capabilities.hasSysstat) missing.add("sysstat")
    if (!capabilities.hasSensors) missing.add("lm-sensors")
    if (!capabilities.hasEthtool) missing.add("ethtool")

    if (missing.isEmpty()) return

    SectionHeader("Enhanced Tools")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Install optional tools for more detailed monitoring:",
                style = MaterialTheme.typography.bodySmall
            )
            val installCmd = installCommand(distroFamily, missing)
            if (installCmd.isNotBlank()) {
                Text(
                    installCmd,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                missing.forEach { tool ->
                    StatusChip(tool, StatusYellow)
                }
            }
        }
    }
}

private fun installCommand(distroFamily: DistroFamily?, tools: List<String>): String {
    if (distroFamily == null || tools.isEmpty()) return ""
    val pkgs = tools.joinToString(" ") { when (it) {
        "lm-sensors" -> if (distroFamily == DistroFamily.RHEL || distroFamily == DistroFamily.SUSE) "lm_sensors" else it
        else -> it
    }}
    return when (distroFamily) {
        DistroFamily.DEBIAN -> "sudo apt install -y $pkgs"
        DistroFamily.RHEL -> "sudo dnf install -y $pkgs"
        DistroFamily.ARCH -> "sudo pacman -S --noconfirm $pkgs"
        DistroFamily.SUSE -> "sudo zypper install -y $pkgs"
        else -> "# Install: $pkgs"
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
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
fun NoServerState(onAddServer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "No Servers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add a server to start monitoring.\nYou'll need SSH access (hostname, username, and password or key).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddServer) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Server")
        }
    }
}

private fun formatRelativeAge(ageMs: Long): String {
    val seconds = (ageMs / 1_000L).coerceAtLeast(0)
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h"
    return "${hours / 24}d"
}
