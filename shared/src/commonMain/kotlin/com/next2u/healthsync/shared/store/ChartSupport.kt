package com.next2u.healthsync.shared.store

data class TrendPoint(
    val timestampMillis: Long,
    val value: Float,
)

enum class ChartPeriod(val label: String, val days: Int) {
    D7("7 dias", 7),
    D30("30 dias", 30),
    M3("3 meses", 90),
}

enum class ChartMetricType(val label: String) {
    SEVEN_DAY_SUMMARY("Resumo de 7 dias"),
    HEART_RATE("Batimentos"),
    BLOOD_PRESSURE("Pressão arterial"),
    SPO2("Oxigênio no sangue"),
    TEMPERATURE("Temperatura"),
    ACTIVITY("Passos e calorias"),
}

fun sparklineValues(
    metrics: List<SensorMetric>,
    nowMillis: Long,
    days: Int = 7,
    valueOf: (SensorMetric) -> Float?,
): List<Float> {
    if (metrics.isEmpty()) return emptyList()
    val start = nowMillis - days * 24L * 60 * 60 * 1000
    return metrics.asSequence()
        .filter { it.timestampMillis > start && it.timestampMillis <= nowMillis }
        .sortedBy { it.timestampMillis }
        .mapNotNull(valueOf)
        .toList()
}

fun trendPointsInPeriod(
    metrics: List<SensorMetric>,
    nowMillis: Long,
    period: ChartPeriod,
    valueOf: (SensorMetric) -> Float?,
): List<TrendPoint> {
    if (metrics.isEmpty()) return emptyList()
    val start = nowMillis - period.days * 24L * 60 * 60 * 1000
    return metrics.asSequence()
        .filter { it.timestampMillis > start && it.timestampMillis <= nowMillis }
        .mapNotNull { record -> valueOf(record)?.let { TrendPoint(record.timestampMillis, it) } }
        .sortedBy { it.timestampMillis }
        .toList()
}

fun SensorMetric.heartRateValue(): Float? = heartRate.takeIf { it > 0 }?.toFloat()
fun SensorMetric.spo2Value(): Float? = spO2.takeIf { it > 0 }?.toFloat()
fun SensorMetric.systolicValue(): Float? {
    val sys = systolicBp.takeIf { it > 0 }
    val dia = diastolicBp.takeIf { it > 0 }
    return if (sys != null && dia != null) sys.toFloat() else null
}
fun SensorMetric.temperatureValue(): Float? =
    temperatureCelsius.takeIf { it.isFinite() && it > 0f }

fun SensorMetric.activityLabel(): String? {
    val parts = buildList {
        if (steps > 0) add("$steps passos")
        if (calories.isFinite() && calories > 0f) add("${calories.toInt()} kcal")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
