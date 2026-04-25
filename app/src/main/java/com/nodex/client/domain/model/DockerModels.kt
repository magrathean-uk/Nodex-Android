package com.nodex.client.domain.model

enum class DockerContainerStatus {
    RUNNING,
    EXITED,
    PAUSED,
    RESTARTING,
    CREATED,
    DEAD,
    REMOVING,
    UNKNOWN;

    val isRunning: Boolean
        get() = this == RUNNING

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class DockerContainer(
    val id: String,
    val shortID: String,
    val name: String,
    val image: String,
    val imageID: String,
    val status: DockerContainerStatus,
    val statusString: String,
    val ports: List<String> = emptyList(),
    val composeProject: String? = null,
    val composeService: String? = null,
    val createdAt: String = "",
    val command: String = ""
) {
    val isRunning: Boolean
        get() = status.isRunning
}

data class DockerContainerStats(
    val id: String,
    val name: String,
    val cpuPercent: Double,
    val memUsageBytes: Long,
    val memLimitBytes: Long,
    val memPercent: Double,
    val netRxBytes: Long,
    val netTxBytes: Long,
    val blockReadBytes: Long,
    val blockWriteBytes: Long,
    val pids: Int
)
