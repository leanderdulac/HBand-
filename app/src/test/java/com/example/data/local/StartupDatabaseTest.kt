package com.example.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StartupDatabaseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() { context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    @After fun cleanup() { context.deleteDatabase(AppDatabase.DATABASE_NAME) }

    @Test fun lazy_room_is_opened_before_consumers() = runBlocking {
        val db = AppDatabase.schemaPreservingBuilder(context).allowMainThreadQueries().build()
        assertFalse(db.isOpen)
        val gate = StorageStartupGate()
        try {
            gate.initialize({ AppDatabase.verifyOpen(db) }, {
                assertTrue(db.isOpen)
                assertEquals(7, db.openHelper.writableDatabase.version)
            })
            assertTrue(gate.isReady)
        } finally { db.close() }
    }

    @Test fun unsupported_schema_blocks_and_keeps_records() = runBlocking {
        val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE preserved (id INTEGER PRIMARY KEY, data TEXT NOT NULL)")
            old.execSQL("INSERT INTO preserved VALUES (1, 'synthetic-original')")
            old.version = 5
        }
        val candidate = AppDatabase.schemaPreservingBuilder(context).allowMainThreadQueries().build()
        val gate = StorageStartupGate()
        gate.initialize({ AppDatabase.verifyOpen(candidate) }, { error("No consumer may run") })
        assertFalse(candidate.isOpen); assertFalse(gate.awaitReady())
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { old ->
            assertEquals(5, old.version)
            old.rawQuery("SELECT data FROM preserved WHERE id=1", null).use {
                assertTrue(it.moveToFirst()); assertEquals("synthetic-original", it.getString(0))
            }
        }
    }
}
