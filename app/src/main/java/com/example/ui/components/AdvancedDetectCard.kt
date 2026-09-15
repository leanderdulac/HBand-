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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.hband.DetectSessionUiState
import com.example.data.hband.DeviceCapabilities
import com.example.ui.theme.MinimalBorder

@Composable
fun AdvancedDetectCard(
    capabilities: DeviceCapabilities,
    hardwareConnected: Boolean,
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
                        text = "Medições avançadas (P1)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = "Só aparecem APIs que o firmware reportou após o handshake",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00639B)
                    )
                }
            }

            if (ecg.supported) {
                DetectActionBlock(
                    title = "ECG",
                    state = ecg,
                    connected = hardwareConnected,
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
                    connected = hardwareConnected,
                    startLabel = "Medir glicose",
                    stopLabel = "Parar glicose",
                    testTag = "glucose",
                    onStart = onStartGlucose,
                    onStop = onStopGlucose,
                )
            }
            if (bloodComponent.supported) {
                DetectActionBlock(
                    title = "Componentes sanguíneos",
                    state = bloodComponent,
                    connected = hardwareConnected,
                    startLabel = "Medir sangue",
                    stopLabel = "Parar",
                    testTag = "blood_component",
                    onStart = onStartBloodComponent,
                    onStop = onStopBloodComponent,
                )
            }
            if (bodyComponent.supported) {
                DetectActionBlock(
                    title = "Composição corporal",
                    state = bodyComponent,
                    connected = hardwareConnected,
                    startLabel = "Medir corpo",
                    stopLabel = "Parar",
                    testTag = "body_component",
                    onStart = onStartBodyComponent,
                    onStop = onStopBodyComponent,
                )
            }
            if (emotion.supported) {
                DetectActionBlock(
                    title = "Emoção",
                    state = emotion,
                    connected = hardwareConnected,
                    startLabel = "Medir emoção",
                    stopLabel = "Parar",
                    testTag = "emotion",
                    onStart = onStartEmotion,
                    onStop = onStopEmotion,
                )
            }
            if (fatigue.supported) {
                DetectActionBlock(
                    title = "Fadiga",
                    state = fatigue,
                    connected = hardwareConnected,
                    startLabel = "Medir fadiga",
                    stopLabel = "Parar",
                    testTag = "fatigue",
                    onStart = onStartFatigue,
                    onStop = onStopFatigue,
                )
            }
            if (breath.supported) {
                DetectActionBlock(
                    title = "Respiração",
                    state = breath,
                    connected = hardwareConnected,
                    startLabel = "Medir respiração",
                    stopLabel = "Parar",
                    testTag = "breath",
                    onStart = onStartBreath,
                    onStop = onStopBreath,
                )
            }
        }
    }
}

@Composable
private fun DetectActionBlock(
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF191C1E)
        )
        Text(
            text = state.lastError ?: state.lastSummary.ifBlank { "Aguardando medição real do firmware" },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.lastError != null) Color(0xFFBA1A1A) else Color(0xFF64748B),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onStart,
                enabled = connected && !state.running,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                modifier = Modifier.testTag("${testTag}_start")
            ) {
                Text(startLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            OutlinedButton(
                onClick = onStop,
                enabled = connected && state.running,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("${testTag}_stop")
            ) {
                Text(stopLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (extraLabel != null && onExtra != null) {
            OutlinedButton(
                onClick = onExtra,
                enabled = connected && !state.running,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("${testTag}_read")
            ) {
                Text(extraLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RealAdcSparkline(samples: List<Int>) {
    if (samples.size < 2) {
        Text(
            text = "Sem forma de onda real ainda — nenhum ADC inventado.",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            modifier = Modifier.testTag("ecg_waveform_empty")
        )
        return
    }
    val min = samples.min()
    val max = samples.max().coerceAtLeast(min + 1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("ecg_waveform_real")
    ) {
        val path = Path()
        samples.forEachIndexed { index, value ->
            val x = size.width * index / (samples.lastIndex).coerceAtLeast(1)
            val y = size.height - ((value - min).toFloat() / (max - min) * size.height)
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
