package com.nodex.client.domain.repository

import com.nodex.client.core.data.local.MetricRecordDao
import com.nodex.client.core.data.local.ServerDao
import com.nodex.client.core.security.SecureCredentialsStore
import com.nodex.client.domain.model.AuthType
import com.nodex.client.domain.model.ServerConfig
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ServerRepositoryTest {

    @Test
    fun `deleteServer clears metric history before deleting server`() = runTest {
        val serverDao = mockk<ServerDao>(relaxed = true)
        val metricRecordDao = mockk<MetricRecordDao>(relaxed = true)
        val credentialsStore = mockk<SecureCredentialsStore>(relaxed = true)
        val repository = ServerRepository(serverDao, metricRecordDao, credentialsStore)
        val server = ServerConfig(
            id = "server-1",
            name = "Server",
            hostname = "example.com",
            username = "root",
            authType = AuthType.NONE
        )

        repository.deleteServer(server)

        coVerify(ordering = io.mockk.Ordering.SEQUENCE) {
            metricRecordDao.deleteForServer(server.id)
            serverDao.deleteServer(server)
            credentialsStore.clearPassword(server.id)
            credentialsStore.clearSudoPassword(server.id)
        }
    }
}
