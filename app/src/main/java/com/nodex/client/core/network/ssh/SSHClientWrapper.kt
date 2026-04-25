package com.nodex.client.core.network.ssh

import com.nodex.client.core.security.CredentialVault
import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient as SSHJClient
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.Resource
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import android.content.Context
import android.net.Uri
import android.util.Base64
import net.schmizz.sshj.common.KeyType
import timber.log.Timber
import java.security.PublicKey
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SSHClientWrapper @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val hostKeyDao: com.nodex.client.core.data.local.HostKeyDao,
    private val credentialsStore: CredentialVault,
    private val hostKeyAuditStore: HostKeyAuditStore,
    private val hostKeyPromptManager: HostKeyPromptManager
) {

    init {
        // Ensure BouncyCastle is registered correctly for Android
        try {
            val provider = Security.getProvider("BC")
            if (provider == null || provider.javaClass.name != "org.bouncycastle.jce.provider.BouncyCastleProvider") {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }
        } catch (e: Exception) {
            Timber.w(e, "Unable to update BouncyCastle provider; continuing with installed provider")
        }
        cleanupKeyCache()
    }

    private data class CapturedKey(
        val keyType: String,
        val fingerprint: String,
        val publicKey: String
    )

    private fun createHostKeyVerifier(
        knownKey: com.nodex.client.core.data.local.HostKeyEntity?,
        onCapture: (CapturedKey) -> Unit,
        onMismatch: () -> Unit
    ): HostKeyVerifier {
        return object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fingerprint = SecurityUtils.getFingerprint(key)
                return if (knownKey != null) {
                    val matches = fingerprint == knownKey.fingerprint
                    if (!matches) {
                        onMismatch()
                        kotlinx.coroutines.runBlocking {
                            hostKeyAuditStore.recordMismatch(
                                hostnamePort = "$hostname:$port",
                                keyType = knownKey.keyType,
                                newFingerprint = fingerprint,
                                oldFingerprint = knownKey.fingerprint
                            )
                        }
                        hostKeyPromptManager.showMismatch(
                            hostname = hostname,
                            port = port,
                            oldFingerprint = knownKey.fingerprint,
                            newFingerprint = fingerprint
                        )
                    }
                    matches
                } else {
                    val resolvedKeyType = KeyType.fromKey(key)
                    val keyType = if (resolvedKeyType != KeyType.UNKNOWN) {
                        resolvedKeyType.toString()
                    } else {
                        key.algorithm
                    }
                    val publicKey = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
                    onCapture(CapturedKey(keyType, fingerprint, publicKey))
                    true
                }
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
                val stored = knownKey?.keyType?.takeIf { it.isNotBlank() }
                return stored?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    suspend fun <T> execute(
        config: ServerConfig,
        passwordOverride: String? = null,
        block: (SSHJClient) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val client = SSHJClient()
        
        val hostnamePort = "${config.hostname}:${config.port}"
        val knownKey = hostKeyDao.getHostKey(hostnamePort)
        var capturedKey: CapturedKey? = null
        var hostKeyMismatch = false
        
        client.addHostKeyVerifier(
            createHostKeyVerifier(
                knownKey,
                onCapture = { captured -> capturedKey = captured },
                onMismatch = { hostKeyMismatch = true }
            )
        )
        
        try {
            client.connectTimeout = 15000
            client.timeout = 15000
            client.connect(config.hostname, config.port)
            val now = System.currentTimeMillis()
            if (knownKey == null) {
                val captured = capturedKey
                    ?: throw IllegalStateException("Host key capture failed for $hostnamePort")
                return@withContext Result.failure(
                    HostKeyVerificationRequiredException(
                        HostKeyInfo(
                            hostname = config.hostname,
                            port = config.port,
                            keyType = captured.keyType,
                            fingerprint = captured.fingerprint,
                            publicKey = captured.publicKey
                        )
                    )
                )
            } else {
                hostKeyDao.insertHostKey(
                    knownKey.copy(lastSeen = now)
                )
            }
            
            when (config.authType) {
                AuthType.PASSWORD -> {
                    val password = passwordOverride
                        ?: credentialsStore.getPassword(config.id)
                        ?: throw IllegalArgumentException("Password required")
                    try {
                        client.authPassword(config.username, password)
                    } catch (e: Exception) {
                        client.auth(config.username, AuthKeyboardInteractive(object : ChallengeResponseProvider {
                            override fun getSubmethods(): List<String> = emptyList()
                            override fun init(resource: Resource<*>?, name: String?, instruction: String?) {}
                            override fun getResponse(prompt: String?, echo: Boolean): CharArray = password.toCharArray()
                            override fun shouldRetry(): Boolean = false
                        }))
                    }
                }
                AuthType.KEY_FILE -> {
                    withResolvedKeyPath(config.keyFilePath) { resolvedPath ->
                        client.authPublickey(config.username, resolvedPath)
                    }
                }
                AuthType.KEY_DATA -> {
                    withResolvedKeyData(config.keyDataId) { resolvedPath, passphrase ->
                        val provider = if (passphrase != null) {
                            client.loadKeys(resolvedPath, passphrase)
                        } else {
                            client.loadKeys(resolvedPath)
                        }
                        client.authPublickey(config.username, provider)
                    }
                }
                AuthType.NONE -> client.authPublickey(config.username)
            }

            if (!client.isAuthenticated) {
                throw SecurityException("Authentication failed")
            }

            Result.success(block(client))
        } catch (e: Exception) {
            if (hostKeyMismatch) {
                Result.failure(SecurityException("Host key mismatch for ${config.hostname}:${config.port}"))
            } else {
                Result.failure(e)
            }
        } finally {
            try {
                client.disconnect()
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
            cleanupKeyCache()
        }
    }

    fun runCommand(client: SSHJClient, command: String, timeoutSeconds: Int = 10): String {
        return runCommand(client, SshCommandRequest.plain(command, maxOutputChars = 200_000), timeoutSeconds)
    }

    fun runCommand(client: SSHJClient, request: SshCommandRequest, timeoutSeconds: Int = 10): String {
        val session = client.startSession()
        return try {
            val cmd = session.exec(request.command)
            writeStandardInput(cmd, request.stdin)
            val output = readLimited(cmd.inputStream, request.maxOutputChars)
            cmd.join(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            if (cmd.exitStatus != 0 && output.isBlank()) {
                val error = readLimited(cmd.errorStream, request.maxOutputChars)
                if (error.isNotBlank()) return "Error: $error"
            }
            output
        } finally {
            try { session.close() } catch (_: Exception) {}
        }
    }

    suspend fun trustHostKey(info: HostKeyInfo) = withContext(Dispatchers.IO) {
        hostKeyDao.insertHostKey(
            com.nodex.client.core.data.local.HostKeyEntity(
                hostnamePort = info.hostnamePort,
                keyType = info.keyType,
                fingerprint = info.fingerprint,
                publicKey = info.publicKey,
                lastSeen = System.currentTimeMillis()
            )
        )
        hostKeyAuditStore.recordTrusted(info)
    }

    private inline fun withResolvedKeyPath(keyFilePath: String?, usePath: (String) -> Unit) {
        if (keyFilePath.isNullOrBlank()) {
            throw IllegalArgumentException("Key file path required for KEY_FILE auth")
        }

        if (!keyFilePath.startsWith("content://")) {
            usePath(keyFilePath)
            return
        }

        val tempFile = copyContentUriToTempFile(Uri.parse(keyFilePath))
            ?: throw IllegalArgumentException("Unable to read key file")
        try {
            usePath(tempFile.absolutePath)
        } finally {
            tempFile.delete()
        }
    }

    private fun copyContentUriToTempFile(uri: Uri): java.io.File? {
        return try {
            val outputFile = java.io.File.createTempFile("ssh_key_", ".pem", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            outputFile
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanupKeyCache() {
        runCatching { context.cacheDir }.getOrNull()?.listFiles()
            ?.filter { it.name.startsWith("ssh_key_") && it.name.endsWith(".pem") }
            ?.forEach { try { it.delete() } catch (_: Exception) {} }
    }

    private inline fun withResolvedKeyData(
        keyDataId: String?,
        usePath: (String, String?) -> Unit
    ) {
        if (keyDataId.isNullOrBlank()) {
            throw IllegalArgumentException("Imported key is missing")
        }

        val keyText = credentialsStore.getPrivateKey(keyDataId)
            ?: throw IllegalArgumentException("Imported key is unavailable")
        val tempFile = java.io.File.createTempFile("ssh_key_", ".pem", context.cacheDir)
        try {
            tempFile.writeText(keyText)
            usePath(tempFile.absolutePath, credentialsStore.getPrivateKeyPassphrase(keyDataId))
        } finally {
            tempFile.delete()
        }
    }

    private fun writeStandardInput(command: Session.Command, stdin: String?) {
        try {
            command.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                if (stdin != null) {
                    writer.write(stdin)
                    writer.flush()
                }
            }
        } catch (_: Exception) {
            try { command.outputStream.close() } catch (_: Exception) {}
        }
    }

    private fun readLimited(input: java.io.InputStream, maxChars: Int): String {
        val reader = input.reader()
        val buffer = CharArray(8192)
        val builder = StringBuilder()
        while (builder.length < maxChars) {
            val read = reader.read(buffer)
            if (read <= 0) break
            val remaining = maxChars - builder.length
            val toAppend = if (read > remaining) remaining else read
            builder.append(buffer, 0, toAppend)
            if (toAppend < read) break
        }
        return builder.toString()
    }
}
