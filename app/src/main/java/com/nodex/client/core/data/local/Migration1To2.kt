package com.nodex.client.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS host_keys (
                hostnamePort TEXT NOT NULL,
                keyType TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                publicKey TEXT NOT NULL,
                lastSeen INTEGER NOT NULL,
                PRIMARY KEY(hostnamePort)
            )
            """.trimIndent()
        )
    }
}
