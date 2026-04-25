package com.nodex.client.ui.screens.overview

import com.nodex.client.domain.model.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewFreshnessTest {

    @Test
    fun `connected screen becomes stale after four poll windows`() {
        assertTrue(
            OverviewFreshness.isStale(
                lastUpdatedMs = 10_000L,
                nowMs = 19_000L,
                pollIntervalSeconds = 2,
                connectionState = ConnectionState.Connected
            )
        )
    }

    @Test
    fun `disconnected screen does not show stale warning`() {
        assertFalse(
            OverviewFreshness.isStale(
                lastUpdatedMs = 10_000L,
                nowMs = 40_000L,
                pollIntervalSeconds = 2,
                connectionState = ConnectionState.Disconnected
            )
        )
    }
}
