package com.nodex.client.core.network.parser

import com.nodex.client.domain.model.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class StatsParser @Inject constructor() {

    private data class CpuState(val total: Long, val idle: Long)
    private data class CpuTickSnapshot(
        val user: Long, val nice: Long, val system: Long, val idle: Long,
        val iowait: Long, val irq: Long, val softirq: Long, val steal: Long
    ) {
        val total get() = user + nice + system + idle + iowait + irq + softirq + steal
        val idleTotal get() = idle + iowait
    }
    private data class InterfaceMacInfo(val macAddress: String, val mtu: Int, val operState: String)

    private val cpuStates = ConcurrentHashMap<String, CpuState>()
    private val cpuTickStates = ConcurrentHashMap<String, Map<String, CpuTickSnapshot>>()
    private val netDevStates = ConcurrentHashMap<String, Map<String, Pair<Long, Long>>>()

    companion object {
        private val HEADER_REGEX = Regex("""^\[(.+)]$""")
        private val WHITESPACE = Regex("\\s+")
        private val IP_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        private val NET_SPEED_PATH = Regex("/sys/class/net/(\\w+)/speed")
        private val DEV_IFACE = Regex("dev\\s+(\\S+)")
        private val PSEUDO_FS = setOf(
            "tmpfs", "devtmpfs", "overlay", "squashfs", "proc", "sysfs",
            "cgroup", "cgroup2", "pstore", "debugfs", "tracefs", "securityfs",
            "configfs", "ramfs", "hugetlbfs", "autofs", "binfmt_misc", "mqueue",
            "devpts", "fusectl"
        )

        fun isPseudoFilesystem(type: String) = type.lowercase() in PSEUDO_FS
    }

    // ── Full overview parser (iOS parity) ──────────────────────────────

    fun parseOverview(output: String, serverId: String): OverviewMetrics {
        val sections = splitSections(output)

        val cpuResult = parseCpuFull(serverId, sections["CPU_STAT"] ?: "")
        val mem = parseMemoryFull(sections["MEMINFO"] ?: "")
        val mounts = parseMounts(sections["DF"] ?: "")
        val rootMount = mounts.find { it.mountPoint == "/" }
        val otherMounts = mounts.filter { it.mountPoint != "/" }
        val loadParts = (sections["LOAD"] ?: "").split(WHITESPACE)
        val uptimeStr = sections["UPTIME"] ?: ""
        val uptimeSeconds = uptimeStr.split(" ").firstOrNull()?.toDoubleOrNull()?.toLong() ?: 0L
        val processCount = loadParts.getOrNull(3)?.split("/")?.getOrNull(1)?.toIntOrNull() ?: 0
        val temperature = parseThermalZones(sections["THERMAL"] ?: "").firstOrNull()

        return OverviewMetrics(
            timestamp = System.currentTimeMillis(),
            cpuUsagePercent = cpuResult.totalUsage,
            cpuCores = cpuResult.cores,
            cpuTemperature = temperature,
            load1 = loadParts.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
            load5 = loadParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            load15 = loadParts.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
            memUsedBytes = mem.used,
            memTotalBytes = mem.total,
            memFreeBytes = mem.free,
            memAvailableBytes = mem.available,
            memBuffersBytes = mem.buffers,
            memCachedBytes = mem.cached,
            swapUsedBytes = mem.swapUsed,
            swapTotalBytes = mem.swapTotal,
            rootUsedBytes = rootMount?.usedBytes ?: 0L,
            rootTotalBytes = rootMount?.totalBytes ?: 0L,
            volumes = otherMounts,
            processCount = processCount,
            uptimeSeconds = uptimeSeconds
        )
    }

    fun parseNetworkInterfaces(
        output: String,
        serverId: String,
        intervalSeconds: Double = 2.0
    ): List<NetworkInterfaceSample> {
        val sections = splitSections(output)
        val ipAddrData = sections["IP_ADDR"] ?: ""
        val netDevData = sections["NET_DEV"] ?: ""
        val ipRouteData = sections["IP_ROUTE"] ?: ""
        val netSpeedData = sections["NET_SPEED"] ?: ""
        val netMacData = sections["NET_MAC"] ?: ""

        val addrMap = parseIpAddrMap(ipAddrData)
        val speedMap = parseNetworkSpeeds(netSpeedData)
        val macMap = parseInterfaceMacInfo(netMacData)
        val defaultIface = parseDefaultRoute(ipRouteData)
        val current = parseNetDevCounters(netDevData)
        val previous = netDevStates[serverId] ?: current
        netDevStates[serverId] = current

        return current.map { (name, counters) ->
            val prev = previous[name] ?: counters
            val rxDelta = maxOf(0L, counters.first - prev.first)
            val txDelta = maxOf(0L, counters.second - prev.second)
            val rxRate = if (intervalSeconds > 0) rxDelta.toDouble() / intervalSeconds else 0.0
            val txRate = if (intervalSeconds > 0) txDelta.toDouble() / intervalSeconds else 0.0
            val addrs = addrMap[name]

            NetworkInterfaceSample(
                name = name,
                ipv4 = addrs?.first,
                ipv6 = addrs?.second,
                rxBytesPerSec = rxRate,
                txBytesPerSec = txRate,
                rxTotalBytes = counters.first,
                txTotalBytes = counters.second,
                isUp = macMap[name]?.operState?.equals("up", ignoreCase = true) == true,
                isDefaultRoute = defaultIface == name,
                speed = speedMap[name]
            )
        }.sortedBy { it.name }
    }

    // ── Service parser ─────────────────────────────────────────────────

    fun parseServices(allRaw: String, failedRaw: String? = null): List<ServiceInfo> {
        val sections = splitSections(allRaw)
        val servicesRaw = sections["SERVICES"] ?: allRaw
        val failedServicesRaw = failedRaw ?: sections["SERVICES_FAILED"]

        val map = mutableMapOf<String, ServiceInfo>()
        parseServiceLines(servicesRaw).forEach { map[it.name] = it }
        failedServicesRaw?.let { raw ->
            parseServiceLines(raw, preferredState = ServiceState.Failed).forEach {
                map[it.name] = it
            }
        }
        return map.values.sortedBy { it.name }
    }

    private fun parseServiceLines(raw: String, preferredState: ServiceState? = null): List<ServiceInfo> {
        return raw.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split(WHITESPACE)
            if (parts.size < 4) return@mapNotNull null
            val name = parts[0]
            if (!name.endsWith(".service")) return@mapNotNull null
            val load = parts[1]
            val active = parts[2]
            val sub = parts[3]
            val desc = if (parts.size > 4) parts.drop(4).joinToString(" ") else ""
            val state = preferredState ?: when {
                active == "active" && (sub == "running" || sub == "listening") -> ServiceState.Running
                active == "failed" || sub == "failed" -> ServiceState.Failed
                sub == "dead" || active == "inactive" -> ServiceState.Stopped
                else -> ServiceState.Other(sub.ifEmpty { active })
            }
            ServiceInfo(
                id = name, name = name, description = desc,
                loadState = load, activeState = active, subState = sub, state = state
            )
        }
    }

    // ── Process parser ─────────────────────────────────────────────────

    fun parseProcesses(raw: String): List<ProcessInfo> {
        return raw.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split(WHITESPACE, limit = 6)
            if (parts.size < 6) return@mapNotNull null
            if (parts[0].equals("PID", ignoreCase = true)) return@mapNotNull null
            ProcessInfo(
                pid = parts[0].toIntOrNull() ?: return@mapNotNull null,
                ppid = parts[1].toIntOrNull() ?: 0,
                user = parts[2],
                cpuPercent = parts[3].toDoubleOrNull() ?: 0.0,
                memPercent = parts[4].toDoubleOrNull() ?: 0.0,
                command = parts[5]
            )
        }
    }

    // ── Journal / Alert parser ─────────────────────────────────────────

    fun parseJournalEvents(raw: String): List<LogEvent> {
        return raw.lines().mapNotNull { line ->
            if (!line.trimStart().startsWith("{")) return@mapNotNull null
            try {
                val json = org.json.JSONObject(line)
                val message = json.optString("MESSAGE", "(no message)")
                val source: String? = json.optString("_SYSTEMD_UNIT", "").takeIf { it.isNotEmpty() }
                    ?: json.optString("SYSLOG_IDENTIFIER", "").takeIf { it.isNotEmpty() }
                val severity = severityFromPriority(json.optString("PRIORITY", ""))
                val category = categoryFromMessage(message, source)
                val timestamp = json.optString("__REALTIME_TIMESTAMP", "")
                    .toLongOrNull()?.div(1000) ?: System.currentTimeMillis()

                LogEvent(
                    timestamp = timestamp,
                    category = category,
                    severity = severity,
                    message = message,
                    sourceService = source,
                    raw = line
                )
            } catch (_: Exception) { null }
        }
    }

    private fun severityFromPriority(priority: String): AlertSeverity = when (priority) {
        "0", "1", "2" -> AlertSeverity.CRITICAL
        "3", "4" -> AlertSeverity.WARNING
        else -> AlertSeverity.INFO
    }

    private fun categoryFromMessage(message: String, source: String?): AlertCategory {
        val msg = message.lowercase()
        val src = source?.lowercase() ?: ""
        return when {
            src.contains("cpu") -> AlertCategory.CPU
            msg.contains("memory") || msg.contains("oom") -> AlertCategory.MEMORY
            msg.contains("disk") || msg.contains("ext4") || msg.contains("btrfs") -> AlertCategory.DISK
            src.contains("network") || src.contains("systemd-networkd") -> AlertCategory.NETWORK
            src.isNotEmpty() -> AlertCategory.SERVICE
            else -> AlertCategory.OTHER
        }
    }

    // ── Capabilities parser ────────────────────────────────────────────

    fun parseCapabilities(raw: String): ServerCapabilities {
    var kernel: String? = null
    var hasLsblk = false
    var hasLsblkJSON = false
    var hasIPJSON = false
    var hasSysstat = false
    var hasSensors = false
    var hasNvme = false
    var hasEthtool = false
    var hasDocker = false
    var dockerNeedsSudo = false
    var isPodman = false

    for (line in raw.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("HAS_LSBLK_JSON") -> hasLsblkJSON = true
            trimmed.startsWith("HAS_LSBLK") -> hasLsblk = true
            trimmed.startsWith("HAS_IP_JSON") -> hasIPJSON = true
            trimmed.startsWith("HAS_SYSSTAT") -> hasSysstat = true
            trimmed.startsWith("HAS_SENSORS") -> hasSensors = true
            trimmed.startsWith("HAS_NVME") -> hasNvme = true
            trimmed.startsWith("HAS_ETHTOOL") -> hasEthtool = true
            trimmed.startsWith("HAS_DOCKER") -> hasDocker = true
            trimmed.startsWith("DOCKER_NEEDS_SUDO") -> dockerNeedsSudo = true
            trimmed.startsWith("IS_PODMAN") -> isPodman = true
            kernel == null && trimmed.isNotEmpty() && !trimmed.startsWith("HAS_") &&
                !trimmed.startsWith("DOCKER_") && !trimmed.startsWith("IS_") ->
                kernel = trimmed
        }
    }

    return ServerCapabilities(
        kernel = kernel,
        hasLsblk = hasLsblk,
        hasLsblkJSON = hasLsblkJSON,
        hasIPJSON = hasIPJSON,
        hasSysstat = hasSysstat,
        hasSensors = hasSensors,
        hasNvme = hasNvme,
        hasEthtool = hasEthtool,
        hasDocker = hasDocker,
        dockerNeedsSudo = dockerNeedsSudo,
        isPodman = isPodman
    )
}

fun parseDockerContainers(raw: String): List<DockerContainer> {
    return raw.lines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return@mapNotNull null
        try {
            val json = org.json.JSONObject(trimmed)
            val idRaw = json.optString("ID")
            val stateRaw = json.optString("State").lowercase()
            val labelsRaw = json.optString("Labels")
            var composeProject: String? = null
            var composeService: String? = null

            labelsRaw.split(",").forEach { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    when (parts[0].trim()) {
                        "com.docker.compose.project" -> composeProject = parts[1].trim()
                        "com.docker.compose.service" -> composeService = parts[1].trim()
                    }
                }
            }

            val ports = json.optString("Ports")
                .takeIf { it.isNotBlank() }
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            DockerContainer(
                id = idRaw,
                shortID = idRaw.take(12),
                name = json.optString("Names").trim('/'),
                image = json.optString("Image"),
                imageID = json.optString("ImageID"),
                status = when (stateRaw) {
                    "running" -> DockerContainerStatus.RUNNING
                    "exited" -> DockerContainerStatus.EXITED
                    "paused" -> DockerContainerStatus.PAUSED
                    "restarting" -> DockerContainerStatus.RESTARTING
                    "created" -> DockerContainerStatus.CREATED
                    "dead" -> DockerContainerStatus.DEAD
                    "removing" -> DockerContainerStatus.REMOVING
                    else -> DockerContainerStatus.UNKNOWN
                },
                statusString = json.optString("Status"),
                ports = ports,
                composeProject = composeProject,
                composeService = composeService,
                createdAt = json.optString("CreatedAt"),
                command = json.optString("Command")
            )
        } catch (_: Exception) {
            null
        }
    }
}

fun parseDockerStats(raw: String): List<DockerContainerStats> {
    return raw.lines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return@mapNotNull null
        try {
            val json = org.json.JSONObject(trimmed)
            val id = json.optString("ID").ifBlank { json.optString("Container") }
            val name = json.optString("Name")
                .ifBlank { json.optString("Container") }
                .trim('/')

            val (memUsed, memLimit) = splitIoPair(json.optString("MemUsage"))
            val (netRx, netTx) = splitIoPair(json.optString("NetIO"))
            val (blockRead, blockWrite) = splitIoPair(json.optString("BlockIO"))

            DockerContainerStats(
                id = id,
                name = name,
                cpuPercent = json.optString("CPUPerc").removeSuffix("%").trim().toDoubleOrNull() ?: 0.0,
                memUsageBytes = memUsed,
                memLimitBytes = memLimit,
                memPercent = json.optString("MemPerc").removeSuffix("%").trim().toDoubleOrNull() ?: 0.0,
                netRxBytes = netRx,
                netTxBytes = netTx,
                blockReadBytes = blockRead,
                blockWriteBytes = blockWrite,
                pids = json.optString("PIDs").trim().toIntOrNull() ?: 0
            )
        } catch (_: Exception) {
            null
        }
    }
}

private fun splitIoPair(raw: String): Pair<Long, Long> {
    val parts = raw.split(Regex("""\s*/\s*"""), limit = 2)
    return parseHumanBytes(parts.getOrNull(0)) to parseHumanBytes(parts.getOrNull(1))
}

private fun parseHumanBytes(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val match = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([A-Za-z]+)?""").find(raw.trim()) ?: return 0L
    val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return 0L
    val unit = match.groupValues.getOrElse(2) { "" }.lowercase()
    val multiplier = when (unit) {
        "", "b" -> 1.0
        "kb", "kib", "k" -> 1024.0
        "mb", "mib", "m" -> 1024.0 * 1024.0
        "gb", "gib", "g" -> 1024.0 * 1024.0 * 1024.0
        "tb", "tib", "t" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (value * multiplier).toLong()
}

    // ── Temperature parser ─────────────────────────────────────────────

    fun parseThermalZones(raw: String): List<TemperatureReading> {
        return raw.lines().mapNotNull { line ->
            val parts = line.split(":")
            if (parts.size < 2) return@mapNotNull null
            val name = parts[0].trim()
            val milliCelsius = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            TemperatureReading(
                id = name,
                sensorName = name,
                label = name.replace("_", " ")
                    .replaceFirstChar { it.uppercase() },
                currentCelsius = milliCelsius / 1000.0,
                highCelsius = 85.0,
                criticalCelsius = 100.0
            )
        }
    }

    fun parsePublicIP(raw: String): String? {
        val ip = raw.trim()
        return if (ip.matches(IP_REGEX)) ip else null
    }

    // ── System info parser ─────────────────────────────────────────────

    fun parseSystemInfo(output: String): HardwareInfo {
        val sections = splitSections(output)
        val hostname = sections["HOSTNAME"]?.trim() ?: ""
        val uname = sections["UNAME"]?.trim() ?: ""
        val architecture = uname.split(" ").lastOrNull() ?: ""
        val kernelVersion = uname.split(" ").getOrNull(2) ?: ""

        // OS release
        val osRelease = sections["OS_RELEASE"] ?: ""
        val osFields = osRelease.lines().associate { line ->
            val eq = line.indexOf('=')
            if (eq > 0) line.substring(0, eq) to line.substring(eq + 1).trim('"')
            else "" to ""
        }

        // CPU info from lscpu
        val cpuRaw = sections["CPU_INFO"] ?: ""
        val cpuFields = cpuRaw.lines().associate { line ->
            val colon = line.indexOf(':')
            if (colon > 0) line.substring(0, colon).trim() to line.substring(colon + 1).trim()
            else "" to ""
        }
        val cpuModel = cpuFields["Model name"] ?: cpuFields["model name"]
            ?: cpuRaw.lines().firstOrNull()?.substringAfter(":")?.trim() ?: ""
        val cpuCores = cpuFields["Core(s) per socket"]?.toIntOrNull() ?: 0
        val cpuSockets = cpuFields["Socket(s)"]?.toIntOrNull() ?: 1
        val cpuThreads = cpuFields["CPU(s)"]?.toIntOrNull() ?: 0

        // Boot time
        val btimeLine = sections["BOOT_TIME"]?.trim() ?: ""
        val bootTimestamp = btimeLine.split(" ").lastOrNull()?.toLongOrNull() ?: 0

        // Block devices from lsblk
        val blockDevices = parseBlockDevices(sections["BLOCK_DEV"] ?: "")

        // Active sessions from who
        val sessions = parseSessions(sections["WHO"] ?: "")

        // Recent logins from last
        val recentLogins = (sections["LAST"] ?: "").lines()
            .filter { it.isNotBlank() && !it.startsWith("wtmp") && !it.startsWith("reboot") }
            .take(10)

        return HardwareInfo(
            hostname = hostname,
            kernelVersion = kernelVersion,
            architecture = architecture,
            cpuModel = cpuModel,
            cpuCores = cpuCores * cpuSockets,
            cpuThreads = cpuThreads,
            osName = osFields["ID"] ?: "",
            osVersion = osFields["VERSION_ID"] ?: "",
            osPrettyName = osFields["PRETTY_NAME"] ?: "",
            bootTimestamp = bootTimestamp,
            blockDevices = blockDevices,
            activeSessions = sessions,
            recentLogins = recentLogins
        )
    }

    private fun parseBlockDevices(raw: String): List<BlockDevice> {
        return raw.lines().drop(1) // skip header
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split(WHITESPACE, limit = 6)
                if (parts.size < 2) return@mapNotNull null
                BlockDevice(
                    name = parts[0],
                    size = parts.getOrElse(1) { "" },
                    model = parts.getOrElse(2) { "" },
                    type = parts.getOrElse(3) { "" },
                    rotational = parts.getOrElse(4) { "0" } == "1",
                    transport = parts.getOrElse(5) { "" }
                )
            }
    }

    private fun parseSessions(raw: String): List<UserSession> {
        return raw.lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.trim().split(WHITESPACE, limit = 5)
            UserSession(
                user = parts.getOrElse(0) { "" },
                tty = parts.getOrElse(1) { "" },
                from = if (parts.size >= 5) parts[4] else "",
                loginTime = if (parts.size >= 4) "${parts.getOrElse(2) { "" }} ${parts.getOrElse(3) { "" }}" else ""
            )
        }
    }

    // ── Network interface detail parser ────────────────────────────────

    fun parseInterfaceDetails(output: String, serverId: String, intervalSec: Double = 2.0): List<InterfaceDetail> {
        val sections = splitSections(output)
        val netDev = sections["NET_DEV"] ?: ""
        val ipAddr = sections["IP_ADDR"] ?: ""
        val ipRoute = sections["IP_ROUTE"] ?: ""
        val netSpeed = sections["NET_SPEED"] ?: ""
        val netMac = sections["NET_MAC"] ?: ""

        val defaultIface = ipRoute.lines().firstOrNull()?.let { line ->
            DEV_IFACE.find(line)?.groupValues?.get(1)
        } ?: ""

        // Parse MAC/MTU/operstate
        val macMap = parseInterfaceMacInfo(netMac)

        // Parse speeds
        val speedMap = mutableMapOf<String, String>()
        netSpeed.lines().filter { it.isNotBlank() }.forEach { line ->
            val name = NET_SPEED_PATH.find(line)?.groupValues?.get(1) ?: return@forEach
            val mbps = line.substringAfterLast(":").trim().toIntOrNull() ?: return@forEach
            speedMap[name] = if (mbps >= 1000) "${mbps / 1000} Gbps" else "$mbps Mbps"
        }

        // Parse IPs
        val ipv4Map = mutableMapOf<String, String>()
        val ipv6Map = mutableMapOf<String, String>()
        ipAddr.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.trim().split(WHITESPACE)
            val ifaceName = parts.lastOrNull()?.trim() ?: return@forEach
            val family = parts.getOrNull(2) ?: return@forEach
            val addr = parts.getOrNull(3)?.substringBefore("/") ?: return@forEach
            when (family) {
                "inet" -> ipv4Map[ifaceName] = addr
                "inet6" -> if (!addr.startsWith("fe80")) ipv6Map[ifaceName] = addr
            }
        }

        // Parse /proc/net/dev for bytes + errors
        data class NetDevRow(val rxBytes: Long, val rxErrors: Long, val rxDropped: Long,
                             val txBytes: Long, val txErrors: Long, val txDropped: Long)
        val devMap = mutableMapOf<String, NetDevRow>()
        netDev.lines().filter { it.contains(":") && !it.contains("|") }.forEach { line ->
            val name = line.substringBefore(":").trim()
            val vals = line.substringAfter(":").trim().split(WHITESPACE)
            if (vals.size >= 16) {
                devMap[name] = NetDevRow(
                    rxBytes = vals[0].toLongOrNull() ?: 0,
                    rxErrors = vals[2].toLongOrNull() ?: 0,
                    rxDropped = vals[3].toLongOrNull() ?: 0,
                    txBytes = vals[8].toLongOrNull() ?: 0,
                    txErrors = vals[10].toLongOrNull() ?: 0,
                    txDropped = vals[11].toLongOrNull() ?: 0
                )
            }
        }

        val allNames = (devMap.keys + macMap.keys + ipv4Map.keys).filter { it != "lo" }.distinct()

        return allNames.map { name ->
            val dev = devMap[name]
            val mac = macMap[name]
            InterfaceDetail(
                name = name,
                macAddress = mac?.macAddress ?: "",
                mtu = mac?.mtu ?: 0,
                operState = mac?.operState ?: "",
                ipv4 = ipv4Map[name],
                ipv6 = ipv6Map[name],
                speed = speedMap[name] ?: "",
                isDefaultRoute = name == defaultIface,
                rxBytes = dev?.rxBytes ?: 0,
                txBytes = dev?.txBytes ?: 0,
                rxErrors = dev?.rxErrors ?: 0,
                txErrors = dev?.txErrors ?: 0,
                rxDropped = dev?.rxDropped ?: 0,
                txDropped = dev?.txDropped ?: 0
            )
        }.sortedByDescending { it.isDefaultRoute }
    }

    // ── Backward-compatible legacy parser ──────────────────────────────

    @Suppress("DEPRECATION")
    fun parseFastPoll(output: String, serverId: String): ServerStats {
        val overview = parseOverview(output, serverId)
        val interfaces = parseNetworkInterfaces(output, serverId)

        return ServerStats(
            uptime = formatUptime(overview.uptimeSeconds),
            loadAvg = "${overview.load1}, ${overview.load5}, ${overview.load15}",
            memoryUsage = overview.memUsagePercent.toFloat() / 100f,
            cpuUsage = (overview.cpuUsagePercent / 100.0).toFloat(),
            diskUsage = overview.rootUsagePercent.toFloat() / 100f,
            networkTx = "0 B/s",
            networkRx = "0 B/s",
            temperature = overview.cpuTemperature?.currentCelsius?.toFloat() ?: 0f,
            networkInterfaces = interfaces
        )
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private data class CpuFullResult(val totalUsage: Double, val cores: List<CpuCoreLoad>)

    private data class MemResult(
        val total: Long, val used: Long, val free: Long, val available: Long?,
        val buffers: Long, val cached: Long, val swapTotal: Long, val swapUsed: Long
    )

    private fun parseCpuFull(serverId: String, data: String): CpuFullResult {
        val prevTicks = cpuTickStates[serverId] ?: emptyMap()
        val newTicks = mutableMapOf<String, CpuTickSnapshot>()
        val cores = mutableListOf<CpuCoreLoad>()
        var totalUsage = 0.0

        for (line in data.lines()) {
            val parts = line.split(WHITESPACE).filter { it.isNotBlank() }
            if (parts.isEmpty() || !parts[0].startsWith("cpu")) continue
            if (parts.size < 8) continue

            val label = parts[0]
            val snap = CpuTickSnapshot(
                user = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                nice = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                system = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
                idle = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
                iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
                irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
                softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L,
                steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L
            )
            newTicks[label] = snap

            val prev = prevTicks[label]
            val usage = if (prev != null) {
                val deltaTotal = snap.total - prev.total
                val deltaIdle = snap.idleTotal - prev.idleTotal
                if (deltaTotal > 0) ((deltaTotal - deltaIdle).toDouble() / deltaTotal * 100.0).coerceIn(0.0, 100.0) else 0.0
            } else {
                if (snap.total > 0) ((snap.total - snap.idleTotal).toDouble() / snap.total * 100.0).coerceIn(0.0, 100.0) else 0.0
            }

            if (label == "cpu") {
                totalUsage = usage
            } else {
                val coreId = label.removePrefix("cpu").toIntOrNull() ?: continue
                cores.add(CpuCoreLoad(id = coreId, usagePercent = usage))
            }
        }

        cpuTickStates[serverId] = newTicks
        cores.sortBy { it.id }
        return CpuFullResult(totalUsage, cores)
    }

    private fun parseMemoryFull(data: String): MemResult {
        val map = mutableMapOf<String, Long>()
        for (line in data.lines()) {
            val parts = line.split(":")
            if (parts.size < 2) continue
            val key = parts[0].trim()
            val value = parts[1].trim().split(WHITESPACE).firstOrNull()?.toLongOrNull() ?: 0L
            map[key] = value * 1024 // kB → bytes
        }
        val total = map["MemTotal"] ?: 0L
        val free = map["MemFree"] ?: 0L
        val buffers = map["Buffers"] ?: 0L
        val cached = (map["Cached"] ?: 0L) + (map["SReclaimable"] ?: 0L)
        val available = map["MemAvailable"] ?: (free + buffers + cached)
        val used = maxOf(0L, total - available)
        val swapTotal = map["SwapTotal"] ?: 0L
        val swapFree = map["SwapFree"] ?: 0L
        return MemResult(total, used, free, map["MemAvailable"], buffers, cached, swapTotal, maxOf(0L, swapTotal - swapFree))
    }

    private fun parseMounts(dfRaw: String): List<DiskVolume> {
        return dfRaw.lines().drop(1).mapNotNull { line ->
            val parts = line.split(WHITESPACE)
            if (parts.size < 7) return@mapNotNull null
            val fsType = parts[1]
            if (isPseudoFilesystem(fsType)) return@mapNotNull null
            val total = parts[2].toLongOrNull() ?: return@mapNotNull null
            val used = parts[3].toLongOrNull() ?: return@mapNotNull null
            val mountPoint = parts[6]
            DiskVolume(mountPoint = mountPoint, totalBytes = total, usedBytes = used, fsType = fsType)
        }
    }

    private fun parseInterfaceMacInfo(raw: String): Map<String, InterfaceMacInfo> {
        return raw.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null

                val parts = trimmed.split(":")
                if (parts.size < 4) return@mapNotNull null

                val name = parts.first()
                val mtu = parts[parts.lastIndex - 1].toIntOrNull() ?: return@mapNotNull null
                val operState = parts.last()
                val macAddress = parts.subList(1, parts.lastIndex - 1).joinToString(":")
                if (macAddress.isBlank()) return@mapNotNull null

                name to InterfaceMacInfo(macAddress = macAddress, mtu = mtu, operState = operState)
            }
            .toMap()
    }

    private fun parseNetDevCounters(raw: String): Map<String, Pair<Long, Long>> {
        val result = mutableMapOf<String, Pair<Long, Long>>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Inter") || trimmed.startsWith("face")) continue
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx < 0) continue
            val name = trimmed.substring(0, colonIdx).trim()
            val numbers = trimmed.substring(colonIdx + 1).trim()
                .split(WHITESPACE).mapNotNull { it.toLongOrNull() }
            if (numbers.size >= 9) {
                result[name] = numbers[0] to numbers[8]
            }
        }
        return result
    }

    private fun parseIpAddrMap(raw: String): Map<String, Pair<String?, String?>> {
        val result = mutableMapOf<String, Pair<String?, String?>>()
        for (line in raw.lines()) {
            val parts = line.split(WHITESPACE)
            if (parts.size < 4) continue
            if (!parts[0].trimEnd(':').all { it.isDigit() }) continue
            val name = parts[1]
            val family = parts[2]
            val addr = parts[3].substringBefore("/")
            val entry = result[name] ?: (null to null)
            result[name] = when (family) {
                "inet" -> addr to entry.second
                "inet6" -> entry.first to addr
                else -> entry
            }
        }
        return result
    }

    private fun parseNetworkSpeeds(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in raw.lines()) {
            val parts = line.split(":")
            if (parts.size < 2) continue
            val pathParts = parts[0].split("/")
            val netIdx = pathParts.indexOfLast { it == "net" }
            if (netIdx < 0 || netIdx + 1 >= pathParts.size) continue
            result[pathParts[netIdx + 1]] = parts[1].trim()
        }
        return result
    }

    private fun parseDefaultRoute(raw: String): String? {
        for (line in raw.lines()) {
            val parts = line.split(WHITESPACE)
            val devIdx = parts.indexOf("dev")
            if (devIdx >= 0 && devIdx + 1 < parts.size) return parts[devIdx + 1]
        }
        return null
    }

    internal fun splitSections(output: String): Map<String, String> {
        val out = mutableMapOf<String, StringBuilder>()
        var currentKey: String? = null

        for (raw in output.lineSequence()) {
            val line = raw.trimEnd()
            val match = HEADER_REGEX.matchEntire(line.trim())
            if (match != null) {
                currentKey = match.groupValues[1].uppercase()
                out.putIfAbsent(currentKey, StringBuilder())
                continue
            }
            if (currentKey != null) {
                out.getValue(currentKey).appendLine(line)
            }
        }

        return out.mapValues { it.value.toString().trim() }
    }

    private fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        return if (days > 0) "$days days, $hours h" else "$hours h, $mins m"
    }
}
