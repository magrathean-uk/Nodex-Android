package com.nodex.client.core.network.parser

import org.junit.Assert.*
import org.junit.Test

class StatsParserTest {

    @Test
    fun `parseCpu returns reasonable usage based on deltas`() {
        val parser = StatsParser()
        val sample1 = """
[CPU_STAT]
cpu  100 0 100 800 0 0 0 0 0 0
""".trimIndent()
        val sample2 = """
[CPU_STAT]
cpu  200 0 100 900 0 0 0 0 0 0
""".trimIndent()

        parser.parseFastPoll(sample1, "serverA")
        val stats = parser.parseFastPoll(sample2, "serverA")

        assertTrue("CPU usage should be between 0 and 1", stats.cpuUsage in 0f..1f)
        assertTrue("CPU usage should be close to 0.5", stats.cpuUsage > 0.4f && stats.cpuUsage < 0.6f)
    }

    @Test
    fun `parseFastPoll handles multiple sections`() {
        val parser = StatsParser()
        val sample = """
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
1: lo    inet 127.0.0.1/8 scope host lo

[NET_DEV]
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    lo: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0
""".trimIndent()

        val stats = parser.parseFastPoll(sample, "serverB")

        assertTrue("Memory usage should be > 0", stats.memoryUsage > 0f)
        assertTrue("Disk usage should be ~50%", stats.diskUsage > 0.45f && stats.diskUsage < 0.55f)
        assertTrue("Temperature should be parsed", stats.temperature > 0f)
    }

    @Test
    fun `parseServices parses systemctl output`() {
        val parser = StatsParser()
        val output = """
[SERVICES]
  nginx.service              loaded active running A high performance web server
  ssh.service                loaded active running OpenBSD Secure Shell server
  failed.service             loaded failed failed  A broken service
""".trimIndent()

        val services = parser.parseServices(output)
        assertEquals(3, services.size)
        assertTrue(services.any { it.name == "nginx.service" && it.activeState == "active" })
        assertTrue(services.any { it.name == "failed.service" && it.activeState == "failed" })
    }

    @Test
    fun `parseProcesses parses ps output`() {
        val parser = StatsParser()
        // Parser expects: pid ppid user cpu% mem% command
        val output = """
PID PPID USER CPU MEM COMMAND
1 0 root 0.1 0.2 /sbin/init
100 1 www-data 25.0 5.0 nginx
""".trimIndent()

        val processes = parser.parseProcesses(output)
        assertEquals(2, processes.size)
        assertEquals("root", processes[0].user)
        assertEquals(1, processes[0].pid)
        assertEquals(25.0, processes[1].cpuPercent, 0.01)
        assertEquals("nginx", processes[1].command)
    }

    @Test
    fun `parseProcesses keeps first row when no header is present`() {
        val parser = StatsParser()
        val output = """
1 0 root 14.5 0.8 /usr/lib/systemd/systemd
100 1 www-data 25.0 5.0 nginx: worker process
""".trimIndent()

        val processes = parser.parseProcesses(output)
        assertEquals(2, processes.size)
        assertEquals(1, processes.first().pid)
        assertEquals("/usr/lib/systemd/systemd", processes.first().command)
        assertEquals("nginx: worker process", processes.last().command)
    }

    @Test
    fun `parseFastPoll handles empty input gracefully`() {
        val parser = StatsParser()
        val stats = parser.parseFastPoll("", "empty-server")
        assertEquals(0f, stats.cpuUsage)
        assertEquals(0f, stats.memoryUsage)
    }

    @Test
    fun `parseNetworkInterfaces uses operstate for isUp`() {
        val parser = StatsParser()
        val sample = """
[IP_ADDR]
2: eth0    inet 192.168.1.10/24 scope global eth0
3: wlan0    inet 192.168.1.20/24 scope global wlan0

[NET_DEV]
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    eth0: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0
   wlan0: 500 0 0 0 0 0 0 0 600 0 0 0 0 0 0 0

[IP_ROUTE]
default via 192.168.1.1 dev eth0 proto dhcp src 192.168.1.10 metric 100

[NET_SPEED]
/sys/class/net/eth0/speed:1000

[NET_MAC]
eth0:52:54:00:ab:cd:ef:1500:up
wlan0:02:11:22:33:44:55:1500:down
""".trimIndent()

        val interfaces = parser.parseNetworkInterfaces(sample, "server-net")

        assertEquals(2, interfaces.size)
        assertTrue(interfaces.first { it.name == "eth0" }.isUp)
        assertFalse(interfaces.first { it.name == "wlan0" }.isUp)
    }

    @Test
    fun `parseInterfaceDetails parses mac mtu and operstate`() {
        val parser = StatsParser()
        val sample = """
[IP_ADDR]
2: eth0    inet 192.168.1.10/24 scope global eth0

[NET_DEV]
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    eth0: 1000 0 1 2 0 0 0 0 2000 0 3 4 0 0 0 0

[IP_ROUTE]
default via 192.168.1.1 dev eth0 proto dhcp src 192.168.1.10 metric 100

[NET_SPEED]
/sys/class/net/eth0/speed:1000

[NET_MAC]
eth0:52:54:00:ab:cd:ef:1500:up
""".trimIndent()

        val details = parser.parseInterfaceDetails(sample, "server-details")

        assertEquals(1, details.size)
        with(details.single()) {
            assertEquals("eth0", name)
            assertEquals("52:54:00:ab:cd:ef", macAddress)
            assertEquals(1500, mtu)
            assertEquals("up", operState)
            assertTrue(isDefaultRoute)
            assertEquals(1, rxErrors)
            assertEquals(3, txErrors)
        }
    }

    @Test
    fun `parseNetworkInterfaces clamps rate when counters roll over`() {
        val parser = StatsParser()
        val sample1 = """
[NET_DEV]
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    eth0: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0
""".trimIndent()
        val sample2 = """
[NET_DEV]
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    eth0: 100 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0
""".trimIndent()

        parser.parseNetworkInterfaces(sample1, "server-rollover", intervalSeconds = 2.0)
        val interfaces = parser.parseNetworkInterfaces(sample2, "server-rollover", intervalSeconds = 2.0)

        assertEquals(1, interfaces.size)
        assertEquals(0.0, interfaces.single().rxBytesPerSec, 0.0)
        assertEquals(0.0, interfaces.single().txBytesPerSec, 0.0)
    }

    @Test
    fun `parseOverview extracts memory details`() {
        val parser = StatsParser()
        val sample = """
[MEMINFO]
MemTotal:       8000000
MemFree:        2000000
MemAvailable:   4000000
Buffers:         500000
Cached:         1500000
SwapTotal:      2000000
SwapFree:       1500000

[CPU_STAT]
cpu  100 0 100 800 0 0 0 0 0 0

[UPTIME]
86400.00 0.00

[LOAD]
1.50 1.00 0.50 5/200 1234

[DF]
Filesystem     Type  1024-blocks  Used Available Capacity Mounted
/dev/sda1      ext4   100000000 60000000  40000000   60% /

[THERMAL]

[IP_ADDR]
1: lo    inet 127.0.0.1/8 scope host lo

[NET_DEV]
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    lo: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0
""".trimIndent()

        val overview = parser.parseOverview(sample, "memtest")

        assertNotNull(overview)
        assertTrue("Memory usage should be around 50%", overview.memUsagePercent > 40 && overview.memUsagePercent < 60)
        assertTrue("Uptime should be ~1 day", overview.uptimeSeconds > 80000)
        assertTrue("Load1 should be parsed", overview.load1 > 0)
    }

    @Test
    fun `parseJournalEvents handles json lines gracefully`() {
        val parser = StatsParser()
        val events = parser.parseJournalEvents("")
        assertTrue("Empty input should return empty list", events.isEmpty())

        val malformed = parser.parseJournalEvents("not json at all\nanother line")
        assertTrue("Malformed input should return empty list", malformed.isEmpty())
    }

    @Test
    fun `parseCapabilities detects installed tools and container runtime flags`() {
        val parser = StatsParser()
        val output = """
5.15.0-91-generic
HAS_SYSSTAT
HAS_ETHTOOL
HAS_DOCKER
DOCKER_NEEDS_SUDO
IS_PODMAN
""".trimIndent()

        val caps = parser.parseCapabilities(output)
        assertTrue(caps.hasSysstat)
        assertFalse(caps.hasSensors)
        assertTrue(caps.hasEthtool)
        assertTrue(caps.hasDocker)
        assertTrue(caps.dockerNeedsSudo)
        assertTrue(caps.isPodman)
        assertEquals("podman", caps.containerRuntimeBinary)
        assertEquals("Podman", caps.containerRuntimeLabel)
        assertNotNull(caps.kernel)
    }

    @Test
    fun `parseSystemInfo extracts hardware details`() {
        val parser = StatsParser()
        val output = """
[CPU_INFO]
Architecture:            x86_64
CPU(s):                  8
Model name:              Intel(R) Core(TM) i7-9700K
Core(s) per socket:      4
Socket(s):               1
Thread(s) per core:      2

[UNAME]
Linux myhost 5.15.0-91-generic #101-Ubuntu SMP x86_64

[HOSTNAME]
myhost

[OS_RELEASE]
PRETTY_NAME="Ubuntu 22.04.3 LTS"
ID=ubuntu
VERSION_ID="22.04"

[BOOT_TIME]
btime 1700000000
""".trimIndent()

        val info = parser.parseSystemInfo(output)
        assertNotNull(info)
        assertEquals("Intel(R) Core(TM) i7-9700K", info.cpuModel)
        assertEquals(4, info.cpuCores)
        assertEquals("x86_64", info.architecture)
        assertEquals("myhost", info.hostname)
    }
}
