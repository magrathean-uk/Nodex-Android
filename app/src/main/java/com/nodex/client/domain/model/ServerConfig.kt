package com.nodex.client.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "servers")
data class ServerConfig(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val keyFilePath: String? = null,
    val keyDataId: String? = null,
    val distro: String = "Unknown",
    val version: String = "",
    val pollIntervalSeconds: Int = 2,
    val alertLookbackMinutes: Int = 60,
    val publicIPEnabled: Boolean = true,
    val isFavorite: Boolean = false,
    val autoConnect: Boolean = false
) {
    val distroFamily: DistroFamily
        get() = DistroFamily.fromOsId(distro)
}
