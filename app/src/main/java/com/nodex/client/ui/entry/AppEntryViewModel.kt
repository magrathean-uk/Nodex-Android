package com.nodex.client.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodex.client.core.demo.DemoModeManager
import com.nodex.client.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class EntryDestination {
    Onboarding,
    Main
}

data class AppEntryState(
    val themePreference: UserPreferences.Theme = UserPreferences.Theme.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val isDemoMode: Boolean = false
) {
    val destination: EntryDestination
        get() = when {
            !onboardingCompleted -> EntryDestination.Onboarding
            else -> EntryDestination.Main
        }
}

@HiltViewModel
class AppEntryViewModel @Inject constructor(
    userPreferences: UserPreferences,
    demoModeManager: DemoModeManager
) : ViewModel() {

    val state: StateFlow<AppEntryState> = combine(
        userPreferences.theme,
        userPreferences.onboardingCompleted,
        demoModeManager.isDemoMode
    ) { theme, onboardingCompleted, isDemoMode ->
        AppEntryState(
            themePreference = theme,
            onboardingCompleted = onboardingCompleted,
            isDemoMode = isDemoMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppEntryState()
    )
}
