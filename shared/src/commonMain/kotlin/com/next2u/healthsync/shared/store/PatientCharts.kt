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
        heartTrend(nowMillis, period.days)

    fun spo2Trend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        spo2Trend(nowMillis, period.days)

    fun pressureTrend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        pressureTrend(nowMillis, period.days)

    fun diastolicTrend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        diastolicTrend(nowMillis, period.days)

    fun temperatureTrend(nowMillis: Long, period: ChartPeriod): List<TrendPoint> =
        temperatureTrend(nowMillis, period.days)

    /** Int days so Swift never has to pass a Kotlin Native enum. */
    fun heartTrend(nowMillis: Long, days: Int): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, days) { it.heartRateValue() }

    fun spo2Trend(nowMillis: Long, days: Int): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, days) { it.spo2Value() }

    fun pressureTrend(nowMillis: Long, days: Int): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, days) { it.systolicValue() }

    fun diastolicTrend(nowMillis: Long, days: Int): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, days) { it.diastolicBp.takeIf { v -> v > 0 }?.toFloat() }

    fun temperatureTrend(nowMillis: Long, days: Int): List<TrendPoint> =
        trendPointsInPeriod(PatientStore.metrics(), nowMillis, days) { it.temperatureValue() }

    fun latest(): SensorMetric? = PatientStore.latestMetric()

    fun hasPressureHistory(): Boolean = PatientStore.metrics().any { it.systolicValue() != null }
    fun hasTemperatureHistory(): Boolean = PatientStore.metrics().any { it.temperatureValue() != null }
}
