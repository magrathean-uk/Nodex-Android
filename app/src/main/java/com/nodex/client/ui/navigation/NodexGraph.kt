package com.nodex.client.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nodex.client.ui.screens.alerts.AlertsScreen
import com.nodex.client.ui.screens.docker.DockerScreen
import com.nodex.client.ui.screens.history.MetricsHistoryScreen
import com.nodex.client.ui.screens.network.NetworkScreen
import com.nodex.client.ui.screens.overview.OverviewScreen
import com.nodex.client.ui.screens.security.HostKeysScreen
import com.nodex.client.ui.screens.security.SshKeyLibraryScreen
import com.nodex.client.ui.screens.server.AddServerScreen
import com.nodex.client.ui.screens.services.ServicesScreen as ServicesScreenNew
import com.nodex.client.ui.screens.settings.ServerDetailScreen
import com.nodex.client.ui.screens.settings.SettingsScreen
import com.nodex.client.ui.viewmodel.AppViewModel

@Composable
fun NodexGraph(
    navController: NavHostController,
    startDestination: String,
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) }
    ) {
        val onNavigateToAddServer: () -> Unit = {
            navController.navigate(Screen.AddServer.route) { launchSingleTop = true }
        }
        composable(Screen.Overview.route) {
            OverviewScreen(
                viewModel = appViewModel,
                onNavigateToAddServer = onNavigateToAddServer,
                onNavigateToAlerts = {
                    navController.navigate(Screen.Alerts.route) { launchSingleTop = true }
                }
            )
        }
        composable(Screen.Network.route) {
            NetworkScreen(viewModel = appViewModel, onNavigateToAddServer = onNavigateToAddServer)
        }
        composable(Screen.Services.route) {
            ServicesScreenNew(
                viewModel = appViewModel,
                onNavigateToAddServer = onNavigateToAddServer
            )
        }
        composable(Screen.Docker.route) {
            DockerScreen(
                viewModel = appViewModel,
                onNavigateToAddServer = onNavigateToAddServer
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                appViewModel = appViewModel,
                onNavigateToAddServer = onNavigateToAddServer,
                onNavigateToServerDetail = { serverId ->
                    navController.navigate(Screen.ServerDetail.route(serverId)) { launchSingleTop = true }
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route) { launchSingleTop = true }
                },
                onNavigateToHostKeys = {
                    navController.navigate(Screen.HostKeys.route) { launchSingleTop = true }
                },
                onNavigateToSshKeyLibrary = {
                    navController.navigate(Screen.SshKeys.route) { launchSingleTop = true }
                }
            )
        }
        composable(
            Screen.Alerts.route,
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) },
            popExitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) }
        ) {
            AlertsScreen(viewModel = appViewModel, onNavigateToAddServer = onNavigateToAddServer)
        }
        composable(
            Screen.AddServer.route,
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) },
            popExitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) }
        ) {
            AddServerScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.ServerDetail.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) },
            popExitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) }
        ) { entry ->
            val serverId = entry.arguments?.getString("serverId") ?: return@composable
            ServerDetailScreen(
                serverId = serverId,
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Screen.History.route,
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) },
            popExitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) }
        ) {
            MetricsHistoryScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Screen.HostKeys.route,
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) },
            popExitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) }
        ) {
            HostKeysScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Screen.SshKeys.route,
            enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) },
            popExitTransition = { slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)) }
        ) {
            SshKeyLibraryScreen(onBack = { navController.popBackStack() })
        }
    }
}
