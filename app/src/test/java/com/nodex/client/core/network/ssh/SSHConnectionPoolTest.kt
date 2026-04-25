package com.nodex.client.core.network.ssh

import android.content.Context
import com.nodex.client.core.data.local.HostKeyDao
import com.nodex.client.core.security.SecureCredentialsStore
import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.ServerConfig
import io.mockk.every
import io.mockk.mockk
import net.schmizz.sshj.SSHClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files

class SSHConnectionPoolTest {

    @Test
    fun `authenticate reports a clear user error when imported key data is missing`() {
        val credentialsStore = mockk<SecureCredentialsStore>(relaxed = true)
        every { credentialsStore.getPrivateKey("missing-key") } returns null

        val pool = SSHConnectionPool(
            context = mockk<Context>(relaxed = true),
            hostKeyDao = mockk<HostKeyDao>(relaxed = true),
            credentialsStore = credentialsStore,
            hostKeyAuditStore = mockk<HostKeyAuditStore>(relaxed = true),
            hostKeyPromptManager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        )
        val method = SSHConnectionPool::class.java.getDeclaredMethod(
            "authenticate",
            SSHClient::class.java,
            ServerConfig::class.java,
            String::class.java
        ).apply {
            isAccessible = true
        }
        val config = ServerConfig(
            id = "server-key-data",
            name = "Server",
            hostname = "example.com",
            username = "root",
            authType = AuthType.KEY_DATA,
            keyDataId = "missing-key"
        )

        val error = try {
            method.invoke(pool, SSHClient(), config, null)
            null
        } catch (error: InvocationTargetException) {
            error.cause
        }

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Imported key") == true)
    }

    @Test
    fun `first failure creates backoff placeholder even before any connection exists`() {
        val pool = connectionPool()
        val failureMethod = SSHConnectionPool::class.java.getDeclaredMethod(
            "handleConnectionFailure",
            String::class.java,
            Throwable::class.java
        ).apply {
            isAccessible = true
        }

        failureMethod.invoke(pool, "server-backoff", IOException("boom"))

        val pooledConnection = poolMap(pool)["server-backoff"]
        requireNotNull(pooledConnection)
        assertTrue(failureCount(pooledConnection) == 1)
        assertTrue(backoffUntil(pooledConnection) > System.currentTimeMillis())
    }

    @Test
    fun `startup deletes leftover private key temp files`() {
        val cacheDir = Files.createTempDirectory("nodex-cache").toFile()
        val staleKey = java.io.File(cacheDir, "ssh_key_stale.pem").apply {
            writeText("secret")
        }
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns cacheDir

        SSHConnectionPool(
            context = context,
            hostKeyDao = mockk(relaxed = true),
            credentialsStore = mockk<SecureCredentialsStore>(relaxed = true),
            hostKeyAuditStore = mockk(relaxed = true),
            hostKeyPromptManager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        )

        assertFalse(staleKey.exists())
        cacheDir.deleteRecursively()
    }

    private fun connectionPool(): SSHConnectionPool {
        val credentialsStore = mockk<SecureCredentialsStore>(relaxed = true)
        val hostKeyDao = mockk<HostKeyDao>(relaxed = true)
        return SSHConnectionPool(
            context = mockk<Context>(relaxed = true),
            hostKeyDao = hostKeyDao,
            credentialsStore = credentialsStore,
            hostKeyAuditStore = mockk<HostKeyAuditStore>(relaxed = true),
            hostKeyPromptManager = HostKeyPromptManager(mockk<HostKeyAuditStore>(relaxed = true))
        )
    }

    private fun poolMap(pool: SSHConnectionPool): Map<*, *> {
        val field = SSHConnectionPool::class.java.getDeclaredField("pool").apply {
            isAccessible = true
        }
        return field.get(pool) as Map<*, *>
    }

    private fun failureCount(pooledConnection: Any): Int {
        val field = pooledConnection.javaClass.getDeclaredField("consecutiveFailures").apply {
            isAccessible = true
        }
        return field.getInt(pooledConnection)
    }

    private fun backoffUntil(pooledConnection: Any): Long {
        val field = pooledConnection.javaClass.getDeclaredField("backoffUntil").apply {
            isAccessible = true
        }
        return field.getLong(pooledConnection)
    }
}
