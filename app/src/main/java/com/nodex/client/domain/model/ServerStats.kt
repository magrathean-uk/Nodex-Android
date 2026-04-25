package com.nodex.client.domain.model

data class NetworkInterfaceSample(
    val name: String,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val rxBytesPerSec: Double = 0.0,
    val txBytesPerSec: Double = 0.0,
    val rxTotalBytes: Long = 0,
    val txTotalBytes: Long = 0,
    val isUp: Boolean = false,
    val isDefaultRoute: Boolean = false,
    val speed: String? = null
) {
    val displaySpeed: String
        get() {
            val s = speed?.toIntOrNull() ?: return "Unknown"
            return when {
                s <= 0 -> "Unknown"
                s >= 1000 -> "${s / 1000} Gbps"
                else -> "$s Mbps"
            }
        }
}

@Deprecated("Use OverviewMetrics instead", ReplaceWith("OverviewMetrics"))
data class ServerStats(
    val uptime: String = "",
    val loadAvg: String = "",
    val memoryUsage: Float = 0f,
    val cpuUsage: Float = 0f,
    val diskUsage: Float = 0f,
    val networkTx: String = "",
    val networkRx: String = "",
    val temperature: Float = 0f,
    val networkInterfaces: List<NetworkInterfaceSample> = emptyList()
)