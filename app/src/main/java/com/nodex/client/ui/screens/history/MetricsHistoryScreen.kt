package com.nodex.client.ui.screens.history

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.ui.components.*
import com.nodex.client.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsHistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val serverId = selectedId ?: run { onBack(); return }

    val records by viewModel.getMetricHistory(serverId).collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }

    NodexDetailScaffold(
        title = "Metrics History",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                scope.launch {
                    val csv = viewModel.exportMetricsCsv(serverId)
                    val file = File(context.cacheDir, "nodex_metrics_${serverId.take(8)}.csv")
                    file.writeText(csv)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Metrics"))
                }
            }) {
                Icon(Icons.Default.Share, contentDescription = "Export CSV")
            }
            IconButton(onClick = { showClearDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear history")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (records.isEmpty()) {
                EmptyState("No History", "Metrics will appear here after the server is monitored for a while.")
            } else {
                val sorted = records.sortedBy { it.timestamp }
                Text(
                    "${sorted.size} data points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionHeader("CPU Usage")
                MetricChart(
                    data = sorted.map { it.cpuUsage.toFloat() },
                    timestamps = sorted.map { it.timestamp },
                    color = NodexBlue,
                    maxValue = 100f,
                    unit = "%"
                )

                SectionHeader("Memory Usage")
                MetricChart(
                    data = sorted.map { it.memoryUsage.toFloat() },
                    timestamps = sorted.map { it.timestamp },
                    color = StatusGreen,
                    maxValue = 100f,
                    unit = "%"
                )

                SectionHeader("Disk Usage")
                MetricChart(
                    data = sorted.map { it.diskUsage.toFloat() },
                    timestamps = sorted.map { it.timestamp },
                    color = StatusYellow,
                    maxValue = 100f,
                    unit = "%"
                )

                sorted.firstOrNull { it.cpuTemperature != null }?.let {
                    SectionHeader("CPU Temperature")
                    MetricChart(
                        data = sorted.map { r -> (r.cpuTemperature ?: 0.0).toFloat() },
                        timestamps = sorted.map { r -> r.timestamp },
                        color = StatusRed,
                        maxValue = 100f,
                        unit = "°C"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Delete all recorded metrics for this server? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.clearMetricHistory(serverId) }
                    showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MetricChart(
    data: List<Float>,
    timestamps: List<Long>,
    color: Color,
    maxValue: Float,
    unit: String
) {
    if (data.size < 2) {
        Text("Not enough data", style = MaterialTheme.typography.bodySmall)
        return
    }

    val latest = data.last()
    val avg = data.average().toFloat()
    val peak = data.max()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartLabel("Current", String.format(Locale.US, "%.1f%s", latest, unit))
                ChartLabel("Average", String.format(Locale.US, "%.1f%s", avg, unit))
                ChartLabel("Peak", String.format(Locale.US, "%.1f%s", peak, unit))
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val w = size.width
                val h = size.height
                val padding = 4f
                val chartW = w - padding * 2
                val chartH = h - padding * 2

                val effectiveMax = maxValue.coerceAtLeast(peak * 1.1f)
                val path = Path()
                data.forEachIndexed { i, value ->
                    val x = padding + (i.toFloat() / (data.size - 1)) * chartW
                    val y = padding + chartH - (value / effectiveMax) * chartH
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))

                drawLine(
                    color = color.copy(alpha = 0.2f),
                    start = Offset(padding, padding + chartH),
                    end = Offset(padding + chartW, padding + chartH),
                    strokeWidth = 1f
                )
            }

            if (timestamps.size >= 2) {
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt.format(Date(timestamps.first())), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt.format(Date(timestamps.last())), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChartLabel(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
