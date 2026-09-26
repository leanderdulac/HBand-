package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationsTest {

    @Test
    fun `clientReadingId migration is 6 to 7`() {
        val migration = AppDatabaseMigrations.MIGRATION_6_7
        assertEquals(6, migration.startVersion)
        assertEquals(7, migration.endVersion)
        assertEquals(7, AppDatabaseMigrations.VERSION_WITH_CLIENT_READING_ID)
        assertTrue(migration.endVersion > migration.startVersion)
    }

    @Test
    fun `queue compatibility migration is 7 to 8`() {
        assertEquals(7, AppDatabaseMigrations.MIGRATION_7_8.startVersion)
        assertEquals(8, AppDatabaseMigrations.MIGRATION_7_8.endVersion)
        assertEquals(8, AppDatabaseMigrations.VERSION_WITH_CANONICAL_QUEUE_DEFAULT)
    }
}
