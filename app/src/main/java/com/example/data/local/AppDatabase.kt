package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        IngestQueueEntity::class,
        HBandSensorMetricEntity::class,
        HydrationLogEntity::class,
        BreathingSessionEntity::class,
        UserProfileEntity::class,
        AdvancedMeasurementEntity::class,
    ],
    version = AppDatabaseMigrations.VERSION_WITH_CANONICAL_QUEUE_DEFAULT,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingestQueueDao(): IngestQueueDao
    abstract fun sensorMetricDao(): HBandSensorMetricDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun breathingDao(): BreathingDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun advancedMeasurementDao(): AdvancedMeasurementDao

    companion object {
        const val DATABASE_NAME = "healthtech_wearable_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Must run before the first Room open (Application.onCreate and again
         * here as a safety net for WorkManager / ContentProvider paths).
         */
        fun loadSqlCipherNativeLibrary() {
            SqlCipherNative.loadOnce()
        }

        fun getDatabase(context: Context): AppDatabase {
            val existing = INSTANCE
            if (existing != null) return existing
            return synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        /** Call off the main thread before activating any consumer; Room.build is lazy. */
        fun openVerified(context: Context): AppDatabase = synchronized(this) {
            val database = INSTANCE ?: buildDatabase(context.applicationContext)
            try {
                verifyOpen(database)
                INSTANCE = database
                database
            } catch (error: Throwable) {
                if (INSTANCE === database) INSTANCE = null
                throw error
            }
        }

        internal fun verifyOpen(database: AppDatabase) {
            try {
                database.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
                    check(cursor.moveToFirst() && cursor.getInt(0) == 1)
                }
            } catch (error: Throwable) {
                try { database.close() } catch (_: Exception) { /* Keep the opening failure. */ }
                throw error
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            loadSqlCipherNativeLibrary()
            // Test runtimes may skip the native load; production factories must still
            // refuse storage instead of silently selecting Room's plaintext helper.
            check(SqlCipherNative.isLoaded) { "Armazenamento criptografado indisponível." }
            SqliteFileInspector.requireEncryptedInput(context.getDatabasePath(DATABASE_NAME))

            val passphrase = SqlCipherPassphrase.getPassphrase(context)
            return schemaPreservingBuilder(context)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }

        // Unknown versions must fail opening, never recreate a database holding readings.
        // Tests use this same schema policy with synthetic SQLite files, without SQLCipher.
        internal fun schemaPreservingBuilder(context: Context): RoomDatabase.Builder<AppDatabase> =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(AppDatabaseMigrations.MIGRATION_6_7, AppDatabaseMigrations.MIGRATION_7_8)
    }
}
