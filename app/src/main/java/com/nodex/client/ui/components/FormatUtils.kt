package com.nodex.client.ui.components

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    val digitGroup = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.size - 1)
    val value = bytes / 1024.0.pow(digitGroup.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroup])
}

fun formatBytesPerSec(bytesPerSec: Double): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    val digitGroup = (ln(bytesPerSec) / ln(1024.0)).toInt().coerceAtMost(units.size - 1)
    val value = bytesPerSec / 1024.0.pow(digitGroup.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroup])
}

fun formatBitRate(bytesPerSec: Double): String {
    val bitsPerSec = bytesPerSec * 8
    if (bitsPerSec <= 0) return "0 bps"
    val units = arrayOf("bps", "Kbps", "Mbps", "Gbps")
    val digitGroup = (ln(bitsPerSec) / ln(1000.0)).toInt().coerceAtMost(units.size - 1)
    val value = bitsPerSec / 1000.0.pow(digitGroup.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroup])
}

fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatPercent(value: Double): String {
    return String.format(Locale.US, "%.0f%%", value)
}

fun percentOf(used: Long, total: Long): Double {
    if (total <= 0) return 0.0
    return (used.toDouble() / total) * 100.0
}
