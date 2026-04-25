package com.nodex.client.domain.model

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

enum class ConnectionQuality(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
    UNKNOWN("Unknown");

    companion object {
        fun fromLatencyMs(ms: Long): ConnectionQuality = when {
            ms < 50 -> EXCELLENT
            ms < 150 -> GOOD
            ms < 400 -> FAIR
            ms < Long.MAX_VALUE -> POOR
            else -> UNKNOWN
        }
    }
}
