package com.nodex.client.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Test

class AppEntryStateTest {

    @Test
    fun `first launch starts onboarding`() {
        val state = AppEntryState(onboardingCompleted = false)

        assertEquals(EntryDestination.Onboarding, state.destination)
    }

    @Test
    fun `completed onboarding reaches main app`() {
        val state = AppEntryState(onboardingCompleted = true)

        assertEquals(EntryDestination.Main, state.destination)
    }
}
