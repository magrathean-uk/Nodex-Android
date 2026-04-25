package com.nodex.client.ui.serveredit

import com.nodex.client.domain.model.AuthType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerEditorStateTest {

    @Test
    fun `password auth requires a password`() {
        val state = ServerEditorState(
            name = "Debian",
            hostname = " 192.168.1.10 ",
            portText = "22",
            username = " root ",
            authType = AuthType.PASSWORD,
            password = ""
        )

        val validation = state.validation()

        assertFalse(validation.canSave)
        assertEquals("Password is required.", validation.authError)
    }

    @Test
    fun `key auth requires a selected imported key`() {
        val state = ServerEditorState(
            name = "Debian",
            hostname = "server.example",
            portText = "22",
            username = "root",
            authType = AuthType.KEY_DATA,
            keyPassphrase = " secret "
        )

        val validation = state.validation()

        assertFalse(validation.canSave)
        assertEquals("Select or import an SSH key.", validation.authError)
        assertEquals("secret", state.normalizedKeyPassphrase)
    }

    @Test
    fun `later auth stays valid without credentials`() {
        val state = ServerEditorState(
            name = "Debian",
            hostname = "server.example",
            portText = "2222",
            username = "root",
            authType = AuthType.NONE
        )

        val validation = state.validation()
        val server = state.toServerConfig(id = "server-1")

        assertTrue(validation.canSave)
        assertNull(validation.authError)
        assertEquals(AuthType.NONE, server.authType)
        assertEquals(2222, server.port)
    }

    @Test
    fun `toServerConfig trims fields and keeps selected key`() {
        val state = ServerEditorState(
            id = "server-2",
            name = "  Debian VM ",
            hostname = " demo.local ",
            portText = " 2200 ",
            username = " alice ",
            authType = AuthType.KEY_DATA,
            selectedKeyId = "key-1",
            selectedKeyLabel = "Main Key",
            distro = "ubuntu",
            autoConnect = true,
            isFavorite = true,
            publicIPEnabled = false,
            pollIntervalSeconds = 10,
            alertLookbackMinutes = 30
        )

        val server = state.toServerConfig()

        assertEquals("server-2", server.id)
        assertEquals("Debian VM", server.name)
        assertEquals("demo.local", server.hostname)
        assertEquals("alice", server.username)
        assertEquals(2200, server.port)
        assertEquals(AuthType.KEY_DATA, server.authType)
        assertEquals("key-1", server.keyDataId)
        assertTrue(server.autoConnect)
        assertTrue(server.isFavorite)
        assertFalse(server.publicIPEnabled)
    }
}
