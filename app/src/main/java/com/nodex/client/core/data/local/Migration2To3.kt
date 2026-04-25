package com.nodex.client.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nodex.client.core.security.CredentialVault

class Migration2To3(
    private val credentialsStore: CredentialVault
) : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(servers)").use { pragma ->
            val nameIndex = pragma.getColumnIndex("name")
            while (pragma.moveToNext()) {
                if (nameIndex >= 0) {
                    columns.add(pragma.getString(nameIndex))
                }
            }
        }

        if (columns.contains("password")) {
            db.query("SELECT id, password FROM servers").use { cursor ->
                val idIndex = cursor.getColumnIndex("id")
                val passwordIndex = cursor.getColumnIndex("password")
                while (cursor.moveToNext()) {
                    if (idIndex >= 0 && passwordIndex >= 0) {
                        val id = cursor.getString(idIndex)
                        val password = cursor.getString(passwordIndex)
                        if (!password.isNullOrBlank()) {
                            credentialsStore.savePassword(id, password)
                        }
                    }
                }
            }
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS servers_new (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                hostname TEXT NOT NULL,
                port INTEGER NOT NULL,
                username TEXT NOT NULL,
                authType TEXT NOT NULL,
                keyFilePath TEXT,
                distro TEXT NOT NULL,
                version TEXT NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )

        val selectName = if (columns.contains("name")) "name" else "''"
        val selectHostname = if (columns.contains("hostname")) "hostname" else "''"
        val selectPort = if (columns.contains("port")) "port" else "22"
        val selectUsername = if (columns.contains("username")) "username" else "''"
        val selectAuthType = if (columns.contains("authType")) "authType" else "'NONE'"
        val selectKeyFilePath = if (columns.contains("keyFilePath")) "keyFilePath" else "NULL"
        val selectDistro = if (columns.contains("distro")) "distro" else "'Unknown'"
        val selectVersion = if (columns.contains("version")) "version" else "''"

        db.execSQL(
            """
            INSERT INTO servers_new (id, name, hostname, port, username, authType, keyFilePath, distro, version)
            SELECT id, $selectName, $selectHostname, $selectPort, $selectUsername, $selectAuthType, $selectKeyFilePath, $selectDistro, $selectVersion
            FROM servers
            """.trimIndent()
        )

        db.execSQL("DROP TABLE servers")
        db.execSQL("ALTER TABLE servers_new RENAME TO servers")
    }
}
