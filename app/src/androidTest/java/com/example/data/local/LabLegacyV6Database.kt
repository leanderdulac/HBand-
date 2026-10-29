package com.example.data.local

import androidx.room.*

/** Frozen v6 queue schema from 72e65f4. Other five entity schemas are unchanged. */
@Entity(tableName = "ingest_queue")
data class LabLegacyQueueRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = "PENDING",
    val retries: Int = 0,
    val createdAt: Long = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
)
@Dao interface LabLegacyQueueDao { @Query("SELECT COUNT(*) FROM ingest_queue") fun count(): Int }
@Database(entities = [LabLegacyQueueRow::class, HBandSensorMetricEntity::class, HydrationLogEntity::class,
    BreathingSessionEntity::class, UserProfileEntity::class, AdvancedMeasurementEntity::class],
    version = 6, exportSchema = false)
abstract class LabLegacyV6Database : RoomDatabase() { abstract fun queue(): LabLegacyQueueDao }
