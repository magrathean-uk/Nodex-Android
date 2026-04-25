package com.nodex.client.ui.screens.overview

import com.nodex.client.domain.model.ConnectionState

object OverviewFreshness {
    fun freshnessWindowMs(pollIntervalSeconds: Int): Long {
        return pollIntervalSeconds.coerceAtLeast(2) * 4_000L
    }

    fun isStale(
        lastUpdatedMs: Long?,
        nowMs: Long,
        pollIntervalSeconds: Int,
        connectionState: ConnectionState
    ): Boolean {
        return lastUpdatedMs != null &&
            connectionState is ConnectionState.Connected &&
            nowMs - lastUpdatedMs > freshnessWindowMs(pollIntervalSeconds)
    }
}
