package com.nodex.client.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val keyType: String,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis()
)
