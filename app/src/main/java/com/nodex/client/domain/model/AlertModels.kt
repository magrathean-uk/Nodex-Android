package com.nodex.client.domain.model

import java.util.UUID

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class AlertCategory {
    CPU, MEMORY, DISK, SWAP, NETWORK, SERVICE, SYSTEM, OTHER
}

data class LogEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val category: AlertCategory,
    val severity: AlertSeverity,
    val message: String,
    val sourceService: String? = null,
    val raw: String? = null
)

data class AlertItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val serviceName: String? = null,
    val severity: AlertSeverity,
    val category: AlertCategory,
    val timestamp: Long,
    val message: String,
    val rawLog: String = "",
    val currentServiceState: ServiceState = ServiceState.Other("unknown"),
    val firstSeen: Long = timestamp,
    val lastSeen: Long = timestamp,
    val occurrenceCount: Int = 1,
    val relatedLogs: List<String> = emptyList()
) {
    val isResolved: Boolean
        get() = when (currentServiceState) {
            is ServiceState.Running, is ServiceState.Stopped -> true
            is ServiceState.Other -> {
                val s = currentServiceState.raw.lowercase()
                s == "active" || s == "exited" || s == "inactive"
            }
            is ServiceState.Failed -> false
        }

    val statusLabel: String
        get() = when {
            currentServiceState is ServiceState.Failed -> "Failed"
            isResolved -> "Resolved"
            else -> "Active"
        }
}
