package com.nodex.client.core.security

import android.content.Context
import com.nodex.client.core.data.local.SshKeyDao
import com.nodex.client.core.data.local.SshKeyEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import net.schmizz.sshj.SSHClient as SSHJClient
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sshKeyDao: SshKeyDao,
    private val credentialsStore: CredentialVault
) {
    val keys: Flow<List<SshKeyEntity>> = sshKeyDao.observeKeys()

    suspend fun importKey(
        name: String,
        keyText: String,
        passphrase: String?
    ): SshKeyEntity {
        val normalizedName = name.trim().ifBlank { "Imported Key" }
        val normalizedText = keyText.trim()
        val normalizedPassphrase = passphrase?.trim()?.takeIf { it.isNotEmpty() }
        val metadata = loadKeyMetadata(normalizedText, normalizedPassphrase)
        val id = UUID.randomUUID().toString()
        credentialsStore.savePrivateKey(id, normalizedText)
        credentialsStore.savePrivateKeyPassphrase(id, normalizedPassphrase)
        val entity = SshKeyEntity(
            id = id,
            name = normalizedName,
            keyType = metadata.keyType,
            fingerprint = metadata.fingerprint
        )
        sshKeyDao.insertKey(entity)
        return entity
    }

    suspend fun getKey(id: String): SshKeyEntity? = sshKeyDao.getKey(id)

    fun getKeyText(id: String): String? = credentialsStore.getPrivateKey(id)

    fun getKeyPassphrase(id: String): String? = credentialsStore.getPrivateKeyPassphrase(id)

    suspend fun deleteKey(id: String) {
        sshKeyDao.deleteKey(id)
        credentialsStore.clearPrivateKey(id)
    }

    suspend fun deleteAllKeys() {
        sshKeyDao.getAllKeys().forEach { credentialsStore.clearPrivateKey(it.id) }
        sshKeyDao.deleteAllKeys()
    }

    private fun loadKeyMetadata(keyText: String, passphrase: String?): KeyMetadata {
        val tempFile = File.createTempFile("ssh_key_import_", ".pem", context.cacheDir)
        return try {
            tempFile.writeText(keyText)
            val client = SSHJClient()
            try {
                val provider = if (passphrase != null) {
                    client.loadKeys(tempFile.absolutePath, passphrase)
                } else {
                    client.loadKeys(tempFile.absolutePath)
                }
                val publicKey = provider.public
                KeyMetadata(
                    keyType = provider.type.toString(),
                    fingerprint = PublicKeyFingerprints.sha256(publicKey.encoded)
                )
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        } catch (error: Exception) {
            throw IllegalArgumentException(
                error.message ?: "Unable to load the private key. Check the key format and passphrase."
            )
        } finally {
            tempFile.delete()
        }
    }

    private data class KeyMetadata(
        val keyType: String,
        val fingerprint: String
    )
}
