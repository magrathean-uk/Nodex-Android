package com.nodex.client.core.network.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SshCommandRequestTest {

    @Test
    fun `sudo request keeps the password out of the shell command`() {
        val request = SshCommandRequest.sudo(
            command = "systemctl restart nginx",
            password = "sup3r-secret"
        )

        assertEquals("sudo -S -p '' systemctl restart nginx", request.command)
        assertFalse(request.command.contains("sup3r-secret"))
        assertEquals("sup3r-secret\n", request.stdin)
    }

    @Test
    fun `plain request trims outer whitespace`() {
        val request = SshCommandRequest.plain("  uname -a  ")

        assertEquals("uname -a", request.command)
        assertEquals(null, request.stdin)
    }
}
