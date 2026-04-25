package com.nodex.client.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodexUiTestConfigTest {

    @Test
    fun `load returns null without required live host fields`() {
        val config = NodexUiTestConfig.load { key ->
            when (key) {
                "NODEX_UI_TEST_LIVE_HOST" -> " "
                "NODEX_UI_TEST_LIVE_USERNAME" -> "root"
                else -> null
            }
        }

        assertNull(config)
    }

    @Test
    fun `load parses inline key fixture and password fields`() {
        val config = NodexUiTestConfig.load { key ->
            when (key) {
                "NODEX_UI_TEST_LIVE_HOST" -> "127.0.0.1"
                "NODEX_UI_TEST_LIVE_USERNAME" -> "user3"
                "NODEX_UI_TEST_LIVE_NAME" -> " Debian "
                "NODEX_UI_TEST_LIVE_PORT" -> "42231"
                "NODEX_UI_TEST_KEY_NAME" -> " user3-key "
                "NODEX_UI_TEST_KEY_TEXT" -> " PRIVATE KEY "
                "NODEX_UI_TEST_PASSWORD" -> " secret "
                "NODEX_UI_TEST_SUDO_PASSWORD" -> " sudo "
                else -> null
            }
        }

        requireNotNull(config)
        assertEquals("127.0.0.1", config.host)
        assertEquals("user3", config.username)
        assertEquals("Debian", config.name)
        assertEquals("42231", config.port)
        assertEquals("user3-key", config.keyName)
        assertEquals("PRIVATE KEY", config.keyText)
        assertEquals("secret", config.password)
        assertEquals("sudo", config.sudoPassword)
    }

    @Test
    fun `load keeps password-only live config`() {
        val config = NodexUiTestConfig.load { key ->
            when (key) {
                "NODEX_UI_TEST_LIVE_HOST" -> "127.0.0.1"
                "NODEX_UI_TEST_LIVE_USERNAME" -> "user1"
                "NODEX_UI_TEST_PASSWORD" -> "pw"
                else -> null
            }
        }

        requireNotNull(config)
        assertEquals("127.0.0.1", config.name)
        assertEquals("22", config.port)
        assertNull(config.keyText)
        assertEquals("pw", config.password)
    }
}
