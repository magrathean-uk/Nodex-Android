package com.nodex.client.domain.model

enum class ServerPowerAction(val displayName: String, val command: String) {
    REBOOT("Reboot", "shutdown -r now"),
    SHUTDOWN("Shutdown", "shutdown -h +1"),
    POWEROFF("Power Off", "shutdown -h now");

    val isDestructive: Boolean = true
}

enum class ProcessSignal(val flag: String) {
    TERM("-15"),
    KILL("-9"),
    HUP("-1"),
    INT("-2")
}
