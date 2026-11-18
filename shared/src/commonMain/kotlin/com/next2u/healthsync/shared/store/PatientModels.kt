package com.next2u.healthsync.shared.store

import com.next2u.healthsync.shared.ingest.StoredQueueItem
import com.next2u.healthsync.shared.model.HBandTelemetry
import kotlinx.serialization.Serializable

enum class StoreStatus {
    OPENING,
    READY,
    FAILED,
}

@Serializable
data class SensorMetric(
    val id: Long = 0,
    val deviceId: String,
    val timestamp: String,
    val timestampMillis: Long,
    val heartRate: Int,
    val systolicBp: Int = 0,
    val diastolicBp: Int = 0,
    val spO2: Int = 0,
    val temperatureCelsius: Float = 0f,
    val steps: Int = 0,
    val calories: Float = 0f,
    val distanceMeters: Float = 0f,
    val hrvScore: Int = 0,
    val deepSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
)

@Serializable
data class UserProfile(
    val id: String = "CURRENT_USER",
    val patientId: String = "PAT-HBAND-001",
    val fullName: String = "Alex Rivera",
    val age: Int = 32,
    val gender: String = "Masculino",
    val heightCm: Float = 178f,
    val weightKg: Float = 74f,
    val dailyStepGoal: Int = 8000,
    val targetWaterMl: Int = 2500,
    val emergencyContact: String = "+55 11 98765-4321",
    val medicalNotes: String = "Monitoramento cardiovascular preventivo com HBand VE30.",
)

@Serializable
data class HydrationLog(
    val id: Long = 0,
    val amountMl: Int,
    val timestampMillis: Long,
    val dateString: String,
)

@Serializable
data class BreathingSession(
    val id: Long = 0,
    val durationSeconds: Int,
    val patternName: String = "4-7-8 Relaxing Breath",
    val timestampMillis: Long,
    val dateString: String,
)

@Serializable
data class PatientSnapshot(
    val schemaVersion: Int = PatientStore.SCHEMA_VERSION,
    val metrics: List<SensorMetric> = emptyList(),
    val queue: List<StoredQueueItem> = emptyList(),
    val profile: UserProfile = UserProfile(),
    val hydration: List<HydrationLog> = emptyList(),
    val breathing: List<BreathingSession> = emptyList(),
    val nextMetricId: Long = 1,
    val nextHydrationId: Long = 1,
    val nextBreathingId: Long = 1,
)

fun HBandTelemetry.toMetric(id: Long, timestampMillis: Long): SensorMetric = SensorMetric(
    id = id,
    deviceId = deviceId,
    timestamp = timestamp,
    timestampMillis = timestampMillis,
    heartRate = heartRate,
    systolicBp = bloodPressure.systolic,
    diastolicBp = bloodPressure.diastolic,
    spO2 = spO2,
    temperatureCelsius = temperatureCelsius,
    steps = steps,
    calories = calories,
    distanceMeters = distanceMeters,
    hrvScore = hrvScore,
    deepSleepMinutes = sleepSummary.deepSleepMinutes,
    lightSleepMinutes = sleepSummary.lightSleepMinutes,
    awakeMinutes = sleepSummary.awakeMinutes,
)
