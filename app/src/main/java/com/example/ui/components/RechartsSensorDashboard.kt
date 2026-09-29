package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.local.HBandSensorMetricEntity
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
) {
    if (readFailed) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Histórico", style = MaterialTheme.typography.headlineMedium)
            LocalReadNotice("o histórico", onRetryRead)
        }
        return
    }
    var selectedMetric by rememberSaveable { mutableStateOf(ChartMetricType.SEVEN_DAY_SUMMARY) }
    var menuOpen by remember { mutableStateOf(false) }
    var showGraph by rememberSaveable { mutableStateOf(false) }
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
                        if (selectedMetric in listOf(ChartMetricType.HEART_RATE, ChartMetricType.SPO2, ChartMetricType.TEMPERATURE)) {
                            OutlinedButton(
                                onClick = { showGraph = !showGraph },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) { Text(if (showGraph) "Fechar gráfico" else "Ver gráfico") }
                            if (showGraph) SavedReadingsPlot(records, selectedMetric)
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

/** Dots use the stored timestamps and a data-derived range. Exact values remain readable below. */
@Composable
private fun SavedReadingsPlot(records: List<HBandSensorMetricEntity>, type: ChartMetricType) {
    fun value(record: HBandSensorMetricEntity): Float = when (type) {
        ChartMetricType.HEART_RATE -> record.heartRate.toFloat()
        ChartMetricType.SPO2 -> record.spO2.toFloat()
        else -> record.temperatureCelsius
    }
    val low = records.minOf { value(it) }
    val high = records.maxOf { value(it) }
    val start = records.minOf { it.timestampMillis }
    val end = records.maxOf { it.timestampMillis }
    val ink = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    Text("Cada ponto é um registro. Veja os valores e horários na lista abaixo.", style = MaterialTheme.typography.bodyLarge)
    Text("Maior valor: ${savedMetricValue(records.maxBy { value(it) }, type)}", style = MaterialTheme.typography.bodyMedium)
    Canvas(
        Modifier.fillMaxWidth().height(160.dp).semantics {
            contentDescription = "Gráfico de ${type.label}. Valores e horários disponíveis na lista abaixo."
        },
    ) {
        val inset = 12.dp.toPx()
        val width = (size.width - 2 * inset).coerceAtLeast(1f)
        val height = (size.height - 2 * inset).coerceAtLeast(1f)
        drawLine(axis, Offset(inset, inset + height), Offset(inset + width, inset + height))
        records.forEach { record ->
            val xFraction = if (end == start) 0.5f else
                ((record.timestampMillis - start).toDouble() / (end - start)).toFloat()
            val yFraction = if (high == low) 0.5f else (value(record) - low) / (high - low)
            drawCircle(ink, 4.dp.toPx(), Offset(inset + width * xFraction, inset + height * (1 - yFraction)))
        }
    }
    Text("Menor valor: ${savedMetricValue(records.minBy { value(it) }, type)}", style = MaterialTheme.typography.bodyMedium)
}
