package com.nodex.client.core.security

import java.security.MessageDigest
import java.util.Base64

object PublicKeyFingerprints {
    fun sha256(encodedPublicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}
