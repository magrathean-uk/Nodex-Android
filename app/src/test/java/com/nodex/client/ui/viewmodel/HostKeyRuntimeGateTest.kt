package com.nodex.client.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyRuntimeGateTest {

    @Test
    fun pendingDecisionBlocksRepeatedPromptRequests() {
        val gate = HostKeyRuntimeGate()

        assertTrue(gate.markPending("server-1"))
        assertFalse(gate.markPending("server-1"))
        assertEquals("Review the host key prompt to continue.", gate.blockedMessage("server-1"))
    }

    @Test
    fun rejectBlocksPollingUntilServerConfigChanges() {
        val gate = HostKeyRuntimeGate()

        gate.markPending("server-1")
        gate.markRejected("server-1")

        assertEquals("Host key rejected.", gate.blockedMessage("server-1"))

        gate.clear("server-1")
        assertNull(gate.blockedMessage("server-1"))
    }
}
