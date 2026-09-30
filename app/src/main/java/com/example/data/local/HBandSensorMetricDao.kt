package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HBandSensorMetricDao {

    @Query("SELECT * FROM hband_sensor_metrics ORDER BY timestampMillis DESC")
    fun getAllMetrics(): Flow<List<HBandSensorMetricEntity>>

    @Query("SELECT * FROM hband_sensor_metrics ORDER BY timestampMillis DESC")
    suspend fun getAllMetricsList(): List<HBandSensorMetricEntity>

    // Read inside the same write transaction as history admission. No schema or
    // existing row changes; restrict materialization to this device/time window.
    @Query("SELECT * FROM hband_sensor_metrics WHERE deviceId = :deviceId AND timestampMillis BETWEEN :fromMillis AND :toMillis")
    suspend fun getHistoryWindow(deviceId: String, fromMillis: Long, toMillis: Long): List<HBandSensorMetricEntity>

    @Query("SELECT * FROM hband_sensor_metrics ORDER BY timestampMillis DESC LIMIT 1")
    fun getLatestMetric(): Flow<HBandSensorMetricEntity?>

    @Query("SELECT * FROM hband_sensor_metrics ORDER BY timestampMillis DESC LIMIT :limit")
    fun getRecentMetrics(limit: Int): Flow<List<HBandSensorMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: HBandSensorMetricEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<HBandSensorMetricEntity>)

    @Query("DELETE FROM hband_sensor_metrics WHERE id = :id")
    suspend fun deleteMetricById(id: Long)

    @Query("DELETE FROM hband_sensor_metrics")
    suspend fun clearAllMetrics()
}
