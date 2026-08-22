package com.example.data.model

import com.squareup.moshi.JsonClass

data class HBandDevice(
    val deviceId: String = "HBAND-B57-89A4",
    val name: String = "HBand V100 Pro",
    val macAddress: String = "E4:A8:B6:12:89:A4",
    val batteryLevel: Int = 88,
    val rssi: Int = -58,
    val isConnected: Boolean = true,
    val firmwareVersion: String = "v2.4.12-HBand"
)

data class BloodPressure(
    val systolic: Int = 118,
    val diastolic: Int = 78
)

data class SleepSummary(
    val deepSleepMinutes: Int = 135,
    val lightSleepMinutes: Int = 245,
    val awakeMinutes: Int = 15
) {
    val totalSleepMinutes: Int get() = deepSleepMinutes + lightSleepMinutes + awakeMinutes
}

data class HBandTelemetry(
    val deviceId: String = "HBAND-B57-89A4",
    val deviceModel: String = "HBand V100 Pro",
    val timestamp: String,
    val heartRate: Int,
    val bloodPressure: BloodPressure,
    val spO2: Int,
    val temperatureCelsius: Float,
    val steps: Int,
    val calories: Float,
    val distanceMeters: Float,
    val hrvScore: Int,
    val sleepSummary: SleepSummary,
    // true somente quando os valores vieram de um pacote GATT real do dispositivo;
    // false para snapshots gerados por spot-check manual ou pelo simulador de teste.
    val isRealSensorData: Boolean = false
)

@JsonClass(generateAdapter = true)
data class HealthCheckResponse(
    val status: String? = "ok",
    val timestamp: String? = null,
    val service: String? = "healthtech-secure-api",
    val version: String? = "1.0.0"
)

@JsonClass(generateAdapter = true)
data class IngestResponse(
    val success: Boolean = true,
    val message: String? = "Wearable telemetry ingested successfully",
    val id: String? = null,
    val processedAt: String? = null
)
