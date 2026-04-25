package com.nodex.client.core.network.ssh

import com.nodex.client.core.data.local.HostKeyDao
import com.nodex.client.core.security.CredentialVault
import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient as SSHJClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.Resource
import android.content.Context
import android.net.Uri
import android.util.Base64
import timber.log.Timber
import java.io.IOException
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * Persistent SSH connection per server. Keeps a single SSHJClient alive
 * and multiplexes exec sessions over it — matching the iOS architecture.
 */
@Singleton
class SSHConnectionPool @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val hostKeyDao: HostKeyDao,
    private val credentialsStore: CredentialVault,
    private val hostKeyAuditStore: HostKeyAuditStore,
    private val hostKeyPromptManager: HostKeyPromptManager
) {

    init {
        try {
            val provider = java.security.Security.getProvider("BC")
            if (provider == null || provider.javaClass.name != "org.bouncycastle.jce.provider.BouncyCastleProvider") {
                java.security.Security.removeProvider("BC")
                java.security.Security.insertProviderAt(
                    org.bouncycastle.jce.provider.BouncyCastleProvider(), 1
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Unable to update BouncyCastle provider for pooled SSH client")
        }
        cleanupKeyCache()
    }

    private class PooledConnection(
        val client: SSHJClient,
        val serverId: String,
        var lastUsed: Long = System.currentTimeMillis(),
        var consecutiveFailures: Int = 0,
        var backoffUntil: Long = 0
    ) {
        val isHealthy: Boolean
            get() = try {
                client.isConnected && client.isAuthenticated
            } catch (_: Exception) {
                false
            }
    }

    private val pool = ConcurrentHashMap<String, PooledConnection>()
    private val connectMutex = ConcurrentHashMap<String, Mutex>()
    private val connectSemaphore = Semaphore(3) // max 3 concurrent connection setups

    /**
     * Execute a block with a persistent SSH connection.
     * Reuses existing connection if healthy, otherwise creates a new one.
     */
    suspend fun <T> execute(
        config: ServerConfig,
        passwordOverride: String? = null,
        block: suspend (SSHJClient) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val serverId = config.id

        // Check backoff
        val existing = pool[serverId]
        if (existing != null && System.currentTimeMillis() < existing.backoffUntil) {
            return@withContext Result.failure(
                IOException("Connection backing off (${existing.consecutiveFailures} failures)")
            )
        }

        try {
            val conn = acquireConnection(config, passwordOverride)
            conn.lastUsed = System.currentTimeMillis()
            val result = block(conn.client)
            conn.consecutiveFailures = 0
            conn.backoffUntil = 0
            Result.success(result)
        } catch (e: Exception) {
            handleConnectionFailure(serverId, e)
            Result.failure(e)
        }
    }

    /**
     * Run a single command on the pooled connection.
     */
    fun runCommand(client: SSHJClient, command: String, timeoutSeconds: Int = 10): String {
        return runCommand(client, SshCommandRequest.plain(command), timeoutSeconds)
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

    /**
     * Disconnect a specific server.
     */
    fun disconnect(serverId: String) {
        pool.remove(serverId)?.let { conn ->
            try {
                Timber.d("Disconnecting pooled connection for $serverId")
                conn.client.disconnect()
            } catch (_: Exception) {}
        }
        connectMutex.remove(serverId)
    }

    /**
     * Disconnect all servers (e.g. on app destroy).
     */
    fun disconnectAll() {
        Timber.d("Disconnecting all pooled connections (${pool.size})")
        pool.keys().toList().forEach { disconnect(it) }
        cleanupKeyCache()
    }

    /**
     * Check if a server has an active healthy connection.
     */
    fun isConnected(serverId: String): Boolean {
        return pool[serverId]?.isHealthy == true
    }

    // ── Internal ───────────────────────────────────────────────────────

    private suspend fun acquireConnection(
        config: ServerConfig,
        passwordOverride: String?
    ): PooledConnection {
        val serverId = config.id

        // Fast path: reuse healthy connection
        pool[serverId]?.let { conn ->
            if (conn.isHealthy) return conn
            // Stale — remove and reconnect
            Timber.d("Stale connection for $serverId, reconnecting")
            pool.remove(serverId)
            try { conn.client.disconnect() } catch (_: Exception) {}
        }

        // Serialize connection attempts per server
        val mutex = connectMutex.getOrPut(serverId) { Mutex() }
        return mutex.withLock {
            // Double-check after acquiring lock
            pool[serverId]?.let { if (it.isHealthy) return@withLock it }

            // Limit concurrent connection establishments
            connectSemaphore.withPermit {
                val conn = createConnection(config, passwordOverride)
                pool[serverId] = conn
                Timber.d("New pooled connection for $serverId")
                conn
            }
        }
    }

    private suspend fun createConnection(
        config: ServerConfig,
        passwordOverride: String?
    ): PooledConnection {
        val client = SSHJClient()
        val hostnamePort = "${config.hostname}:${config.port}"
        val knownKey = hostKeyDao.getHostKey(hostnamePort)
        var capturedKey: CapturedKey? = null
        var hostKeyMismatch = false

        client.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val fingerprint = SecurityUtils.getFingerprint(key)
                return if (knownKey != null) {
                    val matches = fingerprint == knownKey.fingerprint
                    if (!matches) {
                        hostKeyMismatch = true
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
                    val keyType = if (resolvedKeyType != KeyType.UNKNOWN) resolvedKeyType.toString() else key.algorithm
                    val publicKey = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
                    capturedKey = CapturedKey(keyType, fingerprint, publicKey)
                    true
                }
            }
            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
                return knownKey?.keyType?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }
        })

        client.connectTimeout = 15000
        client.timeout = 15000
        client.connect(config.hostname, config.port)

        // Enable keepalive — send every 30s to keep connection alive
        try {
            client.connection.keepAlive.keepAliveInterval = 30
        } catch (_: Exception) {
            // Keepalive setup may fail on some server configs
        }

        if (knownKey == null) {
            val captured = capturedKey
                ?: throw IllegalStateException("Host key capture failed for $hostnamePort")
            client.disconnect()
            throw HostKeyVerificationRequiredException(
                HostKeyInfo(
                    hostname = config.hostname,
                    port = config.port,
                    keyType = captured.keyType,
                    fingerprint = captured.fingerprint,
                    publicKey = captured.publicKey
                )
            )
        } else {
            hostKeyDao.insertHostKey(knownKey.copy(lastSeen = System.currentTimeMillis()))
        }

        if (hostKeyMismatch) {
            client.disconnect()
            throw SecurityException("Host key mismatch for ${config.hostname}:${config.port}")
        }

        authenticate(client, config, passwordOverride)

        if (!client.isAuthenticated) {
            client.disconnect()
            throw SecurityException("Authentication failed")
        }

        return PooledConnection(client, config.id)
    }

    private fun authenticate(client: SSHJClient, config: ServerConfig, passwordOverride: String?) {
        when (config.authType) {
            AuthType.PASSWORD -> {
                val password = passwordOverride
                    ?: credentialsStore.getPassword(config.id)
                    ?: throw IllegalArgumentException("Password required")
                try {
                    client.authPassword(config.username, password)
                } catch (_: Exception) {
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
    }

    private fun handleConnectionFailure(serverId: String, error: Throwable) {
        val conn = pool.getOrPut(serverId) {
            PooledConnection(SSHJClient(), serverId)
        }
        conn.consecutiveFailures++
        val backoffSeconds = min(60, (1 shl min(conn.consecutiveFailures, 6)))
        val jitter = (backoffSeconds * 0.2 * Random.nextDouble()).toLong()
        conn.backoffUntil = System.currentTimeMillis() + (backoffSeconds * 1000L) + jitter
        Timber.w("Connection failure #${conn.consecutiveFailures} for $serverId, backoff ${backoffSeconds}s")

        if (conn.consecutiveFailures >= 10) {
            Timber.e("Server $serverId unreachable after 10 failures, removing from pool")
            pool.remove(serverId)
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
                outputFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            outputFile
        } catch (_: Exception) {
            null
        }
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

    private fun cleanupKeyCache() {
        runCatching { context.cacheDir }.getOrNull()?.listFiles()
            ?.filter { it.name.startsWith("ssh_key_") && it.name.endsWith(".pem") }
            ?.forEach { try { it.delete() } catch (_: Exception) {} }
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

    private data class CapturedKey(
        val keyType: String,
        val fingerprint: String,
        val publicKey: String
    )
}
