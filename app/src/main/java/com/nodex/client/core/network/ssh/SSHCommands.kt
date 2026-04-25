package com.nodex.client.core.network.ssh

object SSHCommands {

    private const val COMMON_PATH = "/usr/local/sbin:/usr/sbin:/sbin:/snap/bin:/usr/bin:/bin"

    private fun dockerDetectSnippet(): String = """
        DOCKER_BIN=""
        if command -v docker >/dev/null 2>&1; then DOCKER_BIN="docker";
        elif command -v podman >/dev/null 2>&1; then DOCKER_BIN="podman";
        elif [ -x /snap/bin/docker ]; then DOCKER_BIN="/snap/bin/docker";
        elif [ -x /usr/bin/docker ]; then DOCKER_BIN="/usr/bin/docker";
        elif [ -x /usr/local/bin/docker ]; then DOCKER_BIN="/usr/local/bin/docker";
        fi
    """.trimIndent()

    private fun publicIpSnippet(enabled: Boolean): String {
        return if (!enabled) {
            """
            printf '\n[PUBLIC_IP]\n';
            printf '\n';
            """.trimIndent()
        } else {
            """
            printf '\n[PUBLIC_IP]\n';
            ip='';
            for url in https://api.ipify.org https://ipv4.icanhazip.com https://ifconfig.me/ip https://checkip.amazonaws.com; do
              [ -n "${'$'}ip" ] && break;
              if command -v curl >/dev/null 2>&1; then
                candidate=${'$'}(curl -4 -fsS --connect-timeout 2 --max-time 3 "${'$'}url" 2>/dev/null | tr -d '\r\n' | head -c 15);
              elif command -v wget >/dev/null 2>&1; then
                candidate=${'$'}(wget -4 -qO- --timeout=3 "${'$'}url" 2>/dev/null | tr -d '\r\n' | head -c 15);
              else
                candidate='';
              fi
              if printf '%s' "${'$'}candidate" | grep -Eq '^([0-9]{1,3}\.){3}[0-9]{1,3}${'$'}'; then
                ip="${'$'}candidate";
              fi
            done
            if [ -z "${'$'}ip" ] && command -v dig >/dev/null 2>&1; then
              candidate=${'$'}(dig +short +time=2 +tries=1 -4 myip.opendns.com @resolver1.opendns.com 2>/dev/null | tr -d '\r\n' | head -c 15);
              if printf '%s' "${'$'}candidate" | grep -Eq '^([0-9]{1,3}\.){3}[0-9]{1,3}${'$'}'; then
                ip="${'$'}candidate";
              fi
            fi
            printf '%s\n' "${'$'}{ip:-}";
            """.trimIndent()
        }
    }

    fun detectDistro(): String = "cat /etc/os-release"

    fun fastPoll(): String = """
        export PATH=${'$'}PATH:$COMMON_PATH
        printf '[UPTIME]\n';        cat /proc/uptime;
        printf '\n[LOAD]\n';        cat /proc/loadavg;
        printf '\n[MEMINFO]\n';     cat /proc/meminfo;
        printf '\n[CPU_STAT]\n';    cat /proc/stat;
        printf '\n[NET_DEV]\n';     cat /proc/net/dev;
        printf '\n[IP_ADDR]\n';     ip -o addr show;
        printf '\n[IP_ROUTE]\n';    ip route show default;
        printf '\n[DISKSTATS]\n';   cat /proc/diskstats;
        printf '\n[DF]\n';          df -B1 -PT 2>/dev/null;
        printf '\n[THERMAL]\n';     cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null || true;
        printf '\n[NET_SPEED]\n';   grep -H "" /sys/class/net/*/speed 2>/dev/null || true;
        printf '\n[NET_MAC]\n';     for d in /sys/class/net/*; do n=${'$'}(basename "${'$'}d"); [ "${'$'}n" != "lo" ] && echo "${'$'}n:${'$'}(cat "${'$'}d/address" 2>/dev/null):${'$'}(cat "${'$'}d/mtu" 2>/dev/null):${'$'}(cat "${'$'}d/operstate" 2>/dev/null)"; done 2>/dev/null || true;
    """.trimIndent()

    fun slowPoll(publicIpEnabled: Boolean = true): String = publicIpSnippet(publicIpEnabled)

    fun processes(): String = """
        ps axo pid,ppid,user,%cpu,%mem,comm --sort=-%cpu --no-headers | head -30
    """.trimIndent()

    fun services(): String = """
        printf '[SERVICES]\n';
        systemctl list-units --type=service --all --no-legend --no-pager || true;

        printf '\n[SERVICES_FAILED]\n';
        systemctl --failed --type=service --no-legend --no-pager || true;
    """.trimIndent()

    fun recentJournal(minutesBack: Int): String = """
        journalctl --since="${minutesBack} minutes ago" --output=json --no-pager --lines=400 2>/dev/null || true
    """.trimIndent()

    fun serviceJournal(unit: String, lines: Int): String {
        val escaped = unit.replace("'", "'\"'\"'")
        return "journalctl -xeu '$escaped' --no-pager --lines=${lines.coerceAtLeast(1)}"
    }

    fun capabilities(): String = """
        export PATH=${'$'}PATH:$COMMON_PATH
        set +e
        uname -r || true
        command -v lsblk >/dev/null 2>&1 && echo HAS_LSBLK || true
        lsblk -J >/dev/null 2>&1 && echo HAS_LSBLK_JSON || true
        ip -j addr >/dev/null 2>&1 && echo HAS_IP_JSON || true
        command -v iostat >/dev/null 2>&1 && echo HAS_SYSSTAT || true
        command -v sensors >/dev/null 2>&1 && echo HAS_SENSORS || true
        command -v nvme >/dev/null 2>&1 && echo HAS_NVME || true
        command -v ethtool >/dev/null 2>&1 && echo HAS_ETHTOOL || true
        ${dockerDetectSnippet()}
        if [ -n "${'$'}DOCKER_BIN" ]; then
          echo HAS_DOCKER
          "${'$'}DOCKER_BIN" --version 2>/dev/null | grep -qi podman && echo IS_PODMAN
          [ "${'$'}DOCKER_BIN" = "podman" ] && echo IS_PODMAN
          if ! "${'$'}DOCKER_BIN" info >/dev/null 2>&1; then
            echo DOCKER_NEEDS_SUDO
          fi
        fi
        true
    """.trimIndent()

    fun systemInfo(): String = """
        export PATH=${'$'}PATH:$COMMON_PATH
        printf '[HOSTNAME]\n';      hostname 2>/dev/null || cat /etc/hostname 2>/dev/null || true;
        printf '\n[OS_RELEASE]\n';  cat /etc/os-release 2>/dev/null || true;
        printf '\n[UNAME]\n';       uname -a 2>/dev/null || true;
        printf '\n[CPU_INFO]\n';    lscpu 2>/dev/null || grep -m1 'model name' /proc/cpuinfo 2>/dev/null || true;
        printf '\n[BOOT_TIME]\n';   grep btime /proc/stat 2>/dev/null || true;
        printf '\n[BLOCK_DEV]\n';   lsblk -d -o NAME,SIZE,MODEL,TYPE,ROTA,TRAN 2>/dev/null || cat /proc/partitions 2>/dev/null || true;
        printf '\n[WHO]\n';         who 2>/dev/null || true;
        printf '\n[LAST]\n';        last -n 10 -w 2>/dev/null || true;
    """.trimIndent()

    fun temperature(): String = """
        export PATH=${'$'}PATH:$COMMON_PATH
        printf '[SENSORS]\n';
        if command -v sensors >/dev/null 2>&1; then
          sensors -j 2>/dev/null || sensors 2>/dev/null || true;
        else
          echo 'NO_SENSORS';
        fi
        printf '\n[THERMAL_ZONES]\n';
        for zone in /sys/class/thermal/thermal_zone*; do
          if [ -d "${'$'}zone" ]; then
            name=${'$'}(cat "${'$'}zone/type" 2>/dev/null || echo "unknown");
            temp=${'$'}(cat "${'$'}zone/temp" 2>/dev/null || echo "0");
            echo "${'$'}name:${'$'}temp";
          fi
        done
        printf '\n[CPU_TEMP]\n';
        cat /sys/class/hwmon/hwmon*/temp*_input 2>/dev/null | head -1 || true;
    """.trimIndent()

    fun slowPollBatch(alertLookbackMinutes: Int, publicIpEnabled: Boolean = true): String = """
        export PATH=${'$'}PATH:$COMMON_PATH
        set +e

        printf '[SERVICES]\n';
        systemctl list-units --type=service --all --no-legend --no-pager 2>/dev/null || true;
        printf '\n[SERVICES_FAILED]\n';
        systemctl --failed --type=service --no-legend --no-pager 2>/dev/null || true;

        printf '\n[PROCESSES]\n';
        ps axo pid,ppid,user,%cpu,%mem,comm --sort=-%cpu --no-headers 2>/dev/null | head -30;

        printf '\n[JOURNAL]\n';
        journalctl --since="${alertLookbackMinutes} minutes ago" --output=json --no-pager --lines=400 2>/dev/null || true;

        ${publicIpSnippet(publicIpEnabled)}

        printf '\n[HOSTNAME]\n';      hostname 2>/dev/null || cat /etc/hostname 2>/dev/null || true;
        printf '\n[OS_RELEASE]\n';    cat /etc/os-release 2>/dev/null || true;
        printf '\n[UNAME]\n';         uname -a 2>/dev/null || true;
        printf '\n[CPU_INFO]\n';      lscpu 2>/dev/null || grep -m1 'model name' /proc/cpuinfo 2>/dev/null || true;
        printf '\n[BOOT_TIME]\n';     grep btime /proc/stat 2>/dev/null || true;
        printf '\n[BLOCK_DEV]\n';     lsblk -d -o NAME,SIZE,MODEL,TYPE,ROTA,TRAN 2>/dev/null || cat /proc/partitions 2>/dev/null || true;
        printf '\n[WHO]\n';           who 2>/dev/null || true;
        printf '\n[LAST]\n';          last -n 10 -w 2>/dev/null || true;

        printf '\n[CAPABILITIES]\n';
        uname -r || true;
        command -v lsblk >/dev/null 2>&1 && echo HAS_LSBLK || true;
        lsblk -J >/dev/null 2>&1 && echo HAS_LSBLK_JSON || true;
        ip -j addr >/dev/null 2>&1 && echo HAS_IP_JSON || true;
        command -v iostat >/dev/null 2>&1 && echo HAS_SYSSTAT || true;
        command -v sensors >/dev/null 2>&1 && echo HAS_SENSORS || true;
        command -v nvme >/dev/null 2>&1 && echo HAS_NVME || true;
        command -v ethtool >/dev/null 2>&1 && echo HAS_ETHTOOL || true;
        ${dockerDetectSnippet()}
        if [ -n "${'$'}DOCKER_BIN" ]; then
          echo HAS_DOCKER
          "${'$'}DOCKER_BIN" --version 2>/dev/null | grep -qi podman && echo IS_PODMAN
          [ "${'$'}DOCKER_BIN" = "podman" ] && echo IS_PODMAN
          if ! "${'$'}DOCKER_BIN" info >/dev/null 2>&1; then
            echo DOCKER_NEEDS_SUDO
          fi
        fi
        true;
    """.trimIndent()

    fun dockerList(binary: String): String =
        "export PATH=${'$'}PATH:$COMMON_PATH; ${binary.trim()} ps -a --format '{{json .}}' 2>&1"

    fun dockerStats(binary: String): String =
        "export PATH=${'$'}PATH:$COMMON_PATH; ${binary.trim()} stats --no-stream --format '{{json .}}' 2>&1"

    fun dockerAction(binary: String, action: String, containerID: String): String {
        val safeAction = action.lowercase().takeIf { it in setOf("start", "stop", "restart", "remove", "rm") } ?: "status"
        val safeId = sanitizeContainerId(containerID)
        return "export PATH=${'$'}PATH:$COMMON_PATH; ${binary.trim()} ${safeAction} ${safeId.ifBlank { "invalid" }} 2>&1"
    }

    fun dockerLogs(binary: String, containerID: String, lines: Int = 200): String {
        val safeId = sanitizeContainerId(containerID)
        return "export PATH=${'$'}PATH:$COMMON_PATH; ${binary.trim()} logs --tail ${lines.coerceIn(1, 500)} --timestamps ${safeId.ifBlank { "invalid" }} 2>&1"
    }

    private fun sanitizeContainerId(value: String): String =
        value.filter { it.isLetterOrDigit() || it in "-_." }
}
