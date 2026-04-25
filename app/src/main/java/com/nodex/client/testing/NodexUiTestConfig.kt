package com.nodex.client.testing

import java.io.File

data class NodexUiTestConfig(
    val host: String,
    val username: String,
    val name: String,
    val port: String,
    val keyName: String,
    val keyText: String?,
    val keyPath: String?,
    val password: String?,
    val sudoPassword: String?
) {
    companion object {
        fun load(read: (String) -> String?): NodexUiTestConfig? {
            val host = normalized(read("NODEX_UI_TEST_LIVE_HOST")) ?: return null
            val username = normalized(read("NODEX_UI_TEST_LIVE_USERNAME")) ?: return null
            val keyText = normalized(read("NODEX_UI_TEST_KEY_TEXT"))
            val keyPath = normalized(read("NODEX_UI_TEST_KEY_PATH"))
            val keyName = normalized(read("NODEX_UI_TEST_KEY_NAME"))
                ?: keyPath?.let { File(it).name }
                ?: "ui-test-key"

            return NodexUiTestConfig(
                host = host,
                username = username,
                name = normalized(read("NODEX_UI_TEST_LIVE_NAME")) ?: host,
                port = normalized(read("NODEX_UI_TEST_LIVE_PORT")) ?: "22",
                keyName = keyName,
                keyText = keyText,
                keyPath = keyPath,
                password = normalized(read("NODEX_UI_TEST_PASSWORD")),
                sudoPassword = normalized(read("NODEX_UI_TEST_SUDO_PASSWORD"))
            )
        }

        private fun normalized(value: String?): String? {
            val trimmed = value?.trim()
            return if (trimmed.isNullOrEmpty()) null else trimmed
        }
    }
}
