package com.nodex.client.domain.model

data class ServerCapabilities(
    val kernel: String? = null,
    val hasLsblk: Boolean = false,
    val hasLsblkJSON: Boolean = false,
    val hasIPJSON: Boolean = false,
    val hasSysstat: Boolean = false,
    val hasSensors: Boolean = false,
    val hasNvme: Boolean = false,
    val hasEthtool: Boolean = false,
    val hasDocker: Boolean = false,
    val dockerNeedsSudo: Boolean = false,
    val isPodman: Boolean = false
) {
    val containerRuntimeBinary: String?
        get() = when {
            !hasDocker -> null
            isPodman -> "podman"
            else -> "docker"
        }

    val containerRuntimeLabel: String
        get() = if (isPodman) "Podman" else "Docker"
}
