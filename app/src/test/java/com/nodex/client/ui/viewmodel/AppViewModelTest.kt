package com.nodex.client.ui.viewmodel

import com.nodex.client.core.data.local.MetricRecordDao
import com.nodex.client.core.demo.DemoModeManager
import com.nodex.client.core.network.ssh.HostKeyAuditStore
import com.nodex.client.core.network.ssh.HostKeyInfo
import com.nodex.client.core.network.parser.StatsParser
import com.nodex.client.core.network.ssh.HostKeyPromptManager
import com.nodex.client.core.network.ssh.HostKeyVerificationRequiredException
import com.nodex.client.core.network.ssh.SSHConnectionPool
import com.nodex.client.core.security.SecureCredentialsStore
import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.ServerConfig
import com.nodex.client.domain.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `fast polling uses each server interval not selected server interval`() = runTest {
        val fastServer = ServerConfig(
            id = "fast",
            name = "Fast",
            hostname = "fast.example",
            username = "root",
            authType = AuthType.NONE,
            pollIntervalSeconds = 2
        )
        val slowServer = ServerConfig(
            id = "slow",
            name = "Slow",
            hostname = "slow.example",
            username = "root",
            authType = AuthType.NONE,
            pollIntervalSeconds = 30
        )
        val serversFlow = MutableStateFlow(listOf(fastServer, slowServer))
        val repository = mockk<ServerRepository>()
        every { repository.getAllServers() } returns serversFlow

        val demoModeManager = mockk<DemoModeManager>()
        every { demoModeManager.isDemoMode } returns MutableStateFlow(false)
        val connectionPool = mockk<SSHConnectionPool>()
        coEvery { connectionPool.execute<String>(any(), any(), any()) } returns Result.success(fastPollOutput())
        every { connectionPool.disconnectAll() } returns Unit

        val metricRecordDao = mockk<MetricRecordDao>(relaxed = true)
        val viewModel = AppViewModel(
            repository = repository,
            connectionPool = connectionPool,
            statsParser = StatsParser(),
            demoModeManager = demoModeManager,
            metricRecordDao = metricRecordDao,
            credentialsStore = mockk<SecureCredentialsStore>(relaxed = true),
            hostKeyPromptManager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        )

        runCurrent()
        viewModel.selectServer(slowServer.id)
        viewModel.onStart()
        runCurrent()

        advanceTimeBy(2_500)
        runCurrent()

        coVerify(exactly = 2) { connectionPool.execute<String>(match { it.id == "fast" }, any(), any()) }
        coVerify(exactly = 1) { connectionPool.execute<String>(match { it.id == "slow" }, any(), any()) }

        viewModel.onStop()
    }

    @Test
    fun `disconnect timer resets after app resumes and stops again`() = runTest {
        val repository = mockk<ServerRepository>()
        every { repository.getAllServers() } returns MutableStateFlow(emptyList())

        val demoModeManager = mockk<DemoModeManager>()
        every { demoModeManager.isDemoMode } returns MutableStateFlow(false)
        val connectionPool = mockk<SSHConnectionPool>()
        every { connectionPool.disconnectAll() } returns Unit

        val metricRecordDao = mockk<MetricRecordDao>(relaxed = true)
        val viewModel = AppViewModel(
            repository = repository,
            connectionPool = connectionPool,
            statsParser = StatsParser(),
            demoModeManager = demoModeManager,
            metricRecordDao = metricRecordDao,
            credentialsStore = mockk<SecureCredentialsStore>(relaxed = true),
            hostKeyPromptManager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        )

        runCurrent()

        viewModel.onStart()
        runCurrent()
        viewModel.onStop()
        runCurrent()
        val firstDisconnectJob = viewModel.disconnectJobForTest()
        assertNotNull(firstDisconnectJob)
        assertTrue(firstDisconnectJob?.isActive == true)

        advanceTimeBy(60_000)
        viewModel.onStart()
        runCurrent()
        assertTrue(firstDisconnectJob?.isCancelled == true)
        viewModel.onStop()
        runCurrent()
        val secondDisconnectJob = viewModel.disconnectJobForTest()
        assertNotNull(secondDisconnectJob)
        assertNotSame(firstDisconnectJob, secondDisconnectJob)
        assertTrue(secondDisconnectJob?.isActive == true)

        advanceTimeBy(61_000)
        runCurrent()
        coVerify(exactly = 0) { connectionPool.execute<Any>(any(), any(), any()) }
        io.mockk.verify(exactly = 0) { connectionPool.disconnectAll() }
    }

    @Test
    fun `host key prompt is not recreated while a runtime decision is pending`() = runTest {
        val repository = mockk<ServerRepository>()
        every { repository.getAllServers() } returns MutableStateFlow(emptyList())
        val demoModeManager = mockk<DemoModeManager>()
        every { demoModeManager.isDemoMode } returns MutableStateFlow(false)
        val connectionPool = mockk<SSHConnectionPool>(relaxed = true)
        val promptManager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        val viewModel = AppViewModel(
            repository = repository,
            connectionPool = connectionPool,
            statsParser = StatsParser(),
            demoModeManager = demoModeManager,
            metricRecordDao = mockk(relaxed = true),
            credentialsStore = mockk<SecureCredentialsStore>(relaxed = true),
            hostKeyPromptManager = promptManager
        )
        val server = ServerConfig(
            id = "host-key-runtime",
            name = "Runtime",
            hostname = "runtime.example",
            username = "root",
            authType = AuthType.NONE
        )
        val error = HostKeyVerificationRequiredException(
            HostKeyInfo(
                hostname = server.hostname,
                port = server.port,
                keyType = "ssh-ed25519",
                fingerprint = "SHA256:abc",
                publicKey = "pub"
            )
        )

        viewModel.invokeHandlePollFailure(server, error)
        runCurrent()
        val firstPromptId = promptManager.activePrompt.value?.id

        viewModel.invokeHandlePollFailure(server, error)
        runCurrent()

        assertNotNull(firstPromptId)
        assertEquals(firstPromptId, promptManager.activePrompt.value?.id)
    }

    private fun fastPollOutput(): String = """
[UPTIME]
1000.00 0.00

[LOAD]
0.10 0.20 0.30 1/100 123

[MEMINFO]
MemTotal:       1000
MemAvailable:    400

[CPU_STAT]
cpu  100 0 100 800 0 0 0 0 0 0

[DF]
Filesystem     Type  1024-blocks  Used Available Capacity Mounted
/dev/root      ext4        1000   500       500      50% /

[THERMAL]
cpu_thermal:42000

[IP_ADDR]
2: eth0    inet 192.168.1.10/24 scope global eth0

[NET_DEV]
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    eth0: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0

[IP_ROUTE]
default via 192.168.1.1 dev eth0 proto dhcp src 192.168.1.10 metric 100

[NET_SPEED]
/sys/class/net/eth0/speed:1000

[NET_MAC]
eth0:52:54:00:ab:cd:ef:1500:up
""".trimIndent()

    private fun AppViewModel.disconnectJobForTest(): Job? {
        val field = AppViewModel::class.java.getDeclaredField("disconnectJob")
        field.isAccessible = true
        return field.get(this) as? Job
    }

    private fun AppViewModel.invokeHandlePollFailure(server: ServerConfig, error: Throwable) {
        val method = AppViewModel::class.java.getDeclaredMethod(
            "handlePollFailure",
            ServerConfig::class.java,
            Throwable::class.java
        )
        method.isAccessible = true
        method.invoke(this, server, error)
    }
}
