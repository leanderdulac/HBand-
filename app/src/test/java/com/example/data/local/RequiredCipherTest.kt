package com.example.data.local

import android.app.Application
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Robolectric has no Android SQLCipher binary. Only sandbox files are examined. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RequiredCipherTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseFile get() = context.getDatabasePath(AppDatabase.DATABASE_NAME)

    private fun unavailableCipher() {
        AppDatabase.loadSqlCipherNativeLibrary()
        assertFalse(SqlCipherNative.isLoaded)
    }

    @Test fun lazy_production_factory_refuses_missing_cipher() {
        unavailableCipher()
        val result = runCatching { AppDatabase.getDatabase(context) }
        result.getOrNull()?.close()
        assertTrue("Production must not return an unencrypted Room instance", result.isFailure)
        assertFalse(databaseFile.exists())
    }

    @Test fun verified_open_does_not_create_plaintext_database() {
        unavailableCipher()
        val result = runCatching { AppDatabase.openVerified(context) }
        result.getOrNull()?.close()
        assertTrue("Production opening must fail without SQLCipher", result.isFailure)
        assertFalse("No plaintext database may be created", databaseFile.exists())
    }

    @Test fun existing_opaque_database_and_sidecars_are_unchanged() {
        unavailableCipher()
        databaseFile.parentFile!!.mkdirs()
        val files = listOf(databaseFile, File(databaseFile.path + "-wal"), File(databaseFile.path + "-shm"))
        val original = files.mapIndexed { index, file ->
            ByteArray(48) { (it + index + 1).toByte() }.also { file.writeBytes(it) }
        }
        val result = runCatching { AppDatabase.getDatabase(context) }
        result.getOrNull()?.close()
        assertTrue("Missing cipher must stop before constructing Room", result.isFailure)
        files.zip(original).forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
    }

    @Test fun startup_blocks_consumers_when_cipher_is_unavailable() = runBlocking {
        unavailableCipher()
        var runtimeStarts = 0
        val gate = StorageStartupGate()
        gate.initialize({ AppDatabase.openVerified(context) }, { runtimeStarts++ })
        assertEquals(StorageStartupState.UNAVAILABLE, gate.state.value)
        assertFalse(gate.awaitReady())
        assertEquals(0, runtimeStarts)
        assertFalse(databaseFile.exists())
    }
}
