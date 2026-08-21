package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hband_sensor_metrics")
data class HBandSensorMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val timestamp: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val heartRate: Int,
    val systolicBp: Int,
    val diastolicBp: Int,
    val spO2: Int,
    val temperatureCelsius: Float,
    val steps: Int,
    val calories: Float,
    val distanceMeters: Float,
    val hrvScore: Int,
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val awakeMinutes: Int
)
