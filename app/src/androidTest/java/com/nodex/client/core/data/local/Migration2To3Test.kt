package com.nodex.client.core.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.nodex.client.core.security.SecureCredentialsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {

    private lateinit var context: Context
    private lateinit var credentialsStore: SecureCredentialsStore
    private lateinit var dbName: String

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        credentialsStore = SecureCredentialsStore(context)
        dbName = "migration-2-3-${UUID.randomUUID()}"
    }

    @After
    fun tearDown() {
        credentialsStore.clearPassword("server-1")
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate_1_to_2_creates_host_keys_table() {
        val helper = createHelper(
            version = 1,
            onCreate = { }
        )
        val db = helper.writableDatabase

        Migration1To2().migrate(db)

        assertTrue(tableExists(db, "host_keys"))

        helper.close()
    }

    @Test
    fun migrate_moves_password_to_secure_store_and_drops_password_column() {
        val helper = createVersion2Helper()
        val db = helper.writableDatabase

        db.execSQL(
            """
            INSERT INTO servers (id, name, hostname, port, username, password, authType, keyFilePath, distro, version)
            VALUES ('server-1', 'Server', 'example.com', 22, 'root', 'secret', 'PASSWORD', NULL, 'Ubuntu', '24.04')
            """.trimIndent()
        )

        Migration2To3(credentialsStore).migrate(db)

        db.query("SELECT * FROM servers WHERE id = 'server-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Server", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("PASSWORD", cursor.getString(cursor.getColumnIndexOrThrow("authType")))
            assertEquals(-1, cursor.getColumnIndex("password"))
        }
        assertEquals("secret", credentialsStore.getPassword("server-1"))

        helper.close()
    }

    @Test
    fun migrate_3_to_4_adds_monitoring_columns_and_metric_records() {
        val helper = createVersion3Helper()
        val db = helper.writableDatabase

        db.execSQL(
            """
            INSERT INTO servers (id, name, hostname, port, username, authType, keyFilePath, distro, version)
            VALUES ('server-1', 'Server', 'example.com', 22, 'root', 'NONE', NULL, 'Ubuntu', '24.04')
            """.trimIndent()
        )

        Migration3To4().migrate(db)

        db.query("SELECT * FROM servers WHERE id = 'server-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("pollIntervalSeconds")))
            assertEquals(60, cursor.getInt(cursor.getColumnIndexOrThrow("alertLookbackMinutes")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("publicIPEnabled")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isFavorite")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("autoConnect")))
        }
        assertTrue(tableExists(db, "metric_records"))
        assertTrue(indexExists(db, "index_metric_records_serverId_timestamp"))

        helper.close()
    }

    @Test
    fun migrate_4_to_5_adds_key_and_host_key_audit_tables() {
        val helper = createVersion4Helper()
        val db = helper.writableDatabase

        Migration4To5().migrate(db)

        db.query("PRAGMA table_info(servers)").use { cursor ->
            val names = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex))
            }
            assertTrue(names.contains("keyDataId"))
        }
        assertTrue(tableExists(db, "ssh_keys"))
        assertTrue(tableExists(db, "host_key_audit_events"))
        assertTrue(indexExists(db, "index_host_key_audit_events_timestamp"))
        assertTrue(indexExists(db, "index_host_key_audit_events_hostnamePort"))

        helper.close()
    }

    private fun createVersion2Helper(): SupportSQLiteOpenHelper {
        return createHelper(
            version = 2,
            onCreate = { db ->
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS servers (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        hostname TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT,
                        authType TEXT NOT NULL,
                        keyFilePath TEXT,
                        distro TEXT NOT NULL,
                        version TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        )
    }

    private fun createVersion3Helper(): SupportSQLiteOpenHelper {
        return createHelper(
            version = 3,
            onCreate = { db ->
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS servers (
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
            }
        )
    }

    private fun createVersion4Helper(): SupportSQLiteOpenHelper {
        return createHelper(
            version = 4,
            onCreate = { db ->
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS servers (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        hostname TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        username TEXT NOT NULL,
                        authType TEXT NOT NULL,
                        keyFilePath TEXT,
                        distro TEXT NOT NULL,
                        version TEXT NOT NULL,
                        pollIntervalSeconds INTEGER NOT NULL,
                        alertLookbackMinutes INTEGER NOT NULL,
                        publicIPEnabled INTEGER NOT NULL,
                        isFavorite INTEGER NOT NULL,
                        autoConnect INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
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
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_metric_records_serverId_timestamp ON metric_records (serverId, timestamp)")
            }
        )
    }

    private fun createHelper(
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit
    ): SupportSQLiteOpenHelper {
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        onCreate(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun indexExists(db: SupportSQLiteDatabase, index: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(index)).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
