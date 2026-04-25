package com.nodex.client.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "host_keys")
data class HostKeyEntity(
    @PrimaryKey val hostnamePort: String, // format: "hostname:port"
    val keyType: String,
    val fingerprint: String,
    val publicKey: String,
    val lastSeen: Long
)
