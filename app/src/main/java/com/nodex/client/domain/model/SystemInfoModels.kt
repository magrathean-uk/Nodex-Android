package com.nodex.client.domain.model

data class HardwareInfo(
    val hostname: String = "",
    val kernelVersion: String = "",
    val architecture: String = "",
    val cpuModel: String = "",
    val cpuCores: Int = 0,
    val cpuThreads: Int = 0,
    val osName: String = "",
    val osVersion: String = "",
    val osPrettyName: String = "",
    val bootTimestamp: Long = 0,
    val blockDevices: List<BlockDevice> = emptyList(),
    val activeSessions: List<UserSession> = emptyList(),
    val recentLogins: List<String> = emptyList()
)

data class BlockDevice(
    val name: String,
    val size: String,
    val model: String = "",
    val type: String = "",
    val rotational: Boolean = false,
    val transport: String = ""
)

data class UserSession(
    val user: String,
    val tty: String,
    val from: String = "",
    val loginTime: String = ""
)

data class InterfaceDetail(
    val name: String,
    val macAddress: String = "",
    val mtu: Int = 0,
    val operState: String = "",
    val ipv4: String? = null,
    val ipv6: String? = null,
    val speed: String = "",
    val isDefaultRoute: Boolean = false,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxErrors: Long = 0,
    val txErrors: Long = 0,
    val rxDropped: Long = 0,
    val txDropped: Long = 0,
    val rxBytesPerSec: Double = 0.0,
    val txBytesPerSec: Double = 0.0
)
