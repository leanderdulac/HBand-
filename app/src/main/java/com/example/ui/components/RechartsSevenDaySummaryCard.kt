package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.local.HBandSensorMetricEntity

@Composable
fun RechartsSevenDaySummaryCard(
    metrics: List<HBandSensorMetricEntity>,
    modifier: Modifier = Modifier,
) {
    val now by rememberHistoryTime()
    val days = remember(metrics, now) { buildSavedWeek(metrics, now) }
    var daysAgo by rememberSaveable { mutableIntStateOf(0) }
    val selectedDay = days[6 - daysAgo]

    Card(modifier.fillMaxWidth().testTag("recharts_7day_summary_card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Últimos 7 dias", style = MaterialTheme.typography.titleLarge)
            Text(
                "Registros salvos neste aparelho. As datas seguem o horário do aparelho.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                selectedDay.dateLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("history_selected_date").semantics { liveRegion = LiveRegionMode.Polite },
            )
            HistoryDayControls(daysAgo, { daysAgo++ }, { daysAgo-- }, { daysAgo = 0 })
            SavedDayValues(selectedDay)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryDayControls(daysAgo: Int, previous: () -> Unit, next: () -> Unit, today: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val labelWidth = maxOf(measurer.measure("Dia anterior", style).size.width, measurer.measure("Dia seguinte", style).size.width)
    val minimumButtonWidth = with(LocalDensity.current) { labelWidth.toDp() } + 48.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sideBySide = maxWidth >= 480.dp && maxWidth >= minimumButtonWidth * 2 + 12.dp
        val buttonWidth = if (sideBySide) (maxWidth - 12.dp) / 2 else maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = if (sideBySide) 2 else 1,
            ) {
                OutlinedButton(
                    onClick = previous, enabled = daysAgo < 6,
                    modifier = Modifier.width(buttonWidth).heightIn(min = 56.dp).testTag("history_previous_day"),
                ) { Text("Dia anterior") }
                OutlinedButton(
                    onClick = next, enabled = daysAgo > 0,
                    modifier = Modifier.width(buttonWidth).heightIn(min = 56.dp).testTag("history_next_day"),
                ) { Text("Dia seguinte") }
            }
            if (daysAgo > 0) TextButton(
                onClick = today,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("history_today"),
            ) { Text("Voltar para hoje") }
        }
    }
}

@Composable
internal fun SavedDayValues(day: SavedDaySummary) {
    if (day.recordCount == 0) {
        Text("Sem registros neste dia", style = MaterialTheme.typography.titleMedium)
        Text(
            "Para buscar dados do relógio, abra Relógio e confira a conexão.",
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        Text(
            if (day.recordCount == 1) "1 registro salvo" else "${day.recordCount} registros salvos",
            style = MaterialTheme.typography.bodyLarge,
        )
        SavedValue("Média dos batimentos por minuto", day.averageHeartRate?.let { "$it bpm" })
        SavedValue("Passos — maior valor salvo", day.highestSteps?.toString())
        SavedValue("Calorias — maior valor salvo", day.highestCalories?.let { "$it kcal" })
        Text(
            "Os registros podem estar incompletos. Não representam uma medição contínua de todo o dia.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SavedValue(label: String, value: String?) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value ?: "Sem medição disponível", style = MaterialTheme.typography.titleLarge)
    }
}
