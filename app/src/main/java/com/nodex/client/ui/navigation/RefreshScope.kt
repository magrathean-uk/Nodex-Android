package com.nodex.client.ui.navigation

enum class RefreshScope(
    val includesFastPoll: Boolean,
    val includesSlowPoll: Boolean,
    val includesDocker: Boolean
) {
    OVERVIEW(includesFastPoll = true, includesSlowPoll = false, includesDocker = false),
    NETWORK(includesFastPoll = true, includesSlowPoll = false, includesDocker = false),
    SERVICES(includesFastPoll = false, includesSlowPoll = true, includesDocker = false),
    ALERTS(includesFastPoll = false, includesSlowPoll = true, includesDocker = false),
    DOCKER(includesFastPoll = false, includesSlowPoll = false, includesDocker = true),
    ALL(includesFastPoll = true, includesSlowPoll = true, includesDocker = true);

    companion object {
        fun forRoute(route: String?): RefreshScope = when (route) {
            Screen.Overview.route -> OVERVIEW
            Screen.Network.route -> NETWORK
            Screen.Services.route -> SERVICES
            Screen.Alerts.route -> ALERTS
            Screen.Docker.route -> DOCKER
            Screen.Settings.route -> ALL
            else -> ALL
        }
    }
}
