package com.nodex.client.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MiscellaneousServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Overview : Screen("overview", "Overview", Icons.Default.Speed)
    data object Network : Screen("network", "Network", Icons.Default.Wifi)
    data object Services : Screen("services", "Services", Icons.Default.MiscellaneousServices)
    data object Docker : Screen("docker", "Docker", Icons.Default.Storage)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    data object Alerts : Screen("alerts", "Alerts", Icons.Default.Notifications)
    data object AddServer : Screen("add_server", "Add Server", Icons.Default.Speed)
    data object ServerDetail : Screen("server_detail/{serverId}", "Server Detail", Icons.Default.Settings) {
        fun route(serverId: String): String = "server_detail/$serverId"
    }
    data object History : Screen("history", "History", Icons.Default.Analytics)
    data object HostKeys : Screen("host_keys", "Trusted Host Keys", Icons.Default.Lock)
    data object SshKeys : Screen("ssh_keys", "SSH Key Library", Icons.Default.Lock)
}

val TopLevelScreens = listOf(
    Screen.Overview,
    Screen.Network,
    Screen.Services,
    Screen.Docker,
    Screen.Settings
)

val LabelScreens = TopLevelScreens + listOf(
    Screen.Alerts,
    Screen.History,
    Screen.HostKeys,
    Screen.SshKeys,
    Screen.ServerDetail,
    Screen.AddServer
)
