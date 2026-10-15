package com.next2u.healthsync.shared.model

/**
 * Domain models shared by Android (later) and iOS. Field names and omit-rules
 * match `com.example.data.model` so the Core ingest contract stays one source.
 */
data class HBandDevice(
    val deviceId: String,
    val name: String,
    val macAddress: String,
    val batteryLevel: Int? = null,
    val batteryIsSimulated: Boolean = false,
    val rssi: Int? = null,
    val isConnected: Boolean = false,
    val firmwareVersion: String? = null,
)

data class BloodPressure(
    val systolic: Int = 0,
    val diastolic: Int = 0,
)

data class SleepSummary(
    val deepSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
) {
    val totalSleepMinutes: Int get() = deepSleepMinutes + lightSleepMinutes + awakeMinutes
}

data class HBandTelemetry(
    val deviceId: String,
    val deviceModel: String,
    val timestamp: String,
    val heartRate: Int,
    val bloodPressure: BloodPressure = BloodPressure(),
    val spO2: Int = 0,
    val temperatureCelsius: Float = 0f,
    val steps: Int = 0,
    val calories: Float = 0f,
    val distanceMeters: Float = 0f,
    val hrvScore: Int = 0,
    val sleepSummary: SleepSummary = SleepSummary(),
    val isRealSensorData: Boolean = false,
)

data class HealthCheckResponse(
    val status: String? = "ok",
    val timestamp: String? = null,
    val service: String? = "healthtech-secure-api",
    val version: String? = "1.0.0",
)

data class IngestResponse(
    val success: Boolean = true,
    val message: String? = null,
    val id: String? = null,
    val processedAt: String? = null,
    val ingestStatus: String? = null,
    val duplicate: Boolean? = null,
    val clientReadingId: String? = null,
    val readingId: String? = null,
    val patientId: String? = null,
)

data class QueuedReading(
    val clientReadingId: String,
    val payloadJson: String,
    val patientId: String? = null,
)
