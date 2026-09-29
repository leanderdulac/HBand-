package com.example.data.local

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Synthetic files/preferences/cipher. Does not claim Android Keystore or SQLCipher validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DatabaseKeyStorageTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var file: File
    private var wraps = 0
    private var unwraps = 0
    private var generates = 0
    private var commits = 0
    private var applies = 0
    private var failCommit = false
    private var throwCommit = false
    private var decryptFailure = false
    private var decodedSize = 32
    private var raw: ByteArray? = null
    private val cipher = object : DatabaseKeyCipher {
        override fun wrap(raw: ByteArray): Pair<String, String> {
            wraps++
            return "synthetic-wrapped" to "synthetic-iv"
        }
        override fun unwrap(wrapped: String, iv: String): ByteArray {
            unwraps++
            assertEquals("synthetic-wrapped", wrapped)
            assertEquals("synthetic-iv", iv)
            if (decryptFailure) error("private-key-provider-details")
            return ByteArray(decodedSize) { 42 }.also { raw = it }
        }
    }
    private fun storage() = DatabaseKeyStorage(cipher) {
        generates++
        ByteArray(32) { 42 }.also { raw = it }
    }
    private fun observedPrefs(): SharedPreferences = object : SharedPreferences by prefs {
        override fun edit(): SharedPreferences.Editor {
            val delegate = prefs.edit()
            return object : SharedPreferences.Editor by delegate {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    delegate.putString(key, value)
                    return this
                }
                override fun apply() { applies++; error("Key must not use async persistence") }
                override fun commit(): Boolean {
                    commits++
                    // Simulate Android's memory update even on failed disk commit.
                    val ok = delegate.commit()
                    if (throwCommit) error("private-storage-details")
                    return ok && !failCommit
                }
            }
        }
    }
    @Before fun prepare() {
        val context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("synthetic-keys", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        file = File(context.cacheDir, "key-db")
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(file.path + suffix).delete()
    }
    @After fun cleanup() {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(file.path + suffix).delete()
        prefs.edit().clear().commit()
    }
    private fun seeded() {
        prefs.edit().putString(DatabaseKeyStorage.WRAPPED_KEY, "synthetic-wrapped")
            .putString(DatabaseKeyStorage.WRAPPED_IV, "synthetic-iv").commit()
    }
    private fun blocked(action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue("Expected safe recovery failure", failure is DatabaseKeyUnavailableException)
        assertFalse(failure!!.toString().contains("private-"))
        assertNull(failure.cause)
    }

    @Test fun new_key_commits_before_return() {
        val first = storage().getOrCreate(observedPrefs(), file)
        assertEquals(1, commits); assertEquals(0, applies)
        assertArrayEquals(ByteArray(32) { 42 }, first)
        assertEquals(setOf(DatabaseKeyStorage.WRAPPED_KEY, DatabaseKeyStorage.WRAPPED_IV), prefs.all.keys)
        val reopened = storage().getOrCreate(observedPrefs(), file)
        assertArrayEquals(first, reopened)
        assertEquals(1, generates); assertEquals(1, wraps); assertEquals(1, unwraps)
        assertEquals(1, commits)
    }

    @Test fun missing_key_preserves_db_and_sidecars() {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val evidence = File(file.path + suffix)
            val bytes = "synthetic-original-$suffix".toByteArray()
            evidence.writeBytes(bytes)
            blocked { storage().getOrCreate(observedPrefs(), file) }
            assertArrayEquals(bytes, evidence.readBytes())
            assertTrue(prefs.all.isEmpty())
            evidence.delete()
        }
        file.createNewFile()
        blocked { storage().getOrCreate(observedPrefs(), file) }
        assertTrue(file.exists()); assertEquals(0L, file.length())
        assertEquals(0, generates + wraps + unwraps + commits)
    }

    @Test fun legacy_key_reopens_without_changes() {
        seeded()
        val files = listOf("", "-wal", "-shm", "-journal").map { suffix -> File(file.path + suffix) }
        files.forEach { it.writeText("synthetic-existing-content") }
        val before = prefs.all.toMap()
        repeat(2) { assertArrayEquals(ByteArray(32) { 42 }, storage().getOrCreate(observedPrefs(), file)) }
        assertEquals(before, prefs.all)
        files.forEach { assertEquals("synthetic-existing-content", it.readText()) }
        assertEquals(0, generates + wraps + commits)
        assertEquals(2, unwraps)
    }

    @Test fun partial_or_blank_metadata_is_never_replaced() {
        val variants = listOf("synthetic-wrapped" to null, null to "synthetic-iv",
            "" to "synthetic-iv", "synthetic-wrapped" to " ", "" to "")
        for ((wrapped, iv) in variants) {
            prefs.edit().clear().apply()
            val edit = prefs.edit()
            if (wrapped != null) edit.putString(DatabaseKeyStorage.WRAPPED_KEY, wrapped)
            if (iv != null) edit.putString(DatabaseKeyStorage.WRAPPED_IV, iv)
            edit.commit()
            val before = prefs.all.toMap()
            blocked { storage().getOrCreate(observedPrefs(), file) }
            assertEquals(before, prefs.all)
        }
        assertEquals(0, generates + wraps + unwraps + commits)
    }

    @Test fun malformed_metadata_stays_untouched() {
        prefs.edit().putInt(DatabaseKeyStorage.WRAPPED_KEY, 12).commit()
        val before = prefs.all.toMap()
        blocked { storage().getOrCreate(observedPrefs(), file) }
        assertEquals(before, prefs.all)
        assertEquals(0, generates + wraps + unwraps + commits)
    }

    @Test fun unreadable_key_preserves_files_and_metadata() {
        seeded(); file.writeText("synthetic encrypted bytes")
        val bytes = file.readBytes(); val before = prefs.all.toMap()
        decryptFailure = true
        repeat(2) { blocked { storage().getOrCreate(observedPrefs(), file) } }
        assertEquals(before, prefs.all); assertArrayEquals(bytes, file.readBytes())
        assertEquals(0, generates + wraps + commits)
        assertEquals(2, unwraps)
    }

    @Test fun invalid_decoded_size_is_not_returned() {
        seeded(); val before = prefs.all.toMap(); decodedSize = 16
        blocked { storage().getOrCreate(observedPrefs(), file) }
        assertTrue(raw!!.all { it == 0.toByte() })
        assertEquals(before, prefs.all)
        assertEquals(0, generates + wraps + commits)
    }

    @Test fun failed_commit_blocks_volatile_retry() {
        failCommit = true
        val keeper = storage()
        blocked { keeper.getOrCreate(observedPrefs(), file) }
        assertEquals(1, commits); assertFalse(file.exists())
        assertTrue(raw!!.all { it == 0.toByte() })
        assertFalse(prefs.all.isEmpty()) // memory is deliberately updated by the fixture
        failCommit = false
        blocked { keeper.getOrCreate(observedPrefs(), file) }
        assertEquals(0, unwraps)
        assertEquals(1, generates); assertEquals(1, commits)
    }

    @Test fun throwing_commit_also_blocks_retry() {
        throwCommit = true
        val keeper = storage()
        blocked { keeper.getOrCreate(observedPrefs(), file) }
        throwCommit = false
        blocked { keeper.getOrCreate(observedPrefs(), file) }
        assertTrue(raw!!.all { it == 0.toByte() })
        assertEquals(0, unwraps); assertEquals(1, commits)
    }

    @Test fun concurrent_first_open_creates_one_key() {
        val keeper = storage()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..8).map { pool.submit<ByteArray> { keeper.getOrCreate(observedPrefs(), file) } }
                .map { it.get(5, TimeUnit.SECONDS) }
            results.forEach { assertArrayEquals(ByteArray(32) { 42 }, it) }
            assertEquals(1, generates); assertEquals(1, wraps); assertEquals(1, commits)
            assertEquals(7, unwraps)
        } finally { pool.shutdownNow() }
    }

    @Test fun platform_checks_have_safe_errors() {
        val broken = object : SharedPreferences by prefs {
            override fun contains(key: String?) = throw SecurityException("private-preference-path")
        }
        blocked { storage().getOrCreate(broken, file) }
        val brokenGenerator = DatabaseKeyStorage(cipher) { error("private-generator-provider") }
        blocked { brokenGenerator.getOrCreate(prefs, file) }
        assertEquals(0, wraps + commits)
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun wrapping_failure_returns_no_key_or_metadata() {
        val brokenCipher = object : DatabaseKeyCipher {
            override fun wrap(raw: ByteArray): Pair<String, String> = error("private-keystore-details")
            override fun unwrap(wrapped: String, iv: String): ByteArray = error("Unexpected unwrap")
        }
        val generated = ByteArray(32) { 42 }
        blocked { DatabaseKeyStorage(brokenCipher) { generated }.getOrCreate(observedPrefs(), file) }
        assertTrue(generated.all { it == 0.toByte() })
        assertTrue(prefs.all.isEmpty()); assertFalse(file.exists())
        assertEquals(0, commits)
    }
}
