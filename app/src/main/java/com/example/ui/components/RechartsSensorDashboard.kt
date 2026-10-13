package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.ExpectedRangePlaceholders
import com.example.ui.theme.LocalVitalSignalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChartMetricType(val label: String) {
    SEVEN_DAY_SUMMARY("Resumo de 7 dias"),
    HEART_RATE("Batimentos"),
    BLOOD_PRESSURE("Pressão arterial"),
    SPO2("Oxigênio no sangue"),
    TEMPERATURE("Temperatura"),
    ACTIVITY("Passos e calorias"),
}

@Composable
fun RechartsSensorDashboard(
    sensorMetrics: List<HBandSensorMetricEntity>?,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true,
    readFailed: Boolean = false,
    onRetryRead: () -> Unit = {},
    initialMetric: ChartMetricType = ChartMetricType.SEVEN_DAY_SUMMARY,
) {
    if (readFailed) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Histórico", style = MaterialTheme.typography.headlineMedium)
            LocalReadNotice("o histórico", onRetryRead)
        }
        return
    }
    var selectedMetric by rememberSaveable { mutableStateOf(initialMetric) }
    var menuOpen by remember { mutableStateOf(false) }
    var showGraph by rememberSaveable { mutableStateOf(initialMetric != ChartMetricType.SEVEN_DAY_SUMMARY) }
    var period by rememberSaveable { mutableStateOf(ChartPeriod.D7) }
    val now by rememberHistoryTime()
    val records = remember(sensorMetrics, selectedMetric, now) {
        sensorMetrics?.filter { it.timestampMillis > 0 && it.timestampMillis <= now }
            ?.filter { savedMetricValue(it, selectedMetric) != null }
            ?.sortedByDescending { it.timestampMillis }?.take(15)
    }
    val columnModifier = if (isScrollable) modifier.fillMaxSize().verticalScroll(rememberScrollState())
        else modifier.fillMaxWidth()

    Column(
        columnModifier.testTag("recharts_sensor_dashboard"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Histórico", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("Escolha o que deseja consultar.", style = MaterialTheme.typography.bodyLarge)
        Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("history_metric_menu"),
            ) { Text("Ver: ${selectedMetric.label}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ChartMetricType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { selectedMetric = type; menuOpen = false; showGraph = false },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
        }
        if (selectedMetric == ChartMetricType.SEVEN_DAY_SUMMARY) {
            RechartsSevenDaySummaryCard(metrics = sensorMetrics)
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (selectedMetric == ChartMetricType.HEART_RATE) "Batimentos por minuto" else selectedMetric.label,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (records == null) {
                        Text("Carregando registros…", style = MaterialTheme.typography.bodyLarge)
                    } else if (records.isEmpty()) {
                        Text("Sem medições disponíveis", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ainda não há registros com data e valor disponíveis para esta medição. Confira a conexão na aba Relógio.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        Text(
                            "Até 15 registros mais recentes neste celular. As datas seguem o horário do celular.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (selectedMetric in listOf(
                                ChartMetricType.HEART_RATE,
                                ChartMetricType.SPO2,
                                ChartMetricType.TEMPERATURE,
                                ChartMetricType.BLOOD_PRESSURE,
                            )
                        ) {
                            ChartPeriodRow(selected = period, onSelect = { period = it })
                            OutlinedButton(
                                onClick = { showGraph = !showGraph },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) { Text(if (showGraph) "Fechar gráfico" else "Ver gráfico") }
                            if (showGraph) SavedReadingsPlot(sensorMetrics, selectedMetric, period, now)
                        }
                        // Use the phone's current zone on refresh, not the zone from opening this view.
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
                        records.forEach { record ->
                            Column(
                                modifier = Modifier.semantics(mergeDescendants = true) {},
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(dateFormat.format(Date(record.timestampMillis)), style = MaterialTheme.typography.bodyLarge)
                                Text(savedMetricValue(record, selectedMetric).orEmpty(), style = MaterialTheme.typography.titleLarge)
                            }
                            HorizontalDivider()
                        }
                        Text(
                            "A ausência de um registro não significa que a medição foi normal.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

internal fun savedMetricValue(record: HBandSensorMetricEntity, type: ChartMetricType): String? {
    val locale = Locale.forLanguageTag("pt-BR")
    return when (type) {
        ChartMetricType.SEVEN_DAY_SUMMARY -> null
        ChartMetricType.HEART_RATE -> record.heartRate.takeIf { it > 0 }?.let { "$it bpm" }
        ChartMetricType.BLOOD_PRESSURE -> if (record.systolicBp > 0 && record.diastolicBp > 0)
            "${record.systolicBp} / ${record.diastolicBp} mmHg" else null
        ChartMetricType.SPO2 -> record.spO2.takeIf { it > 0 }?.let { "$it%" }
        ChartMetricType.TEMPERATURE -> record.temperatureCelsius.takeIf { it.isFinite() && it > 0f }
            ?.let { String.format(locale, "%.1f °C", it) }
        ChartMetricType.ACTIVITY -> listOfNotNull(
            record.steps.takeIf { it > 0 }?.let { "$it passos" },
            record.calories.takeIf { it.isFinite() && it > 0f }?.let { String.format(locale, "%.0f kcal", it) },
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/** Line uses stored timestamps and real values only. Expected range is a configurable placeholder. */
@Composable
private fun SavedReadingsPlot(
    metrics: List<HBandSensorMetricEntity>?,
    type: ChartMetricType,
    period: ChartPeriod,
    nowMillis: Long,
) {
    val signals = LocalVitalSignalColors.current
    val (range, color, valueOf) = when (type) {
        ChartMetricType.HEART_RATE -> Triple(
            ExpectedRangePlaceholders.heartRate,
            signals.heartRate,
            { record: HBandSensorMetricEntity -> record.heartRate.takeIf { it > 0 }?.toFloat() },
        )
        ChartMetricType.SPO2 -> Triple(
            ExpectedRangePlaceholders.spo2,
            signals.spo2,
            { record: HBandSensorMetricEntity -> record.spO2.takeIf { it > 0 }?.toFloat() },
        )
        ChartMetricType.TEMPERATURE -> Triple(
            ExpectedRangePlaceholders.temperature,
            signals.temperature,
            { record: HBandSensorMetricEntity -> record.temperatureCelsius.takeIf { it.isFinite() && it > 0f } },
        )
        ChartMetricType.BLOOD_PRESSURE -> Triple(
            ExpectedRangePlaceholders.systolic,
            signals.bloodPressure,
            { record: HBandSensorMetricEntity ->
                val sys = record.systolicBp.takeIf { it > 0 }
                val dia = record.diastolicBp.takeIf { it > 0 }
                if (sys != null && dia != null) sys.toFloat() else null
            },
        )
        else -> return
    }
    val points = trendPointsInPeriod(metrics, nowMillis, period, valueOf)
    val diastolic = if (type == ChartMetricType.BLOOD_PRESSURE) {
        trendPointsInPeriod(metrics, nowMillis, period) { record ->
            val sys = record.systolicBp.takeIf { it > 0 }
            val dia = record.diastolicBp.takeIf { it > 0 }
            if (sys != null && dia != null) dia.toFloat() else null
        }
    } else {
        emptyList()
    }
    if (points.isNotEmpty()) {
        val peak = points.maxBy { it.value }
        val low = points.minBy { it.value }
        Text("Maior valor neste período: ${formatTrendValue(type, peak.value)}", style = MaterialTheme.typography.bodyMedium)
        TrendChart(
            points = points,
            range = range,
            color = color,
            secondaryPoints = diastolic,
            chartDescription = "Gráfico de ${type.label}. Valores e horários disponíveis na lista abaixo.",
        )
        Text("Menor valor neste período: ${formatTrendValue(type, low.value)}", style = MaterialTheme.typography.bodyMedium)
    } else {
        TrendChart(points = emptyList(), range = range, color = color)
    }
}

private fun formatTrendValue(type: ChartMetricType, value: Float): String {
    val locale = Locale.forLanguageTag("pt-BR")
    return when (type) {
        ChartMetricType.HEART_RATE -> "${value.toInt()} bpm"
        ChartMetricType.SPO2 -> "${value.toInt()}%"
        ChartMetricType.TEMPERATURE -> String.format(locale, "%.1f °C", value)
        ChartMetricType.BLOOD_PRESSURE -> "${value.toInt()} mmHg"
        else -> value.toString()
    }
}
