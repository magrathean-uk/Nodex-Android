package com.nodex.client.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nodex.client.ui.viewmodel.AppViewModel

@Composable
fun NodexNavHost(startDestination: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val appViewModel: AppViewModel = hiltViewModel()
    AppLifecycleBridge(appViewModel)

    val isDemoMode by appViewModel.isDemoMode.collectAsStateWithLifecycle(initialValue = false)
    val servers by appViewModel.servers.collectAsStateWithLifecycle()
    val selectedId by appViewModel.selectedServerId.collectAsStateWithLifecycle()
    val activeHostKeyMismatch by appViewModel
        .hostKeyPromptManagerForUi()
        .activeMismatch
        .collectAsStateWithLifecycle()

    NodexAppShell(
        navController = navController,
        currentDestination = currentDestination,
        isDemoMode = isDemoMode,
        servers = servers,
        selectedServerId = selectedId,
        onSelectServer = appViewModel::selectServer,
        onRefresh = {
            appViewModel.refreshNow(
                RefreshScope.forRoute(currentDestination?.route)
            )
        }
    ) { innerPadding ->
        NodexGraph(
            navController = navController,
            startDestination = startDestination,
            appViewModel = appViewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }

    HostKeyMismatchDialog(
        mismatch = activeHostKeyMismatch,
        onDismiss = { appViewModel.hostKeyPromptManagerForUi().clearMismatch() },
        onOpenTrustedHostKeys = {
            appViewModel.hostKeyPromptManagerForUi().clearMismatch()
            navController.navigate(Screen.HostKeys.route) { launchSingleTop = true }
        }
    )
}
