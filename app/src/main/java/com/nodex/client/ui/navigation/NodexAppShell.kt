package com.nodex.client.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.nodex.client.domain.model.ServerConfig
import com.nodex.client.ui.theme.NodexDemoOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodexAppShell(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isDemoMode: Boolean,
    servers: List<ServerConfig>,
    selectedServerId: String?,
    onSelectServer: (String) -> Unit,
    onRefresh: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    var showServerMenu by remember { mutableStateOf(false) }
    val showBottomBar = TopLevelScreens.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }
    val currentLabel = LabelScreens.find { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }?.label ?: ""

    Scaffold(
        topBar = {
            if (showBottomBar) {
                Column {
                    DemoModeBanner(visible = isDemoMode)
                    TopAppBar(
                        title = {
                            ServerSwitcherTitle(
                                currentLabel = currentLabel,
                                servers = servers,
                                selectedId = selectedServerId,
                                showServerMenu = showServerMenu,
                                onOpenMenu = { showServerMenu = true },
                                onDismissMenu = { showServerMenu = false },
                                onSelectServer = {
                                    onSelectServer(it)
                                    showServerMenu = false
                                }
                            )
                        },
                        actions = {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        content = content
    )
}

@Composable
private fun DemoModeBanner(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NodexDemoOrange)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "DEMO MODE — No real server connections",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ServerSwitcherTitle(
    currentLabel: String,
    servers: List<ServerConfig>,
    selectedId: String?,
    showServerMenu: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSelectServer: (String) -> Unit
) {
    if (servers.size > 1) {
        val favoriteServers = servers.filter { it.isFavorite }
        TextButton(onClick = onOpenMenu) {
            val selected = servers.find { it.id == selectedId }
            val displayName = selected?.run {
                if (name.isNotBlank()) "$name · $hostname" else hostname
            } ?: currentLabel
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            DropdownMenu(
                expanded = showServerMenu,
                onDismissRequest = onDismissMenu
            ) {
                if (favoriteServers.isNotEmpty()) {
                    ServerSwitcherSectionHeader("Favorites")
                    favoriteServers.forEach { server ->
                        ServerSwitcherMenuItem(server, selectedId, onSelectServer)
                    }
                    ServerSwitcherSectionHeader("All Servers")
                }
                servers.forEach { server ->
                    ServerSwitcherMenuItem(server, selectedId, onSelectServer)
                }
            }
        }
    } else {
        val selected = servers.find { it.id == selectedId }
        val hostname = selected?.hostname
        if (hostname != null) {
            Column {
                Text(currentLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    hostname,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(currentLabel)
        }
    }
}

@Composable
private fun ServerSwitcherSectionHeader(title: String) {
    DropdownMenuItem(
        text = {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = {},
        enabled = false
    )
}

@Composable
private fun ServerSwitcherMenuItem(
    server: ServerConfig,
    selectedId: String?,
    onSelectServer: (String) -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(server.name.ifBlank { server.hostname })
                Text(
                    server.hostname,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = { onSelectServer(server.id) },
        trailingIcon = {
            when {
                server.id == selectedId -> Text("✓", color = MaterialTheme.colorScheme.primary)
                server.isFavorite -> Text("★", color = NodexDemoOrange)
            }
        }
    )
}
