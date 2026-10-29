package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.hband.DetectSessionUiState
import com.example.data.hband.DeviceCapabilities
import com.example.data.hband.VeepooSessionGate
import com.example.ui.theme.MinimalBorder

@Composable
fun AdvancedDetectCard(
    capabilities: DeviceCapabilities,
    hardwareConnected: Boolean,
    actionsEnabled: Boolean = hardwareConnected,
    ecg: DetectSessionUiState,
    glucose: DetectSessionUiState,
    bloodComponent: DetectSessionUiState,
    bodyComponent: DetectSessionUiState,
    emotion: DetectSessionUiState,
    fatigue: DetectSessionUiState,
    breath: DetectSessionUiState,
    onStartEcg: () -> Unit,
    onStopEcg: () -> Unit,
    onReadEcg: () -> Unit,
    onStartGlucose: () -> Unit,
    onStopGlucose: () -> Unit,
    onStartBloodComponent: () -> Unit,
    onStopBloodComponent: () -> Unit,
    onStartBodyComponent: () -> Unit,
    onStopBodyComponent: () -> Unit,
    onStartEmotion: () -> Unit,
    onStopEmotion: () -> Unit,
    onStartFatigue: () -> Unit,
    onStopFatigue: () -> Unit,
    onStartBreath: () -> Unit,
    onStopBreath: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!capabilities.hasAdvancedDetect) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("advanced_detect_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MonitorHeart,
                        contentDescription = null,
                        tint = Color(0xFF00639B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Medições do relógio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = "Recursos informados pelo relógio. Resultados ainda em validação.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF00639B)
                    )
                }
            }

            if (!hardwareConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFF4E5))
                        .padding(12.dp)
                        .testTag("advanced_detect_reconnect_hint")
                ) {
                    Text(
                        text = patientWatchConnectionHint(actionsEnabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF9A3412)
                    )
                }
            }

            if (ecg.supported) {
                DetectActionBlock(
                    title = "ECG",
                    state = ecg,
                    connected = actionsEnabled,
                    startLabel = "Iniciar ECG",
                    stopLabel = "Parar ECG",
                    extraLabel = "Ler ECG gravado",
                    testTag = "ecg",
                    onStart = onStartEcg,
                    onStop = onStopEcg,
                    onExtra = onReadEcg,
                )
                RealAdcSparkline(samples = ecg.waveform)
            }
            if (glucose.supported) {
                DetectActionBlock(
                    title = "Glicose",
                    state = glucose,
                    connected = actionsEnabled,
                    startLabel = "Medir glicose",
                    stopLabel = "Parar medição de glicose",
                    testTag = "glucose",
                    onStart = onStartGlucose,
                    onStop = onStopGlucose,
                )
            }
            if (bloodComponent.supported) {
                DetectActionBlock(
                    title = "Componentes sanguíneos",
                    state = bloodComponent,
                    connected = actionsEnabled,
                    startLabel = "Medir componentes sanguíneos",
                    stopLabel = "Parar medição de componentes sanguíneos",
                    testTag = "blood_component",
                    onStart = onStartBloodComponent,
                    onStop = onStopBloodComponent,
                )
            }
            if (bodyComponent.supported) {
                DetectActionBlock(
                    title = "Composição corporal",
                    state = bodyComponent,
                    connected = actionsEnabled,
                    startLabel = "Medir composição corporal",
                    stopLabel = "Parar medição de composição corporal",
                    testTag = "body_component",
                    onStart = onStartBodyComponent,
                    onStop = onStopBodyComponent,
                )
            }
            if (emotion.supported) {
                DetectActionBlock(
                    title = "Emoção",
                    state = emotion,
                    connected = actionsEnabled,
                    startLabel = "Medir emoção",
                    stopLabel = "Parar medição de emoção",
                    testTag = "emotion",
                    onStart = onStartEmotion,
                    onStop = onStopEmotion,
                )
            }
            if (fatigue.supported) {
                DetectActionBlock(
                    title = "Fadiga",
                    state = fatigue,
                    connected = actionsEnabled,
                    startLabel = "Medir fadiga",
                    stopLabel = "Parar medição de fadiga",
                    testTag = "fatigue",
                    onStart = onStartFatigue,
                    onStop = onStopFatigue,
                )
            }
            if (breath.supported) {
                DetectActionBlock(
                    title = "Respiração",
                    state = breath,
                    connected = actionsEnabled,
                    startLabel = "Medir respiração",
                    stopLabel = "Parar medição de respiração",
                    testTag = "breath",
                    onStart = onStartBreath,
                    onStop = onStopBreath,
                )
            }
        }
    }
}

@Composable
internal fun DetectActionBlock(
    title: String,
    state: DetectSessionUiState,
    connected: Boolean,
    startLabel: String,
    stopLabel: String,
    extraLabel: String? = null,
    testTag: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onExtra: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("${testTag}_block")) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF191C1E)
        )
        Text(
            text = when {
                state.running && !connected -> "A conexão não está disponível para controlar esta medição. Conecte o relógio novamente."
                state.lastError != null -> "O relógio informou uma falha nesta medição. Confira a conexão antes de tentar novamente."
                state.running -> "Medição em andamento. Você pode parar pelo botão abaixo."
                !connected -> "Conecte o relógio para iniciar a medição."
                else -> "Use o botão abaixo para iniciar a medição."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.lastError != null) Color(0xFFBA1A1A) else Color(0xFF44474E),
            modifier = Modifier.testTag("${testTag}_status")
        )
        if (state.running && state.progress in 1..99) {
            LinearProgressIndicator(
                progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF00639B),
                trackColor = Color(0xFFD1E4FF),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.running) Button(
                onClick = onStart,
                enabled = connected && !state.running,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("${testTag}_start")
            ) {
                Text(startLabel, style = MaterialTheme.typography.labelLarge)
            }
            if (state.running) OutlinedButton(
                onClick = onStop,
                enabled = connected && state.running,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("${testTag}_stop")
            ) {
                Text(stopLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (extraLabel != null && onExtra != null && !state.running) {
            OutlinedButton(
                onClick = onExtra,
                enabled = connected && !state.running,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("${testTag}_read")
            ) {
                Text(extraLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (state.lastError == null && state.lastSummary.isNotBlank()) {
            Text("Última informação recebida: ${state.lastSummary}", style = MaterialTheme.typography.bodyLarge)
        }
        if (state.lastError != null && com.example.BuildConfig.DEBUG) PatientSection("Detalhes para suporte: $title") {
            Text(state.lastError, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RealAdcSparkline(samples: List<Int>) {
    if (samples.size < 2) {
        Text(
            text = "Ainda não há traçado do ECG disponível.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF44474E),
            modifier = Modifier.testTag("ecg_waveform_empty")
        )
        return
    }
    val normalized = remember(samples) { normalizedEcgWaveform(samples) }
    Text("Traçado recebido do relógio. A escala foi ajustada para exibição.", style = MaterialTheme.typography.bodyMedium)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("ecg_waveform_real")
            .semantics { contentDescription = "Traçado de ECG com ${samples.size} pontos recebidos." }
    ) {
        val path = Path()
        normalized.forEachIndexed { index, value ->
            val x = size.width * index / (samples.lastIndex).coerceAtLeast(1)
            val y = size.height - value * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color(0xFF00639B),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawLine(
            color = Color(0xFFD1E4FF),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/** Only display coordinates change; no samples, timing or clinical values are invented. */
internal fun normalizedEcgWaveform(samples: List<Int>): List<Float> {
    if (samples.isEmpty()) return emptyList()
    val min = samples.min().toDouble()
    val range = samples.max().toDouble() - min
    return if (range == 0.0) List(samples.size) { 0.5f }
    else samples.map { ((it.toDouble() - min) / range).toFloat() }
}
