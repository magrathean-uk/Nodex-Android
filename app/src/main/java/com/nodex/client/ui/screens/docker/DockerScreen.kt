package com.nodex.client.ui.screens.docker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nodex.client.domain.model.DockerContainer
import com.nodex.client.domain.model.DockerContainerStats
import com.nodex.client.domain.model.DockerContainerStatus
import com.nodex.client.ui.components.EmptyState
import com.nodex.client.ui.components.ErrorBanner
import com.nodex.client.ui.components.SectionHeader
import com.nodex.client.ui.components.StatusChip
import com.nodex.client.ui.components.StatusGreen
import com.nodex.client.ui.components.StatusRed
import com.nodex.client.ui.components.StatusYellow
import com.nodex.client.ui.components.formatBytes
import com.nodex.client.ui.components.formatPercent
import com.nodex.client.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DockerFilter { All, Running, Stopped }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockerScreen(
    viewModel: AppViewModel,
    onNavigateToAddServer: () -> Unit = {}
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val dockerContainers by viewModel.dockerContainers.collectAsStateWithLifecycle()
    val dockerStats by viewModel.dockerStats.collectAsStateWithLifecycle()
    val dockerErrors by viewModel.dockerErrors.collectAsStateWithLifecycle()
    val dockerRefreshing by viewModel.dockerRefreshing.collectAsStateWithLifecycle()

    if (servers.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyState("No Servers", "Add a server to inspect Docker or Podman workloads.")
            Button(onClick = onNavigateToAddServer) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Server")
            }
        }
        return
    }

    val serverId = selectedId ?: return
    val capabilities = viewModel.getCapabilities(serverId)
    val hasStoredSudoPassword = viewModel.hasSudoPassword(serverId)
    val containers = dockerContainers[serverId] ?: emptyList()
    val stats = dockerStats[serverId] ?: emptyMap()
    val error = dockerErrors[serverId]
    val isRefreshing = dockerRefreshing[serverId] == true

    var selectedContainer by remember(serverId) { mutableStateOf<DockerContainer?>(null) }
    var logsTarget by remember(serverId) { mutableStateOf<DockerContainer?>(null) }
    var logsText by remember(serverId) { mutableStateOf("") }
    var logsLoading by remember(serverId) { mutableStateOf(false) }
    var actionMessage by remember(serverId) { mutableStateOf<String?>(null) }
    var searchText by remember(serverId) { mutableStateOf("") }
    var filter by remember(serverId) { mutableStateOf(DockerFilter.All) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val filteredContainers = containers.filter { container ->
        val matchesFilter = when (filter) {
            DockerFilter.All -> true
            DockerFilter.Running -> container.isRunning
            DockerFilter.Stopped -> !container.isRunning
        }
        val matchesSearch = if (searchText.isBlank()) {
            true
        } else {
            listOfNotNull(
                container.name,
                container.image,
                container.composeProject,
                container.composeService,
                container.statusString,
                container.status.displayName
            ).joinToString(" ").contains(searchText, ignoreCase = true)
        }
        matchesFilter && matchesSearch
    }

    LaunchedEffect(serverId, capabilities?.hasDocker, lifecycleOwner) {
        if (capabilities?.hasDocker != true) return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshDockerNow(serverId)
            while (true) {
                delay(10_000)
                viewModel.refreshDockerNow(serverId)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshDockerNow(serverId) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RuntimeCard(
                    runtimeLabel = capabilities?.containerRuntimeLabel,
                    needsSudo = capabilities?.dockerNeedsSudo == true,
                    hasStoredSudoPassword = hasStoredSudoPassword
                )
            }

            if (capabilities?.hasDocker != true) {
                item {
                    EmptyState(
                        title = "Docker Not Detected",
                        subtitle = "Docker or Podman was not detected on the selected server."
                    )
                }
            } else {
                if (error != null) {
                    item {
                        ErrorBanner(
                            message = error,
                            onRetry = { viewModel.refreshDockerNow(serverId) }
                        )
                    }
                }

                if (error == null && containers.isNotEmpty()) {
                    item("filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                label = { Text("Search containers") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                DockerFilter.entries.forEachIndexed { index, option ->
                                    SegmentedButton(
                                        selected = filter == option,
                                        onClick = { filter = option },
                                        shape = SegmentedButtonDefaults.itemShape(index, DockerFilter.entries.size)
                                    ) {
                                        Text(option.name)
                                    }
                                }
                            }
                        }
                    }
                }

                if (error == null && filteredContainers.isEmpty() && !isRefreshing) {
                    item {
                        EmptyState(
                            title = if (containers.isEmpty()) "No Containers" else "No Matching Containers",
                            subtitle = if (containers.isEmpty()) {
                                "No running or stopped containers were found on this host."
                            } else {
                                "Try a different search or filter."
                            }
                        )
                    }
                } else {
                    val grouped = filteredContainers.groupBy { it.composeProject }
                    val projects = grouped.keys.filterNotNull().sorted()
                    projects.forEach { project ->
                        val projectContainers = grouped[project].orEmpty()
                        item(key = "project-$project") {
                            ContainerSection(
                                title = project,
                                containers = projectContainers,
                                stats = stats,
                                onSelect = { selectedContainer = it }
                            )
                        }
                    }

                    val standalone = grouped[null].orEmpty()
                    if (standalone.isNotEmpty()) {
                        item(key = "standalone") {
                            ContainerSection(
                                title = if (projects.isEmpty()) "Containers" else "Standalone",
                                containers = standalone,
                                stats = stats,
                                onSelect = { selectedContainer = it }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    selectedContainer?.let { container ->
        ModalBottomSheet(onDismissRequest = { selectedContainer = null }) {
            ContainerDetailSheet(
                container = container,
                stats = stats[container.id] ?: stats[container.shortID],
                actionMessage = actionMessage,
                onDismiss = { selectedContainer = null },
                onLogs = {
                    logsTarget = container
                    logsLoading = true
                    logsText = ""
                    scope.launch {
                        logsText = viewModel.fetchDockerLogs(serverId, container.id)
                        logsLoading = false
                    }
                },
                onAction = { action ->
                    actionMessage = null
                    viewModel.performDockerAction(serverId, container.id, action) { result ->
                        actionMessage = result.exceptionOrNull()?.message
                    }
                }
            )
        }
    }

    logsTarget?.let { container ->
        AlertDialog(
            onDismissRequest = { logsTarget = null },
            title = { Text("${container.name} logs") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (logsLoading) {
                        Text("Loading…")
                    } else {
                        Text(
                            if (logsText.isBlank()) "No log output" else logsText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { logsTarget = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun RuntimeCard(runtimeLabel: String?, needsSudo: Boolean, hasStoredSudoPassword: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(runtimeLabel ?: "Container Runtime", StatusGreen)
            if (needsSudo) {
                StatusChip("sudo required", StatusYellow)
            }
            Text(
                if (needsSudo && !hasStoredSudoPassword) {
                    "Save a sudo password in Settings if the container runtime is root-owned."
                } else {
                    "Grouped by Compose project when labels are present."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContainerSection(
    title: String,
    containers: List<DockerContainer>,
    stats: Map<String, DockerContainerStats>,
    onSelect: (DockerContainer) -> Unit
) {
    val runningCount = containers.count { it.isRunning }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title)
            StatusChip("${runningCount}/${containers.size}", if (runningCount == containers.size) StatusGreen else StatusYellow)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                containers.forEachIndexed { index, container ->
                    ContainerRow(
                        container = container,
                        stats = stats[container.id] ?: stats[container.shortID],
                        onClick = { onSelect(container) }
                    )
                    if (index < containers.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerRow(
    container: DockerContainer,
    stats: DockerContainerStats?,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    container.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    container.image,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    container.statusString.ifBlank { container.status.displayName },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(container.status)
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                stats?.let {
                    Text(
                        formatPercent(it.cpuPercent),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${formatBytes(it.memUsageBytes)} · ${it.pids} PID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text(
                    container.shortID,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContainerDetailSheet(
    container: DockerContainer,
    stats: DockerContainerStats?,
    actionMessage: String?,
    onDismiss: () -> Unit,
    onLogs: () -> Unit,
    onAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            container.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            container.image,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(container.status.displayName, statusColor(container.status))
            if (container.composeProject != null) {
                StatusChip(container.composeProject, StatusGreen)
            }
        }

        InfoRow("Container ID", container.id)
        if (container.command.isNotBlank()) InfoRow("Command", container.command)
        if (container.ports.isNotEmpty()) InfoRow("Ports", container.ports.joinToString(", "))
        if (container.createdAt.isNotBlank()) InfoRow("Created", container.createdAt)

        stats?.let {
            HorizontalDivider()
            Text("Resource Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            InfoRow("CPU", formatPercent(it.cpuPercent))
            InfoRow("Memory", "${formatBytes(it.memUsageBytes)} / ${formatBytes(it.memLimitBytes)}")
            InfoRow("Network", "${formatBytes(it.netRxBytes)} rx · ${formatBytes(it.netTxBytes)} tx")
            InfoRow("Block I/O", "${formatBytes(it.blockReadBytes)} rd · ${formatBytes(it.blockWriteBytes)} wr")
            InfoRow("PIDs", it.pids.toString())
        }

        actionMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onAction(if (container.isRunning) "stop" else "start") }, modifier = Modifier.weight(1f)) {
                Text(if (container.isRunning) "Stop" else "Start")
            }
            OutlinedButton(onClick = { onAction("restart") }, modifier = Modifier.weight(1f)) {
                Text("Restart")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onLogs, modifier = Modifier.weight(1f)) {
                Text("Logs")
            }
            OutlinedButton(
                onClick = { onAction("remove") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Remove")
            }
        }

        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Done")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun statusColor(status: DockerContainerStatus) = when (status) {
    DockerContainerStatus.RUNNING -> StatusGreen
    DockerContainerStatus.EXITED, DockerContainerStatus.DEAD, DockerContainerStatus.REMOVING -> StatusRed
    DockerContainerStatus.PAUSED, DockerContainerStatus.RESTARTING -> StatusYellow
    DockerContainerStatus.CREATED, DockerContainerStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
