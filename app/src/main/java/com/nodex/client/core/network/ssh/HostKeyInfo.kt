package com.nodex.client.core.network.ssh

data class HostKeyInfo(
    val hostname: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    val publicKey: String
) {
    val hostnamePort: String
        get() = "$hostname:$port"
}
