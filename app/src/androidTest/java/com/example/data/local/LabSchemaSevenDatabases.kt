package com.example.data.local

import androidx.room.*

/** Frozen queue schema at published PR5 e9a80ef; its other five entities are unchanged. */
@Entity(tableName = "ingest_queue", indices = [Index(value = ["clientReadingId"], unique = true)])
data class LabPublishedV7QueueRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = "PENDING",
    val retries: Int = 0,
    val createdAt: Long = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
    val clientReadingId: String,
)

/** Frozen queue schema at local ffe5210; SQL DEFAULT differs from the published v7. */
@Entity(tableName = "ingest_queue", indices = [Index(value = ["clientReadingId"], unique = true)])
data class LabLocalV7QueueRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = "PENDING",
    val retries: Int = 0,
    val createdAt: Long = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "''") val clientReadingId: String,
)

@Dao interface LabV7QueueCountDao { @Query("SELECT COUNT(*) FROM ingest_queue") fun count(): Int }

@Database(entities = [LabPublishedV7QueueRow::class, HBandSensorMetricEntity::class,
    HydrationLogEntity::class, BreathingSessionEntity::class, UserProfileEntity::class,
    AdvancedMeasurementEntity::class], version = 7, exportSchema = false)
abstract class LabPublishedV7Database : RoomDatabase() { abstract fun queue(): LabV7QueueCountDao }

@Database(entities = [LabLocalV7QueueRow::class, HBandSensorMetricEntity::class,
    HydrationLogEntity::class, BreathingSessionEntity::class, UserProfileEntity::class,
    AdvancedMeasurementEntity::class], version = 7, exportSchema = false)
abstract class LabLocalV7Database : RoomDatabase() { abstract fun queue(): LabV7QueueCountDao }
