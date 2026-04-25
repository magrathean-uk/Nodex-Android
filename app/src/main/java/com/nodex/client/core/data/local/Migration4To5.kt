package com.nodex.client.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE servers ADD COLUMN keyDataId TEXT")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ssh_keys (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                keyType TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS host_key_audit_events (
                id TEXT NOT NULL PRIMARY KEY,
                hostnamePort TEXT NOT NULL,
                keyType TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                action TEXT NOT NULL,
                detail TEXT,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_host_key_audit_events_timestamp ON host_key_audit_events (timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_host_key_audit_events_hostnamePort ON host_key_audit_events (hostnamePort)")
    }
}
