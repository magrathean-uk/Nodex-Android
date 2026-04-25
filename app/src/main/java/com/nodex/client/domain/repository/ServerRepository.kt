package com.nodex.client.domain.repository

import com.nodex.client.core.data.local.ServerDao
import com.nodex.client.core.data.local.MetricRecordDao
import com.nodex.client.core.security.CredentialVault
import com.nodex.client.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val metricRecordDao: MetricRecordDao,
    private val credentialsStore: CredentialVault
) {
    fun getAllServers(): Flow<List<ServerConfig>> = serverDao.getAllServers()

    suspend fun getAllServersSync(): List<ServerConfig> = serverDao.getAllServers().first()

    suspend fun getServerById(id: String): ServerConfig? = serverDao.getServerById(id)

    suspend fun addServer(server: ServerConfig) = serverDao.insertServer(server)

    suspend fun updateServer(server: ServerConfig) = serverDao.updateServer(server)

    suspend fun deleteServer(server: ServerConfig) {
        metricRecordDao.deleteForServer(server.id)
        serverDao.deleteServer(server)
        credentialsStore.clearPassword(server.id)
        credentialsStore.clearSudoPassword(server.id)
    }
}
