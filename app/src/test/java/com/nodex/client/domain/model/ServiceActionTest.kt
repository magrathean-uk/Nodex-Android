package com.nodex.client.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceActionTest {

    @Test
    fun `commandFor keeps safe service name`() {
        assertEquals(
            "systemctl restart nginx.service",
            ServiceAction.RESTART.commandFor("nginx.service")
        )
    }

    @Test
    fun `commandFor rejects unsafe service name`() {
        assertEquals(
            "echo 'invalid service name'",
            ServiceAction.RESTART.commandFor("nginx.service; rm -rf /")
        )
    }
}
