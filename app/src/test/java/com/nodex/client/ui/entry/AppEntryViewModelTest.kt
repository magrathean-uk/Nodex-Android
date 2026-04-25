package com.nodex.client.ui.entry

import com.nodex.client.core.demo.DemoModeManager
import com.nodex.client.data.prefs.UserPreferences
import com.nodex.client.ui.viewmodel.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `viewmodel combines theme onboarding and demo mode`() = runTest {
        val userPreferences = mockk<UserPreferences>()
        every { userPreferences.theme } returns MutableStateFlow(UserPreferences.Theme.DARK)
        every { userPreferences.onboardingCompleted } returns MutableStateFlow(true)

        val demoModeManager = mockk<DemoModeManager>()
        every { demoModeManager.isDemoMode } returns MutableStateFlow(true)

        val viewModel = AppEntryViewModel(
            userPreferences = userPreferences,
            demoModeManager = demoModeManager
        )

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { }
        }

        runCurrent()

        val state = viewModel.state.value
        assertEquals(UserPreferences.Theme.DARK, state.themePreference)
        assertTrue(state.isDemoMode)
        assertEquals(EntryDestination.Main, state.destination)

        job.cancel()
    }
}
