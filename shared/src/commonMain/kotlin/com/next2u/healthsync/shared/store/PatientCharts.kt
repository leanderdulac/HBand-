package com.next2u.healthsync.shared.store

/** Host-facing chart series from the encrypted store. Real values only. */
object PatientCharts {
    fun allMetrics(): List<SensorMetric> = PatientStore.metrics()

    fun heartSparkline(nowMillis: Long): List<Float> =
        sparklineValues(PatientStore.metrics(), nowMillis) { it.heartRateValue() }

    fun spo2Sparkline(nowMillis: Long): List<Float> =
        sparklineValues(PatientStore.metrics(), nowMillis) { it.spo2Value() }

    fun pressureSparkline(nowMillis: Long): List<Float> =
        sparklineValues(PatientStore.metrics(), nowMillis) { it.systolicValue() }

    fun temperatureSparkline(nowMillis: Long): List<Float> =
        sparklineValues(PatientStore.metrics(), nowMillis) { it.temperatureValue() }

    fun heartTrend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, period) { it.heartRateValue() }

    fun spo2Trend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, period) { it.spo2Value() }

    fun pressureTrend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, period) { it.systolicValue() }

    fun diastolicTrend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, period) { it.diastolicBp.takeIf { v -> v > 0 }?.toFloat() }

    fun temperatureTrend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, period) { it.temperatureValue() }

    fun latest(): SensorMetric? = PatientStore.latestMetric()

    fun hasPressureHistory(): Boolean = PatientStore.metrics().any { it.systolicValue() != null }
    fun hasTemperatureHistory(): Boolean = PatientStore.metrics().any { it.temperatureValue() != null }
}
