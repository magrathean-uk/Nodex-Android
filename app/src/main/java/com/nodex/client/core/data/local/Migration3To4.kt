package com.nodex.client.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns to servers table
        db.execSQL("ALTER TABLE servers ADD COLUMN pollIntervalSeconds INTEGER NOT NULL DEFAULT 2")
        db.execSQL("ALTER TABLE servers ADD COLUMN alertLookbackMinutes INTEGER NOT NULL DEFAULT 60")
        db.execSQL("ALTER TABLE servers ADD COLUMN publicIPEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE servers ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE servers ADD COLUMN autoConnect INTEGER NOT NULL DEFAULT 0")

        // Create metric_records table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS metric_records (
                id TEXT NOT NULL PRIMARY KEY,
                serverId TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                cpuUsage REAL NOT NULL,
                memoryUsage REAL NOT NULL,
                diskUsage REAL NOT NULL,
                cpuTemperature REAL,
                networkRxBytes INTEGER NOT NULL DEFAULT 0,
                networkTxBytes INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_metric_records_serverId_timestamp ON metric_records (serverId, timestamp)")
    }
}
