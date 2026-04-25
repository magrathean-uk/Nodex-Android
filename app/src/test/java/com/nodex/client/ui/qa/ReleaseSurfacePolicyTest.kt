package com.nodex.client.ui.qa

import com.nodex.client.core.network.ssh.HostKeyAuditStore
import com.nodex.client.core.network.ssh.HostKeyInfo
import com.nodex.client.core.network.ssh.HostKeyPromptManager
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSurfacePolicyTest {

    @Test
    fun hostKeyTrustPromptBlocksUntilExplicitDecision() = runBlocking {
        val manager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        manager.requestTrust(
            info = HostKeyInfo(
                hostname = "example.invalid",
                port = 22,
                keyType = "ssh-ed25519",
                fingerprint = "SHA256:test",
                publicKey = "AAAATEST"
            ),
            onTrust = {},
            onReject = {}
        )

        assertNotNull(manager.activePrompt.value)
        manager.rejectActivePrompt()
        assertTrue(manager.activePrompt.value == null)
    }
}
