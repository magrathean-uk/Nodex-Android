package com.nodex.client.core.network.ssh

import com.nodex.client.ui.viewmodel.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostKeyPromptManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `trusting active prompt runs callback and clears prompt`() = runTest {
        val manager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        var trusted = false
        val info = HostKeyInfo(
            hostname = "demo.local",
            port = 22,
            keyType = "ssh-ed25519",
            fingerprint = "SHA256:abc",
            publicKey = "pub"
        )

        manager.requestTrust(
            info = info,
            onTrust = { trusted = true }
        )
        manager.trustActivePrompt()

        assertTrue(trusted)
        assertNull(manager.activePrompt.value)
    }

    @Test
    fun `rejecting active prompt runs reject callback and clears prompt`() = runTest {
        val manager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        var rejected = false
        val info = HostKeyInfo(
            hostname = "demo.local",
            port = 22,
            keyType = "ssh-ed25519",
            fingerprint = "SHA256:abc",
            publicKey = "pub"
        )

        manager.requestTrust(
            info = info,
            onTrust = {},
            onReject = { rejected = true }
        )
        manager.rejectActivePrompt()

        assertTrue(rejected)
        assertNull(manager.activePrompt.value)
    }

    @Test
    fun `rejecting active prompt audits the rejection`() = runTest {
        val auditStore = mockk<HostKeyAuditStore>(relaxed = true)
        val manager = HostKeyPromptManager(auditStore)
        val info = HostKeyInfo(
            hostname = "demo.local",
            port = 22,
            keyType = "ssh-ed25519",
            fingerprint = "SHA256:abc",
            publicKey = "pub"
        )

        manager.requestTrust(
            info = info,
            onTrust = {},
            onReject = {}
        )
        manager.rejectActivePrompt()

        coVerify(exactly = 1) { auditStore.recordRejected(info) }
        assertNull(manager.activePrompt.value)
    }

    @Test
    fun `showMismatch exposes old and new fingerprints`() = runTest {
        val manager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))

        manager.showMismatch(
            hostname = "demo.local",
            port = 22,
            oldFingerprint = "SHA256:old",
            newFingerprint = "SHA256:new"
        )

        val mismatch = manager.activeMismatch.value
        requireNotNull(mismatch)
        assertEquals("demo.local", mismatch.hostname)
        assertEquals("SHA256:old", mismatch.oldFingerprint)
        assertEquals("SHA256:new", mismatch.newFingerprint)

        manager.clearMismatch()
        assertNull(manager.activeMismatch.value)
    }
}
