package com.nodex.client.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "host_key_audit_events",
    indices = [Index(value = ["timestamp"]), Index(value = ["hostnamePort"])]
)
data class HostKeyAuditEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val hostnamePort: String,
    val keyType: String,
    val fingerprint: String,
    val action: String,
    val detail: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
