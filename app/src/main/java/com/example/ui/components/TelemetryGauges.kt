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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HBandTelemetry
import com.example.ui.theme.MinimalBorder

@Composable
fun TelemetryGauges(
    telemetry: HBandTelemetry?,
    modifier: Modifier = Modifier
) {
    if (telemetry == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aguardando fluxo de telemetria do smartwatch BLE...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF44474E)
                )
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card: Heart Rate in Clean Minimalism style (#D1E4FF container)
        HeartRateHeroCard(
            heartRate = telemetry.heartRate,
            deviceModel = telemetry.deviceModel,
            hrvScore = telemetry.hrvScore,
            modifier = Modifier.testTag("gauge_heart_rate")
        )

        // Row 2: SpO2 Level & HRV Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val hasSpo2 = telemetry.spO2 > 0
            MetricCard(
                title = "Oxigenação (SpO2)",
                value = if (hasSpo2) "${telemetry.spO2}%" else "--",
                subtitle = if (hasSpo2) {
                    if (telemetry.spO2 >= 95) "Oxigenação Normal" else "Oxigenação Baixa"
                } else "Sensor Não Medido",
                icon = Icons.Default.WaterDrop,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gauge_spo2")
            )

            val hasHrv = telemetry.hrvScore > 0
            MetricCard(
                title = "Pontuação VFC (HRV)",
                value = if (hasHrv) "${telemetry.hrvScore}/100" else "--",
                subtitle = if (hasHrv) "Variabilidade Cardíaca Real" else "Calculando RR...",
                icon = Icons.Default.Bedtime,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gauge_hrv")
            )
        }

        // Row 3: Blood Pressure & Body Temp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val hasBp = telemetry.bloodPressure.systolic > 0 && telemetry.bloodPressure.diastolic > 0
            MetricCard(
                title = "Pressão Arterial",
                value = if (hasBp) "${telemetry.bloodPressure.systolic}/${telemetry.bloodPressure.diastolic}" else "-- / --",
                subtitle = if (hasBp) "mmHg (Medido)" else "Não suportado no modelo",
                icon = Icons.Default.Speed,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gauge_blood_pressure")
            )

            val hasTemp = telemetry.temperatureCelsius > 0f
            MetricCard(
                title = "Temp. Corporal",
                value = if (hasTemp) "%.1f°C".format(telemetry.temperatureCelsius) else "--",
                subtitle = if (hasTemp) "Sensor Térmico" else "Não suportado no modelo",
                icon = Icons.Default.Thermostat,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gauge_temp")
            )
        }

        // Row 4: Daily Activity
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gauge_activity"),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0F4FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                tint = Color(0xFF00639B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Atividade e Passos (BLE Real)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF44474E)
                            )
                            Text(
                                text = "${telemetry.steps} passos",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF191C1E)
                                )
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.1f kcal".format(telemetry.calories),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00639B)
                        )
                        Text(
                            text = "%.1f km".format(telemetry.distanceMeters / 1000f),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF44474E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val progress = (telemetry.steps / 10000f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = Color(0xFF004A77),
                    trackColor = Color(0xFF004A77).copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
private fun HeartRateHeroCard(
    heartRate: Int,
    deviceModel: String = "",
    hrvScore: Int = 0,
    modifier: Modifier = Modifier
) {
    val isMeasuring = heartRate > 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E4FF))
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Frequência Cardíaca (Sensor PPG Real)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF001D36)
                    )
                    Text(
                        text = if (isMeasuring) "Leitura de hardware ao vivo · $deviceModel" else "Aguardando leitura do sensor no pulso...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF004A77)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (isMeasuring) Color(0xFFB3261E) else Color(0xFF004A77),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (isMeasuring) "$heartRate" else "--",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF001D36),
                        letterSpacing = (-2).sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF004A77)
                    )
                )
            }

            if (!isMeasuring) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Mantenha o sensor no pulso. Para Gear S3, abra o app de Batimentos no relógio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF004A77)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ECG waveform line in clean navy accent
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                val path = Path()
                val w = size.width
                val h = size.height
                val midY = h / 2f

                if (isMeasuring) {
                    path.moveTo(0f, midY)
                    path.lineTo(w * 0.25f, midY)
                    path.lineTo(w * 0.35f, midY - h * 0.35f)
                    path.lineTo(w * 0.45f, midY + h * 0.45f)
                    path.lineTo(w * 0.55f, midY - h * 0.85f)
                    path.lineTo(w * 0.65f, midY + h * 0.35f)
                    path.lineTo(w * 0.75f, midY)
                    path.lineTo(w, midY)
                } else {
                    path.moveTo(0f, midY)
                    path.lineTo(w, midY)
                }

                drawPath(
                    path = path,
                    color = Color(0xFF004A77),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F4FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF00639B),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF44474E)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191C1E)
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF004A77)
                )
            }
        }
    }
}
