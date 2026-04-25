package com.nodex.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodex.client.data.prefs.UserPreferences
import com.nodex.client.ui.entry.AppEntryViewModel
import com.nodex.client.ui.entry.EntryDestination
import com.nodex.client.ui.navigation.HostKeyTrustDialog
import com.nodex.client.ui.navigation.NodexNavHost
import com.nodex.client.ui.screens.onboarding.OnboardingScreen
import com.nodex.client.ui.screens.security.HostKeyPromptViewModel
import com.nodex.client.ui.theme.NodexTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appEntryViewModel: AppEntryViewModel = hiltViewModel()
            val hostKeyPromptViewModel: HostKeyPromptViewModel = hiltViewModel()
            val entryState = appEntryViewModel.state.collectAsStateWithLifecycle().value
            val activeHostKeyPrompt = hostKeyPromptViewModel.activePrompt.collectAsStateWithLifecycle().value

            val useDarkTheme = when (entryState.themePreference) {
                UserPreferences.Theme.LIGHT -> false
                UserPreferences.Theme.DARK -> true
                UserPreferences.Theme.SYSTEM -> isSystemInDarkTheme()
            }

            NodexTheme(darkTheme = useDarkTheme) {
                when (entryState.destination) {
                    EntryDestination.Onboarding -> OnboardingScreen(onOnboardingFinished = {})
                    EntryDestination.Main -> {
                        NodexNavHost(startDestination = com.nodex.client.ui.navigation.Screen.Overview.route)
                    }
                }

                HostKeyTrustDialog(
                    prompt = activeHostKeyPrompt,
                    onTrust = { hostKeyPromptViewModel.trustActivePrompt() },
                    onReject = { hostKeyPromptViewModel.rejectActivePrompt() }
                )
            }
        }
    }
}
