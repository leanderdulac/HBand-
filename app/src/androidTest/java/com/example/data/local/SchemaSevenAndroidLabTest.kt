package com.example.data.local

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ingest.QueueAuthorization
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Three separately invoked processes; keeps every synthetic database and wrapped key. */
@RunWith(AndroidJUnit4::class)
class SchemaSevenAndroidLabTest {
    private lateinit var context: Context
    private val opened = mutableListOf<RoomDatabase>()
    private val cases = listOf("published", "local", "validation-rollback")
    private val tables = listOf("ingest_queue", "hband_sensor_metrics", "hydration_logs",
        "breathing_sessions", "user_profile", "advanced_measurements")
    private val evidence get() = context.getSharedPreferences("schema_seven_lab", Context.MODE_PRIVATE)
    private fun name(case: String) = "schema-seven-lab-$case"
    private fun key() = SqlCipherPassphrase.getPassphrase(context)

    @Before fun requireIsolatedEmulator() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.aistudio.hbandhealthtech.pxq97m.storagelab")
        check(context.applicationContext.javaClass == Application::class.java)
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("storageLab") == "synthetic-only")
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        AppDatabase.loadSqlCipherNativeLibrary()
        assertTrue(SqlCipherNative.isLoaded)
    }

    @After fun closeOnly() { opened.forEach { it.close() } }

    private fun old(case: String): RoomDatabase = (if (case == "local") {
        Room.databaseBuilder(context, LabLocalV7Database::class.java, name(case))
            .openHelperFactory(SupportOpenHelperFactory(key())).build()
    } else {
        Room.databaseBuilder(context, LabPublishedV7Database::class.java, name(case))
            .openHelperFactory(SupportOpenHelperFactory(key())).build()
    }).also { opened += it }

    private fun current(case: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name(case))
            .addMigrations(AppDatabaseMigrations.MIGRATION_6_7, AppDatabaseMigrations.MIGRATION_7_8)
            .openHelperFactory(SupportOpenHelperFactory(key())).build().also { opened += it }

    // Compare storage types as well as exact text/null values, including non-queue tables.
    private fun rows(sql: SupportSQLiteDatabase, query: String): String = sql.query(query).use { cursor ->
        JSONArray().apply {
            while (cursor.moveToNext()) put(JSONArray().apply {
                for (i in 0 until cursor.columnCount) put(JSONArray().apply {
                    put(cursor.getType(i)); put(if (cursor.isNull(i)) org.json.JSONObject.NULL else cursor.getString(i))
                })
            })
        }.toString()
    }

    private fun snapshot(sql: SupportSQLiteDatabase): String = JSONArray().apply {
        tables.forEach { put(rows(sql, "SELECT * FROM $it ORDER BY id")) }
    }.toString()

    private fun schema(sql: SupportSQLiteDatabase) = rows(sql,
        "SELECT type,name,sql FROM sqlite_master ORDER BY type,name")
    private fun identity(sql: SupportSQLiteDatabase) = rows(sql,
        "SELECT identity_hash FROM room_master_table WHERE id=42")
    private fun assertSequence(sql: SupportSQLiteDatabase, expected: Long) {
        sql.query("SELECT seq FROM sqlite_sequence WHERE name='ingest_queue'").use {
            assertTrue(it.moveToFirst()); assertEquals(expected, it.getLong(0)); assertFalse(it.moveToNext())
        }
    }

    private fun metadataDigest(): String {
        val prefs = context.getSharedPreferences("hband_sqlcipher", Context.MODE_PRIVATE)
        val wrapped = checkNotNull(prefs.getString("wrapped_db_key", null))
        val iv = checkNotNull(prefs.getString("wrapped_db_key_iv", null))
        return MessageDigest.getInstance("SHA-256").digest("$wrapped|$iv".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun seed(sql: SupportSQLiteDatabase) {
        for ((i, status) in listOf("PENDING", "FAILED", "FAILED", "SYNCED").withIndex()) {
            sql.execSQL("INSERT INTO ingest_queue VALUES (?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                i + 1, "{\"synthetic\":true,\"original\":$i}", status, i, 1000L + i,
                if (i == 0) null else 2000L + i,
                when (i) { 1 -> QueueAuthorization.UNAUTHORIZED_PREFIX; 2 -> QueueAuthorization.FORBIDDEN_PREFIX; else -> null },
                "synthetic-stable-id-$i"))
        }
        sql.execSQL("INSERT INTO ingest_queue VALUES (900,'SYNTHETIC','PENDING',0,0,NULL,NULL,'synthetic-removed')")
        sql.execSQL("DELETE FROM ingest_queue WHERE id=900")
        tables.drop(1).forEach { table ->
            val names = mutableListOf<String>(); val values = mutableListOf<Any>()
            sql.query("PRAGMA table_info($table)").use { columns ->
                while (columns.moveToNext()) {
                    names += columns.getString(1)
                    values += when (columns.getString(2)) { "INTEGER" -> 1; "REAL" -> 1.25; else -> "SYNTHETIC" }
                }
            }
            sql.execSQL("INSERT INTO $table (${names.joinToString()}) VALUES (${names.joinToString { "?" }})", values.toTypedArray())
        }
    }

    @Test fun seedBothV7Origins() {
        check(!evidence.contains("seed_pid")) { "Never reseed a retained lab" }
        cases.forEach { check(!context.getDatabasePath(name(it)).exists()) }
        val record = evidence.edit()
        for (case in cases) {
            val db = old(case)
            val sql = db.openHelper.writableDatabase
            assertEquals(7, sql.version)
            sql.query("PRAGMA cipher_version").use {
                assertTrue(it.moveToFirst()); assertTrue(it.getString(0).isNotBlank())
                println("SQLCipher=${it.getString(0)}; case=$case")
            }
            seed(sql)
            if (case == "validation-rollback") {
                sql.execSQL("ALTER TABLE hydration_logs ADD COLUMN unknown_field TEXT DEFAULT 'SYNTHETIC-PRESERVED'")
            }
            record.putString("$case-before", snapshot(sql))
                .putString("$case-schema", schema(sql)).putString("$case-identity", identity(sql))
            assertSequence(sql, 900)
            db.close()
            assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(context.getDatabasePath(name(case))))
        }
        check(record.putInt("seed_pid", Process.myPid()).putString("key_digest", metadataDigest()).commit())
    }

    @Test fun migrateBothOriginsAndVerifyRollback() = runBlocking {
        check(evidence.contains("seed_pid") && !evidence.contains("migration_pid"))
        assertNotEquals(evidence.getInt("seed_pid", -1), Process.myPid())
        assertEquals(evidence.getString("key_digest", null), metadataDigest())
        for (case in cases.take(2)) {
            check(context.getDatabasePath(name(case)).exists())
            val db = current(case)
            AppDatabase.verifyOpen(db)
            val sql = db.openHelper.writableDatabase
            assertEquals(8, sql.version)
            assertEquals(evidence.getString("$case-before", null), snapshot(sql))
            assertSequence(sql, 900)
            val dao = db.ingestQueueDao()
            assertTrue(dao.hasAuthorizationBlock(QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX))
            val original = dao.getAllItemsSync()
            assertEquals(4, original.size)
            assertTrue(runCatching { dao.insertItem(original.first().copy(id=0)) }.isFailure)
            assertEquals(original, dao.getAllItemsSync())
            assertSequence(sql, 900)
            assertEquals(901L, dao.insertItem(original.first().copy(id=0, clientReadingId="synthetic-new-after-migration")))
            // Room hashes describe schema, not version: the canonical local v7 equals v8.
            if (case == "published") assertNotEquals(evidence.getString("$case-identity", null), identity(sql))
            else assertEquals(evidence.getString("$case-identity", null), identity(sql))
            check(evidence.edit().putString("$case-after", snapshot(sql)).putString("$case-new-identity", identity(sql)).commit())
            db.close()
        }
        val invalid = "validation-rollback"
        check(context.getDatabasePath(name(invalid)).exists())
        val failed = current(invalid)
        val result = runCatching { AppDatabase.verifyOpen(failed) }
        assertTrue("Room must reject the unknown hydration column", result.isFailure)
        assertTrue(generateSequence(result.exceptionOrNull()) { it.cause }
            .any { it.message?.contains("Migration didn't properly handle: hydration_logs") == true })
        failed.close()
        assertRetainedV7()
        assertEquals(evidence.getString("key_digest", null), metadataDigest())
        check(evidence.edit().putInt("migration_pid", Process.myPid()).commit())
    }

    private fun assertRetainedV7() {
        val case = "validation-rollback"
        val db = old(case)
        val sql = db.openHelper.writableDatabase
        assertEquals(7, sql.version)
        assertEquals(evidence.getString("$case-before", null), snapshot(sql))
        assertEquals(evidence.getString("$case-schema", null), schema(sql))
        assertEquals(evidence.getString("$case-identity", null), identity(sql))
        assertSequence(sql, 900)
        db.close()
    }

    @Test fun reopenV8AndRetainedV7InNewProcess() {
        check(evidence.contains("migration_pid"))
        assertNotEquals(evidence.getInt("seed_pid", -1), Process.myPid())
        assertNotEquals(evidence.getInt("migration_pid", -1), Process.myPid())
        assertEquals(evidence.getString("key_digest", null), metadataDigest())
        for (case in cases.take(2)) {
            check(context.getDatabasePath(name(case)).exists())
            val db = current(case)
            AppDatabase.verifyOpen(db)
            val sql = db.openHelper.writableDatabase
            assertEquals(8, sql.version)
            assertEquals(evidence.getString("$case-after", null), snapshot(sql))
            assertEquals(evidence.getString("$case-new-identity", null), identity(sql))
            assertSequence(sql, 901)
            db.close()
        }
        assertRetainedV7()
        cases.forEach { assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(context.getDatabasePath(name(it)))) }
        assertEquals(evidence.getString("key_digest", null), metadataDigest())
    }
}
