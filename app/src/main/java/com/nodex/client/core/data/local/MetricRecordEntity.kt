package com.nodex.client.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "metric_records",
    indices = [Index(value = ["serverId", "timestamp"])]
)
data class MetricRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val serverId: String,
    val timestamp: Long,
    val cpuUsage: Double,
    val memoryUsage: Double,
    val diskUsage: Double,
    val cpuTemperature: Double? = null,
    val networkRxBytes: Long = 0,
    val networkTxBytes: Long = 0
)
