package com.example.data.hband

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed class GalaxyHealthPull {
    data object NeedsPermission : GalaxyHealthPull()
    data class Unavailable(val message: String) : GalaxyHealthPull()
    data class Data(
        val heartRate: Int,
        val measuredAtMs: Long,
        val spo2: Int,
        val spo2AtMs: Long,
        val systolic: Int,
        val diastolic: Int,
        val bloodPressureAtMs: Long,
        val steps: Int,
        val sawOtherHeartRate: Boolean,
        val heartSamples: List<GalaxyHeartSample> = emptyList(),
    ) : GalaxyHealthPull()
}

class GalaxyHealthReader(private val context: Context) {
    suspend fun pull(now: Instant = Instant.now()): GalaxyHealthPull {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return GalaxyHealthPull.Unavailable(GalaxyHealthMapper.NOTE_UNAVAILABLE)
        }
        val status = runCatching { HealthConnectClient.getSdkStatus(context) }.getOrElse {
            return GalaxyHealthPull.Unavailable(GalaxyHealthMapper.NOTE_UNAVAILABLE)
        }
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            return GalaxyHealthPull.Unavailable(GalaxyHealthMapper.NOTE_UNAVAILABLE)
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) {
            return GalaxyHealthPull.NeedsPermission
        }
        return try {
            readGranted(client, granted, now)
        } catch (_: SecurityException) {
            GalaxyHealthPull.NeedsPermission
        } catch (e: Exception) {
            GalaxyHealthPull.Unavailable(GalaxyHealthMapper.NOTE_UNAVAILABLE + " (" + (e.message ?: "erro") + ")")
        }
    }

    private suspend fun readGranted(
        client: HealthConnectClient,
        granted: Set<String>,
        now: Instant,
    ): GalaxyHealthPull {
        val start = now.minus(12, ChronoUnit.HOURS)
        val range = TimeRangeFilter.between(start, now)
        val hearts = readHeartSamples(client, range)
        val samsungAll = hearts
            .filter { GalaxyHealthMapper.acceptHeartRate(it.first) && GalaxyHealthMapper.isSamsungHealthOrigin(it.third) }
            .sortedBy { it.second }
        val samsung = samsungAll.maxByOrNull { it.second }
        val sawOther = hearts.any {
            GalaxyHealthMapper.acceptHeartRate(it.first) && !GalaxyHealthMapper.isSamsungHealthOrigin(it.third)
        }
        if (samsung == null) {
            return GalaxyHealthPull.Data(
                heartRate = 0,
                measuredAtMs = 0L,
                spo2 = 0,
                spo2AtMs = 0L,
                systolic = 0,
                diastolic = 0,
                bloodPressureAtMs = 0L,
                steps = 0,
                sawOtherHeartRate = sawOther,
            )
        }
        val spo2 = if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in granted) {
            readSpo2(client, range)
        } else {
            0 to 0L
        }
        val pressure = if (HealthPermission.getReadPermission(BloodPressureRecord::class) in granted) {
            readPressure(client, range)
        } else {
            Triple(0, 0, 0L)
        }
        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            readStepsToday(client, now)
        } else {
            0
        }
        return GalaxyHealthPull.Data(
            heartRate = samsung.first,
            measuredAtMs = samsung.second,
            spo2 = spo2.first,
            spo2AtMs = spo2.second,
            systolic = pressure.first,
            diastolic = pressure.second,
            bloodPressureAtMs = pressure.third,
            steps = steps,
            sawOtherHeartRate = sawOther,
            heartSamples = samsungAll.map { GalaxyHeartSample(it.first, it.second) },
        )
    }

    private suspend fun readHeartSamples(
        client: HealthConnectClient,
        range: TimeRangeFilter,
    ): List<Triple<Int, Long, String>> {
        val out = ArrayList<Triple<Int, Long, String>>(256)
        var pageToken: String? = null
        var pages = 0
        while (pages < 6 && out.size < 3000) {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = range,
                    ascendingOrder = false,
                    pageSize = 200,
                    pageToken = pageToken,
                )
            )
            for (record in response.records) {
                val origin = record.metadata.dataOrigin.packageName
                for (sample in record.samples) {
                    out.add(Triple(sample.beatsPerMinute.toInt(), sample.time.toEpochMilli(), origin))
                    if (out.size >= 3000) break
                }
            }
            pageToken = response.pageToken
            pages++
            if (pageToken.isNullOrEmpty()) break
        }
        return out
    }

    private suspend fun readSpo2(
        client: HealthConnectClient,
        range: TimeRangeFilter,
    ): Pair<Int, Long> {
        val record = client.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = range,
                ascendingOrder = false,
                pageSize = 20,
            )
        ).records
            .filter { GalaxyHealthMapper.isSamsungHealthOrigin(it.metadata.dataOrigin.packageName) }
            .maxByOrNull { it.time.toEpochMilli() }
            ?: return 0 to 0L
        return GalaxyHealthMapper.normalizePercent(record.percentage.value) to record.time.toEpochMilli()
    }

    private suspend fun readPressure(
        client: HealthConnectClient,
        range: TimeRangeFilter,
    ): Triple<Int, Int, Long> {
        val record = client.readRecords(
            ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = range,
                ascendingOrder = false,
                pageSize = 20,
            )
        ).records
            .filter { GalaxyHealthMapper.isSamsungHealthOrigin(it.metadata.dataOrigin.packageName) }
            .maxByOrNull { it.time.toEpochMilli() }
            ?: return Triple(0, 0, 0L)
        val systolic = record.systolic.inMillimetersOfMercury.toInt()
        val diastolic = record.diastolic.inMillimetersOfMercury.toInt()
        return Triple(systolic, diastolic, record.time.toEpochMilli())
    }

    private suspend fun readStepsToday(client: HealthConnectClient, now: Instant): Int {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now),
                pageSize = 1000,
            )
        ).records.filter { GalaxyHealthMapper.isSamsungHealthOrigin(it.metadata.dataOrigin.packageName) }
        val total = records.sumOf { it.count }
        return total.coerceIn(0L, 200_000L).toInt()
    }

    companion object {
        val permissions: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
        )

    }
}
