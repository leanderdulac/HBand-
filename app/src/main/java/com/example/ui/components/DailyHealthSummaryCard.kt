package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.HBandSensorMetricEntity

@Composable
fun DailyHealthSummaryCard(
    metrics: List<HBandSensorMetricEntity>,
    modifier: Modifier = Modifier,
) {
    val now by rememberHistoryTime()
    val today = remember(metrics, now) { buildSavedWeek(metrics, now).last() }
    Card(modifier.fillMaxWidth().testTag("daily_health_summary_card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Registros de hoje", style = MaterialTheme.typography.titleLarge)
            Text(today.dateLabel, style = MaterialTheme.typography.bodyLarge)
            SavedDayValues(today)
            Text(
                "Tempo em atividade: indisponível. Os registros atuais não informam essa duração.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
