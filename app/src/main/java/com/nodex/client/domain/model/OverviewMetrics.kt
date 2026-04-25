package com.nodex.client.domain.model

data class OverviewMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    // CPU
    val cpuUsagePercent: Double = 0.0,
    val cpuCores: List<CpuCoreLoad> = emptyList(),
    val cpuTemperature: TemperatureReading? = null,
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    // Memory
    val memUsedBytes: Long = 0,
    val memTotalBytes: Long = 0,
    val memFreeBytes: Long = 0,
    val memAvailableBytes: Long? = null,
    val memBuffersBytes: Long = 0,
    val memCachedBytes: Long = 0,
    // Swap
    val swapUsedBytes: Long = 0,
    val swapTotalBytes: Long = 0,
    // Root filesystem
    val rootUsedBytes: Long = 0,
    val rootTotalBytes: Long = 0,
    // Other filesystems
    val volumes: List<DiskVolume> = emptyList(),
    // Processes / uptime
    val processCount: Int = 0,
    val uptimeSeconds: Long = 0
) {
    val memUsagePercent: Double
        get() = if (memTotalBytes > 0) (memUsedBytes.toDouble() / memTotalBytes) * 100.0 else 0.0

    val swapUsagePercent: Double
        get() = if (swapTotalBytes > 0) (swapUsedBytes.toDouble() / swapTotalBytes) * 100.0 else 0.0

    val rootUsagePercent: Double
        get() = if (rootTotalBytes > 0) (rootUsedBytes.toDouble() / rootTotalBytes) * 100.0 else 0.0
}

data class CpuCoreLoad(
    val id: Int,
    val usagePercent: Double
)

data class TemperatureReading(
    val id: String,
    val sensorName: String,
    val label: String,
    val currentCelsius: Double,
    val highCelsius: Double? = null,
    val criticalCelsius: Double? = null
) {
    val status: TemperatureStatus
        get() = when {
            criticalCelsius != null && currentCelsius >= criticalCelsius -> TemperatureStatus.CRITICAL
            highCelsius != null && currentCelsius >= highCelsius -> TemperatureStatus.WARNING
            currentCelsius >= 80.0 -> TemperatureStatus.WARNING
            else -> TemperatureStatus.NORMAL
        }
}

enum class TemperatureStatus { NORMAL, WARNING, CRITICAL }

data class DiskVolume(
    val mountPoint: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val fsType: String? = null
) {
    val usagePercent: Double
        get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes) * 100.0 else 0.0
}
