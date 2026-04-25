package com.nodex.client.core.network.ssh

import com.nodex.client.core.data.local.HostKeyAuditDao
import com.nodex.client.core.data.local.HostKeyAuditEventEntity
import com.nodex.client.core.data.local.HostKeyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostKeyAuditStore @Inject constructor(
    private val auditDao: HostKeyAuditDao
) {
    fun observeRecentEvents(limit: Int = 20): Flow<List<HostKeyAuditEventEntity>> {
        return auditDao.observeRecentEvents(limit)
    }

    suspend fun recordTrusted(info: HostKeyInfo) {
        append(
            hostnamePort = info.hostnamePort,
            keyType = info.keyType,
            fingerprint = info.fingerprint,
            action = "trusted",
            detail = "Host key saved to the local trust store."
        )
    }

    suspend fun recordRejected(info: HostKeyInfo) {
        append(
            hostnamePort = info.hostnamePort,
            keyType = info.keyType,
            fingerprint = info.fingerprint,
            action = "rejected",
            detail = "Trust request was rejected by the user."
        )
    }

    suspend fun recordMismatch(hostnamePort: String, keyType: String, newFingerprint: String, oldFingerprint: String) {
        append(
            hostnamePort = hostnamePort,
            keyType = keyType,
            fingerprint = newFingerprint,
            action = "mismatch",
            detail = "Previous fingerprint: $oldFingerprint"
        )
    }

    suspend fun recordForgotten(hostKey: HostKeyEntity) {
        append(
            hostnamePort = hostKey.hostnamePort,
            keyType = hostKey.keyType,
            fingerprint = hostKey.fingerprint,
            action = "forgotten",
            detail = "Saved host key was removed from the trust store."
        )
    }

    suspend fun clearAll() {
        auditDao.deleteAllEvents()
    }

    private suspend fun append(
        hostnamePort: String,
        keyType: String,
        fingerprint: String,
        action: String,
        detail: String
    ) {
        auditDao.insertEvent(
            HostKeyAuditEventEntity(
                hostnamePort = hostnamePort,
                keyType = keyType,
                fingerprint = fingerprint,
                action = action,
                detail = detail
            )
        )
        auditDao.trimTo(100)
    }
}
