package com.example.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The production writer is SQLCipher, which Robolectric cannot load.
 * These tests drive the same migration with AES-GCM so a failed export,
 * a mismatched snapshot, and a successful copy are all observable on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PlaintextDatabaseMigrationTest {
    private val passphrase = ByteArray(32) { (it + 3).toByte() }

    @Test fun successful_migration_keeps_queue_and_core_key_out_of_the_plaintext_file() {
        val file = seed("success")
        assertTrue(PlaintextDatabaseMigration.migrateIfPlaintext(file, passphrase, AesSnapshotCodec))
        assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(file))
        val stored = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(stored.contains("unsent-reading-72"))
        assertFalse(stored.contains("core-key-pilot"))
        assertFalse(stored.contains("SQLite format 3"))
        val snapshot = AesSnapshotCodec.readEncrypted(file, passphrase)
        assertEquals(8, snapshot.userVersion)
        assertTrue(snapshot.rows.getValue("ingest_queue").flatten().any { it.contains("unsent-reading-72") })
        assertTrue(snapshot.rows.getValue("core_key").flatten().any { it.contains("core-key-pilot") })
        assertTrue(snapshot.rows.getValue("ingest_queue").flatten().any { it == "T:PENDING" })
        assertFalse(File(file.path + PlaintextDatabaseMigration.PRESERVED_SUFFIX).exists())
        assertFalse(File(file.path + PlaintextDatabaseMigration.RETIRED_SUFFIX).exists())
        assertFalse(File(file.path + "-wal").exists())
    }

    @Test fun failed_export_leaves_the_plaintext_database_readable() {
        val file = seed("fail-export")
        assertTrue(runCatching {
            PlaintextDatabaseMigration.migrateIfPlaintext(file, passphrase, object : EncryptedDatabaseCodec {
                override fun export(source: File, destination: File, passphrase: ByteArray) {
                    error("synthetic export failure")
                }
                override fun readEncrypted(file: File, passphrase: ByteArray) = error("unread")
            })
        }.exceptionOrNull() is MigrationFailed)
        assertTrue(SqliteFileInspector.looksLikeUnencryptedSqlite(file))
        assertEquals("unsent-reading-72", readPayload(file))
        assertEquals("core-key-pilot", readCoreKey(file))
        assertFalse(File(file.path + PlaintextDatabaseMigration.PRESERVED_SUFFIX).exists())
    }

    @Test fun mismatched_ciphertext_does_not_replace_the_original() {
        val file = seed("mismatch")
        val original = file.readBytes()
        val mismatch = object : EncryptedDatabaseCodec {
            override fun export(source: File, destination: File, passphrase: ByteArray) {
                destination.writeBytes(ByteArray(24) { 9 })
            }
            override fun readEncrypted(file: File, passphrase: ByteArray) = DatabaseSnapshot(-1, emptyMap())
        }
        assertTrue(runCatching {
            PlaintextDatabaseMigration.migrateIfPlaintext(file, passphrase, mismatch)
        }.exceptionOrNull() is MigrationFailed)
        assertArrayEquals(original, file.readBytes())
        assertEquals("core-key-pilot", readCoreKey(file))
    }

    @Test fun opaque_file_is_not_migrated() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "opaque-not-sqlite.db")
        val original = ByteArray(32) { 4 }
        file.writeBytes(original)
        var called = false
        val codec = object : EncryptedDatabaseCodec {
            override fun export(source: File, destination: File, passphrase: ByteArray) { called = true }
            override fun readEncrypted(file: File, passphrase: ByteArray) = error("unread")
        }
        assertFalse(PlaintextDatabaseMigration.migrateIfPlaintext(file, passphrase, codec))
        assertFalse(called)
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun invalid_sidecar_does_not_discard_the_plaintext_database() {
        val file = seed("bad-wal")
        File(file.path + "-wal").writeBytes("not-a-real-wal".toByteArray())
        val before = file.readBytes()
        val exploded = runCatching {
            PlaintextDatabaseMigration.migrateIfPlaintext(file, passphrase, AesSnapshotCodec)
        }
        if (exploded.isFailure) {
            assertTrue(SqliteFileInspector.looksLikeUnencryptedSqlite(file))
            assertEquals("unsent-reading-72", readPayload(file))
            assertEquals("not-a-real-wal", File(file.path + "-wal").readText())
        } else {
            // Robolectric checkpoint ignored the junk WAL and the copy still holds the row.
            assertFalse(before.contentEquals(file.readBytes()))
            assertTrue(AesSnapshotCodec.readEncrypted(file, passphrase).rows.getValue("ingest_queue")
                .flatten().any { it.contains("unsent-reading-72") })
        }
    }

    private fun seed(name: String): File {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "$name.db")
        if (file.exists()) file.delete()
        listOf("-wal", "-shm", "-journal", PlaintextDatabaseMigration.PRESERVED_SUFFIX, PlaintextDatabaseMigration.RETIRED_SUFFIX)
            .forEach { File(file.path + it).delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE ingest_queue (id INTEGER PRIMARY KEY, payload TEXT NOT NULL, status TEXT NOT NULL)")
            db.execSQL("INSERT INTO ingest_queue VALUES (7, 'unsent-reading-72', 'PENDING')")
            db.execSQL("CREATE TABLE core_key (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO core_key VALUES (1, 'core-key-pilot')")
            db.version = 8
        }
        return file
    }

    private fun readPayload(file: File): String =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT payload FROM ingest_queue WHERE id = 7", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
        }

    private fun readCoreKey(file: File): String =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM core_key WHERE id = 1", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }
        }
}

private object AesSnapshotCodec : EncryptedDatabaseCodec {
    private val magic = byteArrayOf('N'.code.toByte(), '2'.code.toByte(), 'U'.code.toByte(), 'E'.code.toByte())

    override fun export(source: File, destination: File, passphrase: ByteArray) {
        destination.writeBytes(seal(encode(AndroidPlaintextSnapshots.read(source)), passphrase))
    }

    override fun readEncrypted(file: File, passphrase: ByteArray): DatabaseSnapshot = decode(open(file.readBytes(), passphrase))

    private fun encode(snapshot: DatabaseSnapshot): ByteArray {
        val text = buildString {
            append(snapshot.userVersion)
            append('\n')
            for ((table, rows) in snapshot.rows) {
                append("TABLE ").append(table).append('\n')
                for (row in rows) {
                    append(row.joinToString("\u001f"))
                    append('\n')
                }
                append("END\n")
            }
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun decode(plain: ByteArray): DatabaseSnapshot {
        val lines = plain.toString(Charsets.UTF_8).split('\n')
        val version = lines.first().toInt()
        val rows = linkedMapOf<String, List<List<String>>>()
        var index = 1
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith("TABLE ")) {
                index++
                continue
            }
            val table = line.removePrefix("TABLE ")
            index++
            val tableRows = mutableListOf<List<String>>()
            while (index < lines.size && lines[index] != "END") {
                if (lines[index].isNotEmpty()) tableRows += lines[index].split('\u001f')
                index++
            }
            rows[table] = tableRows
            index++
        }
        return DatabaseSnapshot(version, rows)
    }

    private fun seal(plain: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return magic + cipher.iv + cipher.doFinal(plain)
    }

    private fun open(blob: ByteArray, key: ByteArray): ByteArray {
        check(blob.copyOfRange(0, magic.size).contentEquals(magic))
        val iv = blob.copyOfRange(magic.size, magic.size + 12)
        val body = blob.copyOfRange(magic.size + 12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }
}
