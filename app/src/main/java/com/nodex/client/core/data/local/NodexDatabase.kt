package com.nodex.client.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nodex.client.domain.model.ServerConfig
import com.nodex.client.core.data.converters.RoomConverters

@Database(
    entities = [
        ServerConfig::class,
        HostKeyEntity::class,
        MetricRecordEntity::class,
        SshKeyEntity::class,
        HostKeyAuditEventEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class NodexDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun hostKeyDao(): HostKeyDao
    abstract fun metricRecordDao(): MetricRecordDao
    abstract fun sshKeyDao(): SshKeyDao
    abstract fun hostKeyAuditDao(): HostKeyAuditDao
}
