package com.nodex.client.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicKeyFingerprintsTest {

    @Test
    fun `sha256 fingerprint uses ssh style prefix and no padding`() {
        val fingerprint = PublicKeyFingerprints.sha256(byteArrayOf(1, 2, 3, 4))

        assertEquals("SHA256:n2SnR+G5fxMfq7a0Rylsm28CAeefs8U1bmx36JtqgGo", fingerprint)
    }
}
