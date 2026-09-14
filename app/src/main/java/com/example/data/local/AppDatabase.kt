package com.example.data.local

import android.content.Context
import android.util.Log
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
        UserProfileEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingestQueueDao(): IngestQueueDao
    abstract fun sensorMetricDao(): HBandSensorMetricDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun breathingDao(): BreathingDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        private const val TAG = "AppDatabase"
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

        private fun buildDatabase(context: Context): AppDatabase {
            loadSqlCipherNativeLibrary()
            replaceLegacyUnencryptedFile(context)

            val builder = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration(dropAllTables = true)

            if (SqlCipherNative.isLoaded) {
                val passphrase = SqlCipherPassphrase.getPassphrase(context)
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
            } else {
                Log.w(TAG, "Opening Room without SQLCipher (native library not loaded)")
            }

            return builder.build()
        }

        private fun replaceLegacyUnencryptedFile(context: Context) {
            val file = context.getDatabasePath(DATABASE_NAME)
            if (SqliteFileInspector.looksLikeUnencryptedSqlite(file)) {
                Log.w(TAG, "Removing leftover unencrypted SQLite file before SQLCipher open")
                SqliteFileInspector.deleteSidecars(file)
            }
        }
    }
}
