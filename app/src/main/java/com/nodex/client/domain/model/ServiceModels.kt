package com.nodex.client.domain.model

data class ServiceInfo(
    val id: String,
    val name: String,
    val description: String,
    val loadState: String,
    val activeState: String,
    val subState: String,
    val state: ServiceState
)

sealed class ServiceState {
    data object Running : ServiceState()
    data object Failed : ServiceState()
    data object Stopped : ServiceState()
    data class Other(val raw: String) : ServiceState()

    companion object {
        fun from(activeState: String, subState: String): ServiceState = when {
            activeState == "active" && subState == "running" -> Running
            activeState == "failed" -> Failed
            activeState == "inactive" -> Stopped
            else -> Other(activeState)
        }
    }
}

enum class ServiceAction(val command: String, val displayName: String, val isDestructive: Boolean) {
    START("start", "Start", false),
    STOP("stop", "Stop", true),
    RESTART("restart", "Restart", false),
    ENABLE("enable", "Enable", false),
    DISABLE("disable", "Disable", true),
    STATUS("status", "Status", false);

    val requiresSudo: Boolean get() = this != STATUS

    fun commandFor(serviceName: String): String {
        val sanitized = sanitize(serviceName)
        if (sanitized.isEmpty()) return "echo 'invalid service name'"
        return if (this == STATUS) "systemctl is-active $sanitized"
        else "systemctl $command $sanitized"
    }

    companion object {
        private val ALLOWED = Regex("[a-zA-Z0-9\\-_.@:]+")

        fun sanitize(name: String): String {
            val filtered = name.filter { c ->
                c.isLetterOrDigit() || c in "-_.@:"
            }
            return if (filtered.length == name.length) filtered else ""
        }
    }
}
