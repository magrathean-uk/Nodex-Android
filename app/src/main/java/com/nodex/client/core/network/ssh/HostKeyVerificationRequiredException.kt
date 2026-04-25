package com.nodex.client.core.network.ssh

class HostKeyVerificationRequiredException(
    val hostKeyInfo: HostKeyInfo
) : Exception("Host key verification required for ${hostKeyInfo.hostname}:${hostKeyInfo.port}")
