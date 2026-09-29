package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "advanced_measurements")
data class AdvancedMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val kind: String,
    val timestamp: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val summary: String,
    val numericValue: Float = 0f,
    val secondaryValue: Float = 0f,
    val sampleCount: Int = 0,
    val payloadJson: String = "",
    val isReal: Boolean = true,
)

object AdvancedMeasurementKind {
    const val ECG = "ECG"
    const val GLUCOSE = "GLUCOSE"
    const val BLOOD_COMPONENT = "BLOOD_COMPONENT"
    const val BODY_COMPONENT = "BODY_COMPONENT"
    const val EMOTION = "EMOTION"
    const val FATIGUE = "FATIGUE"
    const val BREATH = "BREATH"
}
