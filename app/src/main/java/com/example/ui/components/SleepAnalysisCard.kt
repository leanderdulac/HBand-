package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.HBandSensorMetricEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Saved sleep fields only. No quality score, REM estimate, or average inferred from one record. */
data class SleepAnalysisSummary(
    val timestampMillis: Long,
    val totalSleepMinutes: Long,
    val deepSleepMins: Int?,
    val remSleepMins: Int? = null,
    val lightSleepMins: Int?,
    val awakeMins: Int?,
)

fun analyzeSleepMetrics(
    metrics: List<HBandSensorMetricEntity>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): SleepAnalysisSummary? {
    val start = Calendar.getInstance(timeZone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6)
    }.timeInMillis
    val latest = metrics.filter {
        it.timestampMillis > 0 && it.timestampMillis in start..now &&
            (it.deepSleepMinutes > 0 || it.lightSleepMinutes > 0)
    }.maxByOrNull { it.timestampMillis } ?: return null
    val deep = latest.deepSleepMinutes.takeIf { it > 0 }
    val light = latest.lightSleepMinutes.takeIf { it > 0 }
    return SleepAnalysisSummary(
        timestampMillis = latest.timestampMillis,
        totalSleepMinutes = (deep?.toLong() ?: 0L) + (light?.toLong() ?: 0L),
        deepSleepMins = deep,
        lightSleepMins = light,
        awakeMins = latest.awakeMinutes.takeIf { it > 0 },
    )
}

@Composable
fun SleepAnalysisCard(metrics: List<HBandSensorMetricEntity>?, modifier: Modifier = Modifier) {
    val now by rememberHistoryTime()
    val saved = remember(metrics, now) { metrics?.let { analyzeSleepMetrics(it, now) } }
    Card(
        modifier.fillMaxWidth().testTag("sleep_analysis_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Meu registro de sono", style = MaterialTheme.typography.titleLarge)
            if (metrics == null) {
                Text("Carregando registros de sono…", style = MaterialTheme.typography.bodyLarge)
            } else if (saved == null) {
                Text("Sem registro de sono disponível nos últimos 7 dias.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("sleep_waiting_empty_state"))
                Text("Conecte seu relógio. Depois, em Ajustes, abra Opções do relógio para receber o histórico.", style = MaterialTheme.typography.bodyLarge)
            } else {
                val date = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(saved.timestampMillis))
                Text("Último registro com sono nos últimos 7 dias", style = MaterialTheme.typography.bodyLarge)
                Text("Data do registro: $date", style = MaterialTheme.typography.bodyLarge)
                Text("A data do registro pode ser diferente da noite medida.", style = MaterialTheme.typography.bodyMedium)
                Text("Soma dos períodos de sono informados", style = MaterialTheme.typography.titleMedium)
                Text(sleepDuration(saved.totalSleepMinutes), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("saved_sleep_duration"))
                SleepValue("Sono profundo", saved.deepSleepMins)
                SleepValue("Sono leve", saved.lightSleepMins)
                SleepValue("Tempo acordado", saved.awakeMins)
                Text("Sono REM: não informado neste registro.", style = MaterialTheme.typography.bodyLarge)
                Text("Estes valores não indicam a qualidade do sono. O aplicativo ainda não confirma a origem de cada registro salvo.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SleepValue(label: String, minutes: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(minutes?.let { sleepDuration(it.toLong()) } ?: "Sem duração disponível", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun sleepDuration(minutes: Long): String = "${minutes / 60} h ${minutes % 60} min"
