package com.nodex.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nodex.client.core.data.local.MetricRecordDao
import com.nodex.client.core.data.local.MetricRecordEntity
import com.nodex.client.core.demo.DemoData
import com.nodex.client.core.demo.DemoModeManager
import com.nodex.client.core.network.parser.StatsParser
import com.nodex.client.core.network.ssh.HostKeyPromptManager
import com.nodex.client.core.network.ssh.HostKeyVerificationRequiredException
import com.nodex.client.core.network.ssh.SSHConnectionPool
import com.nodex.client.core.network.ssh.SSHCommands
import com.nodex.client.core.network.ssh.SshCommandRequest
import com.nodex.client.core.network.ssh.formatSshError
import com.nodex.client.core.security.CredentialVault
import com.nodex.client.domain.model.*
import com.nodex.client.ui.navigation.RefreshScope
import com.nodex.client.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val connectionPool: SSHConnectionPool,
    private val statsParser: StatsParser,
    private val demoModeManager: DemoModeManager,
    private val metricRecordDao: MetricRecordDao,
    private val credentialsStore: CredentialVault,
    private val hostKeyPromptManager: HostKeyPromptManager
) : ViewModel() {

    fun hostKeyPromptManagerForUi(): HostKeyPromptManager = hostKeyPromptManager

    // ── Server list ────────────────────────────────────────────────────
    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    val selectedServer: StateFlow<ServerConfig?> = combine(
        _servers, _selectedServerId
    ) { servers, id -> servers.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Connection state ───────────────────────────────────────────────
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _connectionQuality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    // ── Metrics ────────────────────────────────────────────────────────
    private val _overview = MutableStateFlow<Map<String, OverviewMetrics>>(emptyMap())
    val overview: StateFlow<Map<String, OverviewMetrics>> = _overview.asStateFlow()

    private val _networkInterfaces = MutableStateFlow<Map<String, List<NetworkInterfaceSample>>>(emptyMap())
    val networkInterfaces: StateFlow<Map<String, List<NetworkInterfaceSample>>> = _networkInterfaces.asStateFlow()

    private val _services = MutableStateFlow<Map<String, List<ServiceInfo>>>(emptyMap())
    val services: StateFlow<Map<String, List<ServiceInfo>>> = _services.asStateFlow()

    private val _processes = MutableStateFlow<Map<String, List<ProcessInfo>>>(emptyMap())
    val processes: StateFlow<Map<String, List<ProcessInfo>>> = _processes.asStateFlow()

    private val _alerts = MutableStateFlow<Map<String, List<AlertItem>>>(emptyMap())
    val alerts: StateFlow<Map<String, List<AlertItem>>> = _alerts.asStateFlow()

    private val _dockerContainers = MutableStateFlow<Map<String, List<DockerContainer>>>(emptyMap())
    val dockerContainers: StateFlow<Map<String, List<DockerContainer>>> = _dockerContainers.asStateFlow()

    private val _dockerStats = MutableStateFlow<Map<String, Map<String, DockerContainerStats>>>(emptyMap())
    val dockerStats: StateFlow<Map<String, Map<String, DockerContainerStats>>> = _dockerStats.asStateFlow()

    private val _dockerErrors = MutableStateFlow<Map<String, String?>>(emptyMap())
    val dockerErrors: StateFlow<Map<String, String?>> = _dockerErrors.asStateFlow()

    private val _dockerRefreshing = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val dockerRefreshing: StateFlow<Map<String, Boolean>> = _dockerRefreshing.asStateFlow()

    private val _capabilities = MutableStateFlow<Map<String, ServerCapabilities>>(emptyMap())

    // ── Public IP ──────────────────────────────────────────────────────
    private val _publicIP = MutableStateFlow<Map<String, String?>>(emptyMap())
    val publicIP: StateFlow<Map<String, String?>> = _publicIP.asStateFlow()

    // ── System info (hardware, sessions, etc.) ─────────────────────────
    private val _hardwareInfo = MutableStateFlow<Map<String, HardwareInfo>>(emptyMap())
    val hardwareInfo: StateFlow<Map<String, HardwareInfo>> = _hardwareInfo.asStateFlow()

    private val _interfaceDetails = MutableStateFlow<Map<String, List<InterfaceDetail>>>(emptyMap())
    val interfaceDetails: StateFlow<Map<String, List<InterfaceDetail>>> = _interfaceDetails.asStateFlow()

    // ── Errors ─────────────────────────────────────────────────────────
    private val _serverErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val serverErrors: StateFlow<Map<String, String>> = _serverErrors.asStateFlow()

    // ── Last updated timestamps ────────────────────────────────────────
    private val _lastUpdated = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastUpdated: StateFlow<Map<String, Long>> = _lastUpdated.asStateFlow()

    // ── Legacy compat ──────────────────────────────────────────────────
    @Suppress("DEPRECATION")
    private val _serverStats = MutableStateFlow<Map<String, ServerStats>>(emptyMap())
    @Suppress("DEPRECATION")
    val serverStats: StateFlow<Map<String, ServerStats>> = _serverStats.asStateFlow()

    // ── Demo mode ──────────────────────────────────────────────────────
    val isDemoMode: Flow<Boolean> = demoModeManager.isDemoMode

    private var _isDemoModeValue = false

    // ── Internal state ─────────────────────────────────────────────────
    private var pollJob: Job? = null
    private var slowPollJob: Job? = null
    private var disconnectJob: Job? = null
    private var isActive = false
    private val hostKeyRuntimeGate = HostKeyRuntimeGate()

    init {
        viewModelScope.launch {
            demoModeManager.isDemoMode
                .combine(repository.getAllServers()) { isDemo, servers -> isDemo to servers }
                .collect { (isDemo, servers) ->
                    if (isDemo) {
                        _isDemoModeValue = true
                        pollJob?.cancel()
                        slowPollJob?.cancel()
                        demoModeManager.ensureDemoServer()
                        val currentServers = if (servers.isEmpty()) listOf(DemoData.demoServer) else servers
                        _servers.value = currentServers
                        @Suppress("DEPRECATION")
                        _serverStats.value = currentServers.associate { it.id to DemoData.demoStats }
                        setupDemoMetrics(currentServers)
                        _serverErrors.value = emptyMap()
                        if (_selectedServerId.value == null) {
                            _selectedServerId.value = currentServers.firstOrNull()?.id
                        }
                    } else {
                        _isDemoModeValue = false
                        _servers.value = servers.sortedWith(
                            compareByDescending<ServerConfig> { it.isFavorite }
                                .thenBy { it.name }
                        )
                        if (_selectedServerId.value == null || servers.none { it.id == _selectedServerId.value }) {
                            _selectedServerId.value = servers.firstOrNull()?.id
                        }
                        if (isActive) startPolling()
                    }
                }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    fun onStart() {
        if (isActive) return
        isActive = true
        disconnectJob?.cancel()
        disconnectJob = null
        if (!_isDemoModeValue) startPolling()
    }

    fun onStop() {
        if (!isActive) return
        isActive = false
        pollJob?.cancel()
        slowPollJob?.cancel()
        disconnectJob?.cancel()
        // Keep connections alive for 2 minutes in case user returns quickly.
        disconnectJob = viewModelScope.launch {
            delay(120_000)
            if (!isActive) connectionPool.disconnectAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectJob?.cancel()
        connectionPool.disconnectAll()
    }

    fun selectServer(serverId: String) {
        _selectedServerId.value = serverId
    }

    // ── Polling ────────────────────────────────────────────────────────

    private fun startPolling() {
        pollJob?.cancel()
        slowPollJob?.cancel()
        val servers = _servers.value
        if (servers.isEmpty()) {
            clearAllMetrics()
            return
        }

        pollJob = viewModelScope.launch {
            supervisorScope {
                servers.forEach { server ->
                    launch {
                        while (this@AppViewModel.isActive && currentCoroutineContext().isActive) {
                            fastPoll(server)
                            val intervalMs = server.pollIntervalSeconds
                                .toLong()
                                .coerceIn(2, 60) * 1000L
                            delay(intervalMs)
                        }
                    }
                }
            }
        }

        slowPollJob = viewModelScope.launch {
            // Initial slow poll after 3s
            delay(3000)
            slowPollAll()
            while (isActive) {
                delay(60_000)
                slowPollAll()
            }
        }
    }

    private suspend fun slowPollAll() {
        val currentServers = _servers.value
        coroutineScope {
            currentServers.forEach { server ->
                launch { slowPoll(server) }
            }
        }
    }

    private suspend fun fastPoll(server: ServerConfig) {
        if (isHostKeyBlocked(server)) return

        val current = _connectionStates.value[server.id]
        if (current !is ConnectionState.Connected) {
            _connectionStates.update { it + (server.id to ConnectionState.Connecting) }
        }

        val result = connectionPool.execute(server) { client ->
            val startTime = System.nanoTime()
            val output = connectionPool.runCommand(client, SSHCommands.fastPoll(), timeoutSeconds = 15)
            val latencyMs = (System.nanoTime() - startTime) / 1_000_000
            _connectionQuality.value = ConnectionQuality.fromLatencyMs(latencyMs)
            output
        }

        result.onSuccess { output ->
            _connectionStates.update { it + (server.id to ConnectionState.Connected) }
            _serverErrors.update { it - server.id }
            _lastUpdated.update { it + (server.id to System.currentTimeMillis()) }

            val metrics = statsParser.parseOverview(output, server.id)
            _overview.update { it + (server.id to metrics) }

            val interfaces = statsParser.parseNetworkInterfaces(output, server.id,
                server.pollIntervalSeconds.toDouble())
            _networkInterfaces.update { it + (server.id to interfaces) }

            val details = statsParser.parseInterfaceDetails(output, server.id)
            _interfaceDetails.update { it + (server.id to details) }

            @Suppress("DEPRECATION")
            val legacyStats = statsParser.parseFastPoll(output, server.id)
            @Suppress("DEPRECATION")
            _serverStats.update { it + (server.id to legacyStats) }

            recordMetric(server.id, metrics)
        }.onFailure { handlePollFailure(server, it) }
    }

    private suspend fun slowPoll(server: ServerConfig) {
        if (isHostKeyBlocked(server)) return

        // Single batched command over pooled connection (was 6 separate connections)
        connectionPool.execute(server) { client ->
            connectionPool.runCommand(
                client,
                SSHCommands.slowPollBatch(server.alertLookbackMinutes, server.publicIPEnabled),
                timeoutSeconds = 30
            )
        }.onSuccess { output ->
            val sections = statsParser.splitSections(output)

            // Services
            val servicesSection = buildString {
                sections["SERVICES"]?.let { appendLine("[SERVICES]"); appendLine(it) }
                sections["SERVICES_FAILED"]?.let { appendLine("[SERVICES_FAILED]"); appendLine(it) }
            }
            if (servicesSection.isNotBlank()) {
                val serviceList = statsParser.parseServices(servicesSection)
                _services.update { it + (server.id to serviceList) }
            }

            // Processes
            sections["PROCESSES"]?.let { processSection ->
                val processList = statsParser.parseProcesses(processSection)
                _processes.update { it + (server.id to processList) }
            }

            // Journal → Alerts
            sections["JOURNAL"]?.let { journalSection ->
                val events = statsParser.parseJournalEvents(journalSection)
                val alertItems = aggregateAlerts(events)
                _alerts.update { it + (server.id to alertItems) }
            }

            // Public IP
            sections["PUBLIC_IP"]?.let { ipSection ->
                val ip = statsParser.parsePublicIP(ipSection)
                _publicIP.update { it + (server.id to ip) }
            }

            // System info (hardware, sessions, etc.) — reuse section markers
            val systemInfoOutput = buildString {
                for (key in listOf("HOSTNAME", "OS_RELEASE", "UNAME", "CPU_INFO", "BOOT_TIME", "BLOCK_DEV", "WHO", "LAST")) {
                    sections[key]?.let { appendLine("[$key]"); appendLine(it) }
                }
            }
            if (systemInfoOutput.isNotBlank()) {
                val info = statsParser.parseSystemInfo(systemInfoOutput)
                _hardwareInfo.update { it + (server.id to info) }

                // Update distro if detected and different
                val detectedDistro = info.osName.takeIf { it.isNotBlank() }
                if (detectedDistro != null && detectedDistro != server.distro) {
                    val updated = server.copy(distro = detectedDistro)
                    viewModelScope.launch { repository.updateServer(updated) }
                }
            }

            // Capabilities
            sections["CAPABILITIES"]?.let { capsSection ->
                val caps = statsParser.parseCapabilities(capsSection)
                _capabilities.update { it + (server.id to caps) }
                if (!caps.hasDocker) {
                    _dockerContainers.update { it + (server.id to emptyList()) }
                    _dockerStats.update { it + (server.id to emptyMap()) }
                    _dockerErrors.update { it - server.id }
                    _dockerRefreshing.update { it + (server.id to false) }
                }
            }
        }.onFailure { handlePollFailure(server, it) }
    }

    private fun handlePollFailure(server: ServerConfig, error: Throwable) {
        val message = if (error is HostKeyVerificationRequiredException) {
            if (hostKeyRuntimeGate.markPending(server.id)) {
                viewModelScope.launch {
                    hostKeyPromptManager.requestTrust(
                        info = error.hostKeyInfo,
                        onTrust = {
                            hostKeyRuntimeGate.markTrusted(server.id)
                            connectionPool.trustHostKey(error.hostKeyInfo)
                            fastPoll(server)
                        },
                        onReject = {
                            hostKeyRuntimeGate.markRejected(server.id)
                            _connectionStates.update {
                                it + (server.id to ConnectionState.Error("Host key rejected."))
                            }
                            _serverErrors.update { it + (server.id to "Host key rejected.") }
                        }
                    )
                }
            }
            "Review the host key prompt to continue."
        } else {
            formatSshError(error)
        }

        _connectionStates.update { it + (server.id to ConnectionState.Error(message)) }
        _serverErrors.update { it + (server.id to message) }
    }

    private fun isHostKeyBlocked(server: ServerConfig): Boolean {
        val message = hostKeyRuntimeGate.blockedMessage(server.id) ?: return false
        _connectionStates.update { it + (server.id to ConnectionState.Error(message)) }
        _serverErrors.update { it + (server.id to message) }
        return true
    }

    // ── Service actions ────────────────────────────────────────────────

    fun manageService(serverId: String, serviceName: String, action: ServiceAction,
                      onResult: (Result<String>) -> Unit = {}) {
        if (_isDemoModeValue) { onResult(Result.success("[Demo] ${action.name.lowercase()} simulated")); return }
        viewModelScope.launch {
            val server = _servers.value.find { it.id == serverId } ?: return@launch
            val storedSudoPassword = if (action.requiresSudo) credentialsStore.getSudoPassword(serverId) else null
            val request = buildCommandRequest(
                command = action.commandFor(serviceName),
                requiresSudo = action.requiresSudo,
                sudoPassword = storedSudoPassword
            )

            connectionPool.execute(server) { client ->
                connectionPool.runCommand(client, request, timeoutSeconds = 30)
            }.onSuccess { output ->
                onResult(Result.success(output))
                if (action != ServiceAction.STATUS) {
                    delay(1000)
                    connectionPool.execute(server) { client ->
                        connectionPool.runCommand(client, SSHCommands.services(), timeoutSeconds = 15)
                    }.onSuccess { servicesOutput ->
                        val serviceList = statsParser.parseServices(servicesOutput)
                        _services.update { it + (serverId to serviceList) }
                    }
                }
            }.onFailure { onResult(Result.failure(it)) }
        }
    }

    fun killProcess(serverId: String, pid: Int, signal: ProcessSignal = ProcessSignal.TERM,
                    onResult: (Result<String>) -> Unit = {}) {
        if (_isDemoModeValue) { onResult(Result.success("[Demo] kill simulated")); return }
        viewModelScope.launch {
            val server = _servers.value.find { it.id == serverId } ?: return@launch
            val request = buildCommandRequest(
                command = "kill ${signal.flag} $pid",
                requiresSudo = true,
                sudoPassword = credentialsStore.getSudoPassword(serverId)
            )
            connectionPool.execute(server) { client ->
                connectionPool.runCommand(client, request, timeoutSeconds = 10)
            }.onSuccess { onResult(Result.success(it)) }
                .onFailure { onResult(Result.failure(it)) }
        }
    }

    fun serverPowerAction(serverId: String, action: ServerPowerAction,
                          onResult: (Result<String>) -> Unit = {}) {
        if (_isDemoModeValue) { onResult(Result.success("[Demo] power action simulated")); return }
        viewModelScope.launch {
            val server = _servers.value.find { it.id == serverId } ?: return@launch
            val request = buildCommandRequest(
                command = action.command,
                requiresSudo = true,
                sudoPassword = credentialsStore.getSudoPassword(serverId)
            )
            connectionPool.execute(server) { client ->
                connectionPool.runCommand(client, request, timeoutSeconds = 10)
            }.onSuccess { onResult(Result.success(it)) }
                .onFailure { onResult(Result.failure(it)) }
        }
    }

    // ── Metrics history ────────────────────────────────────────────────

    fun getMetricHistory(serverId: String): Flow<List<MetricRecordEntity>> {
        return metricRecordDao.getRecordsForServer(serverId)
    }

    suspend fun exportMetricsCsv(serverId: String): String {
        val records = metricRecordDao.getRecordsForServerSync(serverId)
        val sb = StringBuilder()
        sb.appendLine("timestamp,cpu_usage,memory_usage,disk_usage,cpu_temp,network_rx,network_tx")
        records.sortedBy { it.timestamp }.forEach { r ->
            sb.appendLine("${r.timestamp},${r.cpuUsage},${r.memoryUsage},${r.diskUsage},${r.cpuTemperature ?: ""},${r.networkRxBytes},${r.networkTxBytes}")
        }
        return sb.toString()
    }

    suspend fun clearMetricHistory(serverId: String) {
        metricRecordDao.deleteForServer(serverId)
    }

    private suspend fun recordMetric(serverId: String, metrics: OverviewMetrics) {
        val entity = MetricRecordEntity(
            serverId = serverId,
            timestamp = metrics.timestamp,
            cpuUsage = metrics.cpuUsagePercent,
            memoryUsage = metrics.memUsagePercent,
            diskUsage = metrics.rootUsagePercent,
            cpuTemperature = metrics.cpuTemperature?.currentCelsius
        )
        metricRecordDao.insert(entity)
        metricRecordDao.trimRecords(serverId, 2880)
    }

    // ── Alert aggregation ──────────────────────────────────────────────

    private fun aggregateAlerts(events: List<LogEvent>): List<AlertItem> {
        val grouped = events.groupBy { "${it.sourceService ?: ""}|${it.category}|${it.message.take(80)}" }
        return grouped.map { (_, group) ->
            val first = group.first()
            AlertItem(
                title = first.message.take(100),
                serviceName = first.sourceService,
                severity = group.maxOf { it.severity },
                category = first.category,
                timestamp = group.last().timestamp,
                message = first.message,
                rawLog = first.raw ?: "",
                firstSeen = group.minOf { it.timestamp },
                lastSeen = group.maxOf { it.timestamp },
                occurrenceCount = group.size,
                relatedLogs = group.mapNotNull { it.raw }.take(10)
            )
        }.sortedWith(
            compareByDescending<AlertItem> { it.severity }
                .thenByDescending { it.lastSeen }
        )
    }

    // ── Demo mode ──────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun setupDemoMetrics(servers: List<ServerConfig>) {
        val now = System.currentTimeMillis()
        servers.forEach { server ->
            _connectionStates.update { it + (server.id to ConnectionState.Connected) }
            _overview.update { it + (server.id to OverviewMetrics(
                cpuUsagePercent = 18.0, load1 = 0.12, load5 = 0.24, load15 = 0.31,
                memUsedBytes = 1_700_000_000, memTotalBytes = 4_000_000_000,
                memFreeBytes = 800_000_000, memAvailableBytes = 2_300_000_000,
                memBuffersBytes = 200_000_000, memCachedBytes = 1_300_000_000,
                swapUsedBytes = 100_000_000, swapTotalBytes = 2_000_000_000,
                rootUsedBytes = 32_000_000_000, rootTotalBytes = 50_000_000_000,
                processCount = 142, uptimeSeconds = 12 * 86400 + 4 * 3600,
                cpuTemperature = TemperatureReading(
                    id = "cpu_pkg", sensorName = "coretemp", label = "CPU Package",
                    currentCelsius = 47.0
                ),
                volumes = listOf(
                    DiskVolume("/", totalBytes = 50_000_000_000, usedBytes = 32_000_000_000),
                    DiskVolume("/data", totalBytes = 200_000_000_000, usedBytes = 120_000_000_000)
                )
            )) }
            _networkInterfaces.update { it + (server.id to listOf(
                NetworkInterfaceSample(
                    name = "eth0", ipv4 = "192.168.1.42", ipv6 = "fe80::1", isUp = true,
                    rxTotalBytes = 125_000_000, txTotalBytes = 98_000_000,
                    rxBytesPerSec = 340_000.0, txBytesPerSec = 120_000.0
                ),
                NetworkInterfaceSample(
                    name = "lo", ipv4 = "127.0.0.1", isUp = true,
                    rxTotalBytes = 5_000_000, txTotalBytes = 5_000_000
                )
            )) }
            _interfaceDetails.update { it + (server.id to listOf(
                InterfaceDetail(
                    name = "eth0", macAddress = "52:54:00:ab:cd:ef", mtu = 1500,
                    operState = "UP", ipv4 = "192.168.1.42", ipv6 = "fe80::1",
                    speed = "1000Mb/s", isDefaultRoute = true,
                    rxBytes = 125_000_000, txBytes = 98_000_000,
                    rxBytesPerSec = 340_000.0, txBytesPerSec = 120_000.0
                )
            )) }
            _hardwareInfo.update { it + (server.id to HardwareInfo(
                hostname = "demo-server",
                kernelVersion = "6.1.0-21-amd64",
                architecture = "x86_64",
                cpuModel = "Intel Core i5-12400 @ 2.50GHz",
                cpuCores = 6,
                cpuThreads = 12,
                osName = "Ubuntu",
                osVersion = "22.04.3",
                osPrettyName = "Ubuntu 22.04.3 LTS",
                bootTimestamp = now - (12 * 86400 + 4 * 3600) * 1000L,
                blockDevices = listOf(
                    BlockDevice("sda", "50G", "QEMU HARDDISK", "disk", false, "SATA"),
                    BlockDevice("sdb", "200G", "QEMU HARDDISK", "disk", false, "SATA")
                ),
                activeSessions = listOf(UserSession("demo", "pts/0", "192.168.1.1", "10:32")),
                recentLogins = listOf("demo pts/0 192.168.1.1 Thu Feb 26 10:32")
            )) }
            _capabilities.update { it + (server.id to ServerCapabilities(
                kernel = "6.1.0-21-amd64",
                hasLsblk = true, hasLsblkJSON = true, hasIPJSON = true,
                hasSysstat = true, hasSensors = true, hasNvme = false,
                hasEthtool = true, hasDocker = true
            )) }
            _dockerContainers.update { it + (server.id to listOf(
                DockerContainer(
                    id = "d1",
                    shortID = "d1",
                    name = "nginx-proxy",
                    image = "nginx:latest",
                    imageID = "sha256:demo1",
                    status = DockerContainerStatus.RUNNING,
                    statusString = "Up 2 hours",
                    ports = listOf("0.0.0.0:80->80/tcp"),
                    composeProject = "proxy",
                    composeService = "nginx",
                    createdAt = "2026-01-01 10:00:00 +0000 UTC",
                    command = "nginx -g 'daemon off;'"
                ),
                DockerContainer(
                    id = "d2",
                    shortID = "d2",
                    name = "redis-cache",
                    image = "redis:7-alpine",
                    imageID = "sha256:demo2",
                    status = DockerContainerStatus.RUNNING,
                    statusString = "Up 5 hours",
                    ports = listOf("6379/tcp"),
                    composeProject = "cache",
                    composeService = "redis",
                    createdAt = "2026-01-01 10:00:00 +0000 UTC",
                    command = "docker-entrypoint.sh redis-server"
                ),
                DockerContainer(
                    id = "d3",
                    shortID = "d3",
                    name = "postgres-db",
                    image = "postgres:16",
                    imageID = "sha256:demo3",
                    status = DockerContainerStatus.EXITED,
                    statusString = "Exited (0) 10 minutes ago",
                    ports = listOf("5432/tcp"),
                    composeProject = "data",
                    composeService = "postgres",
                    createdAt = "2026-01-01 10:00:00 +0000 UTC",
                    command = "postgres"
                )
            )) }
            _dockerStats.update { it + (server.id to mapOf(
                "d1" to DockerContainerStats(
                    id = "d1",
                    name = "nginx-proxy",
                    cpuPercent = 0.5,
                    memUsageBytes = 45L * 1024L * 1024L,
                    memLimitBytes = 512L * 1024L * 1024L,
                    memPercent = 8.7,
                    netRxBytes = 1024L * 100L,
                    netTxBytes = 1024L * 50L,
                    blockReadBytes = 0,
                    blockWriteBytes = 0,
                    pids = 3
                ),
                "d2" to DockerContainerStats(
                    id = "d2",
                    name = "redis-cache",
                    cpuPercent = 0.1,
                    memUsageBytes = 12L * 1024L * 1024L,
                    memLimitBytes = 256L * 1024L * 1024L,
                    memPercent = 4.6,
                    netRxBytes = 1024L * 10L,
                    netTxBytes = 1024L * 20L,
                    blockReadBytes = 0,
                    blockWriteBytes = 0,
                    pids = 1
                )
            )) }
            _dockerErrors.update { it - server.id }
            _dockerRefreshing.update { it + (server.id to false) }
            _publicIP.update { it + (server.id to "203.0.113.42") }
            _services.update { it + (server.id to listOf(
                ServiceInfo("nginx.service", "nginx.service", "A high performance web server",
                    "loaded", "active", "running", ServiceState.Running),
                ServiceInfo("sshd.service", "sshd.service", "OpenBSD Secure Shell server",
                    "loaded", "active", "running", ServiceState.Running),
                ServiceInfo("postgresql.service", "postgresql.service", "PostgreSQL RDBMS",
                    "loaded", "active", "running", ServiceState.Running),
                ServiceInfo("docker.service", "docker.service", "Docker Application Container Engine",
                    "loaded", "active", "running", ServiceState.Running),
                ServiceInfo("fail2ban.service", "fail2ban.service", "Fail2Ban Service",
                    "loaded", "active", "running", ServiceState.Running)
            )) }
            _processes.update { it + (server.id to listOf(
                ProcessInfo(pid = 1234, user = "root", cpuPercent = 5.2, memPercent = 2.1, command = "nginx: worker process"),
                ProcessInfo(pid = 5678, user = "postgres", cpuPercent = 3.1, memPercent = 8.4, command = "postgres: autovacuum"),
                ProcessInfo(pid = 9012, user = "root", cpuPercent = 0.5, memPercent = 0.3, command = "sshd: demo@pts/0"),
                ProcessInfo(pid = 2345, user = "root", cpuPercent = 1.2, memPercent = 0.8, command = "dockerd"),
                ProcessInfo(pid = 3456, user = "www-data", cpuPercent = 0.8, memPercent = 1.5, command = "php-fpm: pool www")
            )) }
            _alerts.update { it + (server.id to listOf(
                AlertItem(
                    title = "High memory usage detected",
                    serviceName = "System",
                    severity = AlertSeverity.WARNING,
                    category = AlertCategory.MEMORY,
                    timestamp = now - 3600_000,
                    message = "Memory usage at 72% — above warning threshold.",
                    currentServiceState = ServiceState.Other("active")
                ),
                AlertItem(
                    title = "nginx.service restarted",
                    serviceName = "nginx.service",
                    severity = AlertSeverity.INFO,
                    category = AlertCategory.SERVICE,
                    timestamp = now - 7200_000,
                    message = "nginx.service was restarted by systemd.",
                    currentServiceState = ServiceState.Running
                )
            )) }
            _lastUpdated.update { it + (server.id to now) }
        }
    }

    private fun clearAllMetrics() {
        @Suppress("DEPRECATION")
        _serverStats.value = emptyMap()
        _overview.value = emptyMap()
        _networkInterfaces.value = emptyMap()
        _services.value = emptyMap()
        _processes.value = emptyMap()
        _alerts.value = emptyMap()
        _dockerContainers.value = emptyMap()
        _dockerStats.value = emptyMap()
        _dockerErrors.value = emptyMap()
        _dockerRefreshing.value = emptyMap()
        _publicIP.value = emptyMap()
        _hardwareInfo.value = emptyMap()
        _interfaceDetails.value = emptyMap()
        _capabilities.value = emptyMap()
        _serverErrors.value = emptyMap()
        _connectionStates.value = emptyMap()
        _lastUpdated.value = emptyMap()
    }

    // ── Server management ──────────────────────────────────────────────

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            connectionPool.disconnect(serverId)
            clearServerState(serverId)
            val server = repository.getServerById(serverId) ?: return@launch
            repository.deleteServer(server)
            if (_selectedServerId.value == serverId) {
                _selectedServerId.value = _servers.value.firstOrNull { it.id != serverId }?.id
            }
        }
    }

    private fun clearServerState(serverId: String) {
        _overview.update { it - serverId }
        _networkInterfaces.update { it - serverId }
        _services.update { it - serverId }
        _processes.update { it - serverId }
        _alerts.update { it - serverId }
        _dockerContainers.update { it - serverId }
        _dockerStats.update { it - serverId }
        _dockerErrors.update { it - serverId }
        _dockerRefreshing.update { it - serverId }
        _publicIP.update { it - serverId }
        _hardwareInfo.update { it - serverId }
        _interfaceDetails.update { it - serverId }
        _capabilities.update { it - serverId }
        _connectionStates.update { it - serverId }
        _serverErrors.update { it - serverId }
        _lastUpdated.update { it - serverId }
        @Suppress("DEPRECATION")
        _serverStats.update { it - serverId }
    }

    fun updateServer(server: ServerConfig) {
        viewModelScope.launch {
            hostKeyRuntimeGate.clear(server.id)
            repository.updateServer(server)
        }
    }

    fun hasSudoPassword(serverId: String): Boolean {
        return credentialsStore.getSudoPassword(serverId) != null
    }

    fun hasPassword(serverId: String): Boolean {
        return credentialsStore.getPassword(serverId) != null
    }

    fun setPassword(serverId: String, password: String?) {
        if (password.isNullOrBlank()) {
            credentialsStore.clearPassword(serverId)
        } else {
            credentialsStore.savePassword(serverId, password)
        }
    }

    fun setSudoPassword(serverId: String, password: String?) {
        if (password.isNullOrBlank()) {
            credentialsStore.clearSudoPassword(serverId)
        } else {
            credentialsStore.saveSudoPassword(serverId, password)
        }
    }

    fun getCapabilities(serverId: String): ServerCapabilities? = _capabilities.value[serverId]

    fun refreshDockerNow(serverId: String? = _selectedServerId.value) {
        val targetId = serverId ?: return
        if (_isDemoModeValue) return
        viewModelScope.launch {
            val server = _servers.value.find { it.id == targetId } ?: return@launch
            refreshDocker(server)
        }
    }

    private suspend fun refreshDocker(server: ServerConfig) {
        val caps = _capabilities.value[server.id]
        if (caps?.hasDocker != true) {
            _dockerContainers.update { it + (server.id to emptyList()) }
            _dockerStats.update { it + (server.id to emptyMap()) }
            _dockerErrors.update { it - server.id }
            _dockerRefreshing.update { it + (server.id to false) }
            return
        }

        _dockerRefreshing.update { it + (server.id to true) }

        val listRequest = buildDockerCommandRequest(server.id, caps) { runtime ->
            SSHCommands.dockerList(runtime)
        }
        val listResult = connectionPool.execute(server) { client ->
            connectionPool.runCommand(client, listRequest, timeoutSeconds = 20)
        }

        listResult.onSuccess { output ->
            val hasJsonPayload = output.lineSequence().any { it.trim().startsWith("{") }
            if (output.isNotBlank() && !hasJsonPayload) {
                _dockerContainers.update { it + (server.id to emptyList()) }
                _dockerStats.update { it + (server.id to emptyMap()) }
                _dockerErrors.update { current -> current + (server.id to output.trim()) }
                return@onSuccess
            }

            val containers = statsParser.parseDockerContainers(output)
            _dockerContainers.update { it + (server.id to containers) }
            _dockerErrors.update { it - server.id }
        }.onFailure {
            _dockerErrors.update { current -> current + (server.id to formatSshError(it)) }
        }

        val containersLoaded = listResult.isSuccess && _dockerErrors.value[server.id].isNullOrBlank()
        if (containersLoaded) {
            val statsRequest = buildDockerCommandRequest(server.id, caps) { runtime ->
                SSHCommands.dockerStats(runtime)
            }
            val statsResult = connectionPool.execute(server) { client ->
                connectionPool.runCommand(client, statsRequest, timeoutSeconds = 20)
            }
            statsResult.onSuccess { output ->
                val hasJsonPayload = output.lineSequence().any { it.trim().startsWith("{") }
                if (output.isNotBlank() && !hasJsonPayload) {
                    _dockerStats.update { it + (server.id to emptyMap()) }
                    _dockerErrors.update { current -> current + (server.id to output.trim()) }
                    return@onSuccess
                }

                val stats = statsParser.parseDockerStats(output)
                val map = buildMap<String, DockerContainerStats> {
                    stats.forEach { stat ->
                        put(stat.id, stat)
                        put(stat.id.take(12), stat)
                    }
                }
                _dockerStats.update { it + (server.id to map) }
                _dockerErrors.update { it - server.id }
            }.onFailure {
                _dockerErrors.update { current -> current + (server.id to formatSshError(it)) }
            }
        }

        _dockerRefreshing.update { it + (server.id to false) }
    }

    fun performDockerAction(
        serverId: String,
        containerId: String,
        action: String,
        onResult: (Result<String>) -> Unit = {}
    ) {
        if (_isDemoModeValue) {
            onResult(Result.success("[Demo] $action simulated"))
            return
        }
        viewModelScope.launch {
            val server = _servers.value.find { it.id == serverId } ?: return@launch
            val caps = _capabilities.value[serverId] ?: return@launch
            val request = buildDockerCommandRequest(serverId, caps) { runtime ->
                SSHCommands.dockerAction(runtime, action, containerId)
            }
            connectionPool.execute(server) { client ->
                connectionPool.runCommand(
                    client,
                    request,
                    timeoutSeconds = 20
                )
            }.onSuccess { output ->
                if (isDockerCommandError(output)) {
                    onResult(Result.failure(IllegalStateException(output.trim())))
                } else {
                    onResult(Result.success(output))
                    delay(800)
                    refreshDocker(server)
                }
            }.onFailure {
                onResult(Result.failure(it))
            }
        }
    }

    suspend fun fetchDockerLogs(serverId: String, containerId: String, lines: Int = 200): String {
        if (_isDemoModeValue) return "[Demo] docker logs unavailable"
        val server = _servers.value.find { it.id == serverId } ?: return ""
        val caps = _capabilities.value[serverId] ?: return ""
        val request = buildDockerCommandRequest(
            serverId = serverId,
            caps = caps,
            maxOutputChars = 1_000_000
        ) { runtime ->
            SSHCommands.dockerLogs(runtime, containerId, lines)
        }
        return connectionPool.execute(server) { client ->
            connectionPool.runCommand(
                client,
                request,
                timeoutSeconds = 20
            )
        }.getOrElse { formatSshError(it) }
    }

    private fun isDockerCommandError(output: String): Boolean {
        val trimmed = output.trim()
        if (trimmed.isBlank()) return false

        val lower = trimmed.lowercase()
        return lower.startsWith("error:")
            || lower.startsWith("error response from daemon:")
            || lower.contains("permission denied")
            || lower.contains("cannot connect to the docker daemon")
            || lower.contains("is the docker daemon running")
            || lower.contains("sudo:")
            || lower.contains("authentication is required")
    }

    fun refreshNow(scope: RefreshScope = RefreshScope.ALL) {
        viewModelScope.launch {
            val selectedId = _selectedServerId.value
            val selectedServer = _servers.value.find { it.id == selectedId } ?: return@launch

            if (scope.includesFastPoll) {
                fastPoll(selectedServer)
            }

            if (scope.includesSlowPoll) {
                slowPoll(selectedServer)
            }

            if (scope.includesDocker) {
                refreshDocker(selectedServer)
            }
        }
    }

    fun manageServiceWithSudo(
        serverId: String,
        serviceName: String,
        action: ServiceAction,
        sudoPassword: String,
        onResult: (Result<String>) -> Unit = {}
    ) {
        if (_isDemoModeValue) {
            onResult(Result.success("[Demo] ${action.name.lowercase()} simulated"))
            return
        }
        viewModelScope.launch {
            val server = _servers.value.find { it.id == serverId } ?: return@launch
            val request = SshCommandRequest.sudo(
                command = "${action.commandFor(serviceName)} 2>&1",
                password = sudoPassword
            )

            connectionPool.execute(server) { client ->
                connectionPool.runCommand(client, request, timeoutSeconds = 30)
            }.onSuccess { output ->
                onResult(Result.success(output))
                if (action != ServiceAction.STATUS) {
                    delay(1000)
                    connectionPool.execute(server) { client ->
                        connectionPool.runCommand(client, SSHCommands.services(), timeoutSeconds = 15)
                    }.onSuccess { servicesOutput ->
                        val serviceList = statsParser.parseServices(servicesOutput)
                        _services.update { it + (serverId to serviceList) }
                    }
                }
            }.onFailure { onResult(Result.failure(it)) }
        }
    }

    private fun buildCommandRequest(
        command: String,
        requiresSudo: Boolean,
        sudoPassword: String?,
        maxOutputChars: Int = SshCommandRequest.DEFAULT_MAX_OUTPUT_CHARS
    ): SshCommandRequest {
        val trimmedCommand = command.trim()
        return when {
            requiresSudo && !sudoPassword.isNullOrBlank() ->
                SshCommandRequest.sudo(trimmedCommand, sudoPassword, maxOutputChars)
            requiresSudo ->
                SshCommandRequest.plain("sudo -n $trimmedCommand", maxOutputChars)
            else ->
                SshCommandRequest.plain(trimmedCommand, maxOutputChars)
        }
    }

    private fun buildDockerCommandRequest(
        serverId: String,
        caps: ServerCapabilities,
        maxOutputChars: Int = SshCommandRequest.DEFAULT_MAX_OUTPUT_CHARS,
        build: (String) -> String
    ): SshCommandRequest {
        val runtime = caps.containerRuntimeBinary ?: "docker"
        if (!caps.dockerNeedsSudo) {
            return SshCommandRequest.plain(build(runtime), maxOutputChars)
        }

        val sudoPassword = credentialsStore.getSudoPassword(serverId)
        val runtimePrefix = if (!sudoPassword.isNullOrBlank()) {
            "sudo -S -p '' $runtime"
        } else {
            "sudo -n $runtime"
        }
        val command = build(runtimePrefix)
        return if (!sudoPassword.isNullOrBlank()) {
            SshCommandRequest(
                command = command,
                stdin = sudoPassword + "\n",
                maxOutputChars = maxOutputChars
            )
        } else {
            SshCommandRequest.plain(command, maxOutputChars)
        }
    }
}
