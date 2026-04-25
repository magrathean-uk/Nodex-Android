package com.nodex.client.testing

import android.content.Context
import androidx.room.Room
import com.nodex.client.core.data.local.NodexDatabase
import com.nodex.client.core.security.SecureCredentialsStore
import com.nodex.client.core.security.SshKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties

object NodexUiTestBootstrap {
    private const val runtimeConfigPath = "/data/local/tmp/nodex-ui-test-config.properties"
    private const val databaseName = "nodex_db"
    private const val secureCredentialsName = "secure_credentials"
    private const val dataStoreDirName = "datastore"
    private val falseValues = setOf("0", "false", "no", "off")

    data class KeyFixture(
        val name: String,
        val text: String
    )

    fun isEnabled(read: (String) -> String?): Boolean {
        return flagEnabled(read("NODEX_UI_TESTING"))
            || flagEnabled(read("NODEX_UI_TEST_RESET"))
            || flagEnabled(read("NODEX_UI_TEST_DIRECT_KEY_IMPORT"))
    }

    fun shouldResetPersistentState(read: (String) -> String?): Boolean {
        return flagEnabled(runtimeValue(read, "NODEX_UI_TEST_RESET"))
    }

    fun shouldUseDirectKeyImport(read: (String) -> String?): Boolean {
        return flagEnabled(runtimeValue(read, "NODEX_UI_TEST_DIRECT_KEY_IMPORT"))
    }

    fun loadConfig(read: (String) -> String?): NodexUiTestConfig? {
        return NodexUiTestConfig.load { key -> runtimeValue(read, key) }
    }

    fun loadKeyFixture(read: (String) -> String?): KeyFixture? {
        val config = loadConfig(read) ?: return null
        val keyText = config.keyText ?: config.keyPath?.let(::readKeyText) ?: return null
        return KeyFixture(
            name = config.keyName,
            text = keyText
        )
    }

    fun resetPersistentState(context: Context) {
        context.deleteDatabase(databaseName)
        context.deleteSharedPreferences(secureCredentialsName)
        context.filesDir.resolve(dataStoreDirName).deleteRecursively()
    }

    fun importKeyFixture(context: Context, fixture: KeyFixture) {
        runBlocking(Dispatchers.IO) {
            val db = Room.databaseBuilder(context, NodexDatabase::class.java, databaseName).build()
            try {
                val store = SshKeyStore(
                    context = context,
                    sshKeyDao = db.sshKeyDao(),
                    credentialsStore = SecureCredentialsStore(context)
                )
                store.importKey(
                    name = fixture.name,
                    keyText = fixture.text,
                    passphrase = null
                )
            } finally {
                db.close()
            }
        }
    }

    private fun runtimeValue(read: (String) -> String?, key: String): String? {
        return normalized(read(key)) ?: runtimeConfigValue(key)
    }

    private fun runtimeConfigValue(key: String): String? {
        val file = File(runtimeConfigPath)
        if (!file.isFile) {
            return null
        }

        val properties = Properties()
        file.inputStream().use { stream ->
            properties.load(stream)
        }
        return normalized(properties.getProperty(key))
    }

    private fun readKeyText(path: String): String? {
        val file = File(path)
        if (!file.isFile) {
            return null
        }
        return normalized(file.readText())
    }

    private fun flagEnabled(value: String?): Boolean {
        val normalized = normalized(value)?.lowercase() ?: return false
        return normalized !in falseValues
    }

    private fun normalized(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}
