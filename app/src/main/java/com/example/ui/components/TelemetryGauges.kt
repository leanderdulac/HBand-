package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.hband.VeepooHistoryMapper
import com.example.data.model.HBandTelemetry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryGauges(telemetry: HBandTelemetry?, modifier: Modifier = Modifier) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val visible = telemetry?.takeIf { it.isRealSensorData || BuildConfig.DEBUG }
    Card(
        modifier.fillMaxWidth().testTag("latest_watch_reading"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Última leitura disponível", style = MaterialTheme.typography.titleLarge)
            if (visible == null) {
                Text("Ainda não há uma leitura disponível.", style = MaterialTheme.typography.bodyLarge)
                Text("Confira a conexão do relógio e tente ler os dados.", style = MaterialTheme.typography.bodyLarge)
            } else {
                if (!visible.isRealSensorData) {
                    Text(
                        "DEMONSTRAÇÃO — valores de teste",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("telemetry_demo_label"),
                    )
                }
                val recordedAt = VeepooHistoryMapper.parseIsoToMillis(visible.timestamp)
                Text(
                    if (recordedAt > 0) "Registro de " + SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(recordedAt))
                    else "Horário do registro indisponível",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Column(
                    Modifier.semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Batimentos por minuto", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        visible.heartRate.takeIf { it > 0 }?.let { "$it bpm" } ?: "Sem medição disponível",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.testTag("gauge_heart_rate"),
                    )
                }
                OutlinedButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("telemetry_details_button"),
                ) { Text(if (showDetails) "Fechar outras leituras" else "Ver outras leituras") }
                if (showDetails) {
                    TelemetryValue("Oxigênio no sangue", visible.spO2.takeIf { it > 0 }?.let { "$it%" }, "gauge_spo2")
                    TelemetryValue(
                        "Pressão arterial",
                        if (visible.bloodPressure.systolic > 0 && visible.bloodPressure.diastolic > 0)
                            "${visible.bloodPressure.systolic} / ${visible.bloodPressure.diastolic} mmHg" else null,
                        "gauge_blood_pressure",
                    )
                    TelemetryValue("Temperatura", visible.temperatureCelsius.takeIf { it.isFinite() && it > 0f }?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.1f °C", it) }, "gauge_temp")
                    TelemetryValue("Variação dos batimentos — índice do relógio", visible.hrvScore.takeIf { it > 0 }?.toString(), "gauge_hrv")
                    TelemetryValue("Passos", visible.steps.takeIf { it > 0 }?.toString(), "gauge_activity")
                    TelemetryValue("Calorias", visible.calories.takeIf { it.isFinite() && it > 0f }?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.0f kcal", it) }, "gauge_calories")
                    TelemetryValue("Distância", visible.distanceMeters.takeIf { it.isFinite() && it > 0f }?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.2f km", it / 1000f) }, "gauge_distance")
                }
            }
        }
    }
}

@Composable
private fun TelemetryValue(label: String, value: String?, tag: String) {
    HorizontalDivider()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value ?: "Sem medição disponível", style = MaterialTheme.typography.titleLarge)
    }
}
