package com.nodex.client.ui.viewmodel

import java.util.concurrent.ConcurrentHashMap

class HostKeyRuntimeGate {
    private val pendingDecisions = ConcurrentHashMap.newKeySet<String>()
    private val rejectedServers = ConcurrentHashMap.newKeySet<String>()

    fun markPending(serverId: String): Boolean {
        rejectedServers.remove(serverId)
        return pendingDecisions.add(serverId)
    }

    fun markTrusted(serverId: String) {
        pendingDecisions.remove(serverId)
        rejectedServers.remove(serverId)
    }

    fun markRejected(serverId: String) {
        pendingDecisions.remove(serverId)
        rejectedServers.add(serverId)
    }

    fun clear(serverId: String) {
        pendingDecisions.remove(serverId)
        rejectedServers.remove(serverId)
    }

    fun blockedMessage(serverId: String): String? = when {
        pendingDecisions.contains(serverId) -> "Review the host key prompt to continue."
        rejectedServers.contains(serverId) -> "Host key rejected."
        else -> null
    }
}
