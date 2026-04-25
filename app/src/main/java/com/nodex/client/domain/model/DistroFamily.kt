package com.nodex.client.domain.model

enum class DistroFamily(val displayName: String) {
    DEBIAN("Debian"),
    FEDORA("Fedora"),
    RHEL("RHEL"),
    SUSE("SUSE"),
    OPENSUSE("openSUSE"),
    ARCH("Arch");

    companion object {
        fun fromOsId(id: String): DistroFamily = when (id.lowercase()) {
            "debian", "ubuntu", "pop", "mint", "elementary", "raspbian", "kali" -> DEBIAN
            "fedora" -> FEDORA
            "centos", "almalinux", "rocky", "rhel", "ol", "amzn" -> RHEL
            "suse", "sles", "sled" -> SUSE
            "opensuse", "opensuse-leap", "opensuse-tumbleweed" -> OPENSUSE
            "arch", "manjaro", "endeavouros" -> ARCH
            else -> DEBIAN
        }
    }
}
