package com.nodex.client.core.demo

import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.NetworkInterfaceSample
import com.nodex.client.domain.model.ServerConfig
import com.nodex.client.domain.model.ServerStats

object DemoData {
    const val DEMO_SERVER_ID = "demo-server"

    val demoServer = ServerConfig(
        id = DEMO_SERVER_ID,
        name = "Demo Server",
        hostname = "demo.nodex.local",
        port = 22,
        username = "demo",
        authType = AuthType.NONE,
        distro = "Ubuntu",
        version = "22.04"
    )

    @Suppress("DEPRECATION")
    val demoStats = ServerStats(
        uptime = "12 days, 4 h",
        loadAvg = "0.12, 0.24, 0.31",
        memoryUsage = 0.42f,
        cpuUsage = 0.18f,
        diskUsage = 0.64f,
        networkTx = "120 KB/s",
        networkRx = "340 KB/s",
        temperature = 42.5f,
        networkInterfaces = listOf(
            NetworkInterfaceSample(
                name = "eth0",
                ipv4 = "192.168.1.42",
                ipv6 = "fe80::1",
                isUp = true,
                rxTotalBytes = 125_000_000,
                txTotalBytes = 98_000_000,
                rxBytesPerSec = 340_000.0,
                txBytesPerSec = 120_000.0
            ),
            NetworkInterfaceSample(
                name = "wlan0",
                ipv4 = "192.168.1.66",
                isUp = true,
                rxTotalBytes = 22_000_000,
                txTotalBytes = 15_000_000,
                rxBytesPerSec = 80_000.0,
                txBytesPerSec = 40_000.0
            )
        )
    )
}
