package com.example.data.local

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.example.data.ingest.QueueAuthorization
import java.security.MessageDigest
import java.util.UUID

/** Android/SQLCipher/Keystore tests, exclusively in the separate synthetic lab package. */
@RunWith(AndroidJUnit4::class)
class StorageAndroidLabTest {
    private lateinit var context: Context
    private val opened = mutableListOf<RoomDatabase>()
    private val oldColumns = "id,payloadJson,status,retries,createdAt,lastAttemptAt,errorMessage"
    private val otherTables = listOf("hband_sensor_metrics", "hydration_logs", "breathing_sessions",
        "user_profile", "advanced_measurements")

    @Before fun requireIsolatedEmulator() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.aistudio.hbandhealthtech.pxq97m.storagelab") {
            "Storage tests require the separate storageLab package"
        }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Synthetic emulator only" }
        check(InstrumentationRegistry.getArguments().getString("storageLab") == "synthetic-only") {
            "Explicit synthetic lab invocation required"
        }
        check(context.applicationContext.javaClass == android.app.Application::class.java)
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET))
        AppDatabase.loadSqlCipherNativeLibrary()
        assertTrue(SqlCipherNative.isLoaded)
    }

    @After fun closeOnly() { opened.forEach { it.close() } } // No deleting databases or keys.

    private fun key() = SqlCipherPassphrase.getPassphrase(context)
    private fun file(name: String) = context.getDatabasePath(name)
    private fun current(name: String, passphrase: ByteArray = key()): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabaseMigrations.MIGRATION_6_7, AppDatabaseMigrations.MIGRATION_7_8)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build().also { opened += it }

    private fun legacy(name: String): LabLegacyV6Database =
        Room.databaseBuilder(context, LabLegacyV6Database::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(key()))
            .build().also { opened += it }

    private fun snapshot(db: SupportSQLiteDatabase, table: String, columns: String = "*") =
        db.query("SELECT $columns FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                if (cursor.isNull(it)) null else cursor.getString(it)
            }) }
        }

    private fun insertLegacy(db: SupportSQLiteDatabase, id: Int, raw: String, status: String = "PENDING") {
        db.execSQL("INSERT INTO ingest_queue ($oldColumns) VALUES (?,?,?,?,?,?,?)",
            arrayOf<Any>(id, raw, status, 3, 1234L + id, 9000L, QueueAuthorization.UNAUTHORIZED_PREFIX + ": synthetic-lab"))
    }

    private fun metadataDigest(): String {
        val prefs = context.getSharedPreferences("hband_sqlcipher", Context.MODE_PRIVATE)
        val wrapped = checkNotNull(prefs.getString("wrapped_db_key", null))
        val iv = checkNotNull(prefs.getString("wrapped_db_key_iv", null))
        return MessageDigest.getInstance("SHA-256").digest("$wrapped|$iv".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Test fun seedCurrentDatabase() = runBlocking {
        // Run this once on a freshly created lab emulator; later phases preserve this file.
        check(!file(AppDatabase.DATABASE_NAME).exists()) { "Seed refuses an existing database" }
        val db = AppDatabase.openVerified(context).also { opened += it }
        val rows = listOf(
            IngestQueueEntity(id = 101, payloadJson = "{\"patient_id\":\"SYNTHETIC\"}",
                createdAt = 1234L, clientReadingId = "synthetic-pending-101"),
            IngestQueueEntity(id = 102, payloadJson = "{\"patient_id\":\"SYNTHETIC\"}",
                status = "FAILED", retries = 2, createdAt = 1235L, lastAttemptAt = 9000L,
                errorMessage = QueueAuthorization.UNAUTHORIZED_PREFIX + ": synthetic-lab", clientReadingId = "synthetic-paused-102"),
        )
        rows.forEach { db.ingestQueueDao().insertItem(it) }
        assertEquals(rows.reversed(), db.ingestQueueDao().getAllItemsSync())
        assertTrue(db.ingestQueueDao().hasAuthorizationBlock(QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX))
        db.close()
        assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(file(AppDatabase.DATABASE_NAME)))
        check(context.getSharedPreferences("storage_lab_evidence", Context.MODE_PRIVATE).edit()
            .putInt("seed_pid", Process.myPid()).putString("metadata_digest", metadataDigest()).commit())
    }

    @Test fun reopenCurrentDatabaseInNewProcess() = runBlocking {
        val evidence = context.getSharedPreferences("storage_lab_evidence", Context.MODE_PRIVATE)
        check(evidence.contains("seed_pid")) { "Seed phase required" }
        assertNotEquals(evidence.getInt("seed_pid", -1), Process.myPid())
        assertEquals(evidence.getString("metadata_digest", null), metadataDigest())
        val db = AppDatabase.openVerified(context).also { opened += it }
        val rows = db.ingestQueueDao().getAllItemsSync()
        assertEquals(listOf(102L, 101L), rows.map { it.id })
        assertEquals(listOf("synthetic-paused-102", "synthetic-pending-101"), rows.map { it.clientReadingId })
        assertEquals(listOf("FAILED", "PENDING"), rows.map { it.status })
        assertTrue(QueueAuthorization.isBlocked(rows.first()))
        assertEquals(listOf(2, 0), rows.map { it.retries })
        assertEquals(listOf(1235L, 1234L), rows.map { it.createdAt })
        assertEquals(listOf(9000L, null), rows.map { it.lastAttemptAt })
        assertEquals(listOf(QueueAuthorization.UNAUTHORIZED_PREFIX + ": synthetic-lab", null), rows.map { it.errorMessage })
        assertTrue(rows.all { it.payloadJson == "{\"patient_id\":\"SYNTHETIC\"}" })
        assertTrue(db.ingestQueueDao().hasAuthorizationBlock(QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX))
        assertEquals(evidence.getString("metadata_digest", null), metadataDigest())
    }

    @Test fun encryptedMigrationPreservesAllRowsAndStableIds() = runBlocking {
        val name = "storage-lab-migration-${UUID.randomUUID()}"
        val oldDb = legacy(name)
        val old = oldDb.openHelper.writableDatabase
        insertLegacy(old, 1, "{\"client_reading_id\":\"synthetic-existing\"}")
        insertLegacy(old, 2, "invalid legacy JSON kept verbatim", "FAILED")
        for (table in otherTables) {
            val names = mutableListOf<String>(); val values = mutableListOf<Any>()
            old.query("PRAGMA table_info($table)").use { columns ->
                while (columns.moveToNext()) {
                    names += columns.getString(1)
                    values += when (columns.getString(2)) { "INTEGER" -> 1; "REAL" -> 1.25; else -> "SYNTHETIC" }
                }
            }
            old.execSQL("INSERT INTO $table (${names.joinToString()}) VALUES (${names.joinToString { "?" }})", values.toTypedArray())
        }
        val before = snapshot(old, "ingest_queue", oldColumns)
        val others = otherTables.associateWith { snapshot(old, it) }
        oldDb.close()
        val db = current(name)
        AppDatabase.verifyOpen(db)
        val sql = db.openHelper.writableDatabase
        assertEquals(8, sql.version)
        assertEquals(before, snapshot(sql, "ingest_queue", oldColumns))
        otherTables.forEach { assertEquals(others[it], snapshot(sql, it)) }
        val rows = db.ingestQueueDao().getAllItemsSync()
        assertEquals(2, rows.map { it.clientReadingId }.distinct().size)
        assertEquals("synthetic-existing", rows.single { it.id == 1L }.clientReadingId)
        assertTrue(rows.all { com.example.data.ingest.IngestReadingIdentity.isValid(it.clientReadingId) })
        db.close()
        assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(file(name)))
        val reopened = current(name)
        assertEquals(rows, reopened.ingestQueueDao().getAllItemsSync())
        assertTrue(runCatching { reopened.ingestQueueDao().insertItem(rows.first().copy(id = 0)) }.isFailure)
        assertEquals(rows, reopened.ingestQueueDao().getAllItemsSync())
    }

    @Test fun duplicateIdentityRollsBackEncryptedMigration() {
        val name = "storage-lab-conflict-${UUID.randomUUID()}"
        val oldDb = legacy(name)
        val old = oldDb.openHelper.writableDatabase
        insertLegacy(old, 1, "{\"client_reading_id\":\"synthetic-shared\"}")
        insertLegacy(old, 2, "{\"client_reading_id\":\"synthetic-shared\"}")
        val before = snapshot(old, "ingest_queue", oldColumns)
        oldDb.close()
        val failed = current(name)
        assertTrue(runCatching { AppDatabase.verifyOpen(failed) }.isFailure)
        failed.close()
        // Open with the original Room v6 schema to verify transactional rollback.
        val restored = legacy(name).openHelper.writableDatabase
        assertEquals(6, restored.version)
        assertEquals(before, snapshot(restored, "ingest_queue", oldColumns))
        restored.query("PRAGMA table_info(ingest_queue)").use { assertEquals(7, it.count) }
    }

    @Test fun wrongKeyDoesNotReplaceEncryptedDatabase() = runBlocking {
        val name = "storage-lab-wrong-key-${UUID.randomUUID()}"
        val db = current(name)
        val row = IngestQueueEntity(id = 1, payloadJson = "SYNTHETIC", clientReadingId = "synthetic-key-row", createdAt = 42L)
        db.ingestQueueDao().insertItem(row)
        db.close()
        val before = file(name).readBytes()
        val wrong = current(name, ByteArray(32) { 7 })
        assertTrue(runCatching { AppDatabase.verifyOpen(wrong) }.isFailure)
        wrong.close()
        assertTrue("Encrypted file must not be replaced", before.contentEquals(file(name).readBytes()))
        assertEquals(listOf(row), current(name).ingestQueueDao().getAllItemsSync())
    }

    private fun atomicCutPoint(): String {
        val point = InstrumentationRegistry.getArguments().getString("atomicCutPoint")
        check(point in setOf("before-queue", "after-queue")) { "Explicit atomic cut point required" }
        return checkNotNull(point)
    }

    private fun atomicMetric(id: Long) = HBandSensorMetricEntity(
        id = id, deviceId = "SYNTHETIC-ATOMIC", timestamp = "2026-09-25T12:00:00Z",
        timestampMillis = id, heartRate = 72, systolicBp = 0, diastolicBp = 0, spO2 = 0,
        temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
        hrvScore = 0, deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
    )

    private fun atomicQueue(id: Long) = IngestQueueEntity(
        id = id, payloadJson = "{\"synthetic_metric_id\":$id}", status = "FAILED",
        retries = 2, createdAt = id, lastAttemptAt = 9000L,
        errorMessage = QueueAuthorization.UNAUTHORIZED_PREFIX + ": synthetic-atomic",
        clientReadingId = "synthetic-atomic-$id",
    )

    /** Intentionally terminates this isolated test process. Not a passing JUnit result. */
    @Test fun interruptAtomicRecording() = runBlocking {
        val point = atomicCutPoint()
        val name = "storage-lab-atomic-$point"
        check(!file(name).exists()) { "Atomic seed refuses an existing database" }
        val db = current(name)
        db.localWriteTransaction().run {
            db.sensorMetricDao().insertMetric(atomicMetric(501))
            db.ingestQueueDao().insertItem(atomicQueue(501))
        }
        db.localWriteTransaction().run {
            db.sensorMetricDao().insertMetric(atomicMetric(502))
            if (point == "after-queue") db.ingestQueueDao().insertItem(atomicQueue(502))
            assertTrue(db.inTransaction())
            assertEquals(2, db.sensorMetricDao().getAllMetricsList().size)
            assertEquals(if (point == "after-queue") 2 else 1, db.ingestQueueDao().getAllItemsSync().size)
            // Persist a marker outside SQLite only after reaching the selected uncommitted state.
            check(context.getSharedPreferences("storage_lab_atomic_$point", Context.MODE_PRIVATE)
                .edit().putInt("interrupted_pid", Process.myPid())
                .putString("metadata_digest", metadataDigest()).putString("cut_point", point).commit())
            Process.killProcess(Process.myPid())
            error("Process termination did not occur")
        }
    }

    @Test fun recoverAfterAtomicInterruption() = runBlocking {
        val point = atomicCutPoint()
        val name = "storage-lab-atomic-$point"
        check(file(name).exists()) { "Interrupted database required; never seed during recovery" }
        val evidence = context.getSharedPreferences("storage_lab_atomic_$point", Context.MODE_PRIVATE)
        assertEquals(point, evidence.getString("cut_point", null))
        assertTrue(evidence.getInt("interrupted_pid", -1) > 0)
        assertNotEquals(evidence.getInt("interrupted_pid", -1), Process.myPid())
        assertEquals(evidence.getString("metadata_digest", null), metadataDigest())
        val db = current(name)
        AppDatabase.verifyOpen(db)
        assertEquals(listOf(atomicMetric(501)), db.sensorMetricDao().getAllMetricsList())
        assertEquals(listOf(atomicQueue(501)), db.ingestQueueDao().getAllItemsSync())
        assertTrue(db.ingestQueueDao().hasAuthorizationBlock(QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX))
        // A subsequent committed pair must remain writable/readable without repairing the file.
        db.localWriteTransaction().run {
            db.sensorMetricDao().insertMetric(atomicMetric(503))
            db.ingestQueueDao().insertItem(atomicQueue(503))
        }
        db.close()
        assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(file(name)))
        val reopened = current(name)
        assertEquals(listOf(atomicMetric(503), atomicMetric(501)), reopened.sensorMetricDao().getAllMetricsList())
        assertEquals(listOf(atomicQueue(503), atomicQueue(501)), reopened.ingestQueueDao().getAllItemsSync())
        assertEquals(evidence.getString("metadata_digest", null), metadataDigest())
    }
}
