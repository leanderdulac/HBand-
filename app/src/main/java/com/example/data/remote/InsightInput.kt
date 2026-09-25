package com.example.data.remote

import com.example.data.local.HBandSensorMetricEntity

/** Activity-only observations must not enter the existing clinical insight path as zero vitals. */
internal fun clinicalInsightRecords(records: List<HBandSensorMetricEntity>): List<HBandSensorMetricEntity> =
    records.filter { row ->
        row.heartRate > 0 || row.systolicBp > 0 || row.diastolicBp > 0 || row.spO2 > 0 ||
            (row.temperatureCelsius.isFinite() && row.temperatureCelsius > 0f) || row.hrvScore > 0 ||
            row.deepSleepMinutes > 0 || row.lightSleepMinutes > 0 || row.awakeMinutes > 0
    }
