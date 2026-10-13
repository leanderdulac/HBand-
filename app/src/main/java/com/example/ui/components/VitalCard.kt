package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.hband.DeviceCapabilities
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.model.HBandTelemetry
import com.example.ui.theme.ExpectedRangePlaceholder
import com.example.ui.theme.ExpectedRangePlaceholders
import com.example.ui.theme.LocalVitalSignalColors
import java.util.Locale

enum class VitalKind {
    HEART,
    SPO2,
    PRESSURE,
    TEMPERATURE,
}

internal fun visiblePatientVitals(capabilities: DeviceCapabilities): List<VitalKind> {
    if (!capabilities.probed) return emptyList()
    return buildList {
        if (capabilities.isSupportHeart) add(VitalKind.HEART)
        if (capabilities.isSupportSpo2) add(VitalKind.SPO2)
        if (capabilities.isSupportBp) add(VitalKind.PRESSURE)
        if (capabilities.isSupportTemperature) add(VitalKind.TEMPERATURE)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VitalCardGrid(
    telemetry: HBandTelemetry?,
    capabilities: DeviceCapabilities,
    history: List<HBandSensorMetricEntity>?,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val kinds = visiblePatientVitals(capabilities)
    if (kinds.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth().testTag("vital_card_grid")) {
        val cardWidth = (maxWidth - 12.dp) / 2
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            kinds.forEach { kind ->
                VitalCard(
                    kind = kind,
                    telemetry = telemetry,
                    history = history,
                    nowMillis = nowMillis,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
fun VitalCard(
    kind: VitalKind,
    telemetry: HBandTelemetry?,
    history: List<HBandSensorMetricEntity>?,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val signals = LocalVitalSignalColors.current
    val locale = Locale.forLanguageTag("pt-BR")
    val presentation = when (kind) {
        VitalKind.HEART -> VitalPresentation(
            title = "Batimentos",
            icon = Icons.Default.Favorite,
            color = signals.heartRate,
            range = ExpectedRangePlaceholders.heartRate,
            valueTag = "gauge_heart_rate",
            numeric = telemetry?.heartRate?.takeIf { it > 0 }?.toFloat(),
            display = telemetry?.heartRate?.takeIf { it > 0 }?.let { "$it bpm" } ?: "Sem dado",
            historyValue = { it.heartRate.takeIf { hr -> hr > 0 }?.toFloat() },
        )
        VitalKind.SPO2 -> VitalPresentation(
            title = "Oxigênio",
            icon = Icons.Default.Air,
            color = signals.spo2,
            range = ExpectedRangePlaceholders.spo2,
            valueTag = "gauge_spo2",
            numeric = telemetry?.spO2?.takeIf { it > 0 }?.toFloat(),
            display = telemetry?.spO2?.takeIf { it > 0 }?.let { "$it%" } ?: "Sem dado",
            historyValue = { it.spO2.takeIf { o2 -> o2 > 0 }?.toFloat() },
        )
        VitalKind.PRESSURE -> {
            val sys = telemetry?.bloodPressure?.systolic?.takeIf { it > 0 }
            val dia = telemetry?.bloodPressure?.diastolic?.takeIf { it > 0 }
            VitalPresentation(
                title = "Pressão",
                icon = Icons.Default.MonitorHeart,
                color = signals.bloodPressure,
                range = ExpectedRangePlaceholders.systolic,
                valueTag = "gauge_blood_pressure",
                numeric = if (sys != null && dia != null) sys.toFloat() else null,
                display = if (sys != null && dia != null) "$sys/$dia" else "Sem dado",
                historyValue = {
                    val s = it.systolicBp.takeIf { v -> v > 0 }
                    val d = it.diastolicBp.takeIf { v -> v > 0 }
                    if (s != null && d != null) s.toFloat() else null
                },
            )
        }
        VitalKind.TEMPERATURE -> {
            val temp = telemetry?.temperatureCelsius?.takeIf { it.isFinite() && it > 0f }
            VitalPresentation(
                title = "Temperatura",
                icon = Icons.Default.Thermostat,
                color = signals.temperature,
                range = ExpectedRangePlaceholders.temperature,
                valueTag = "gauge_temp",
                numeric = temp,
                display = temp?.let { String.format(locale, "%.1f°C", it) } ?: "Sem dado",
                historyValue = { it.temperatureCelsius.takeIf { t -> t.isFinite() && t > 0f } },
            )
        }
    }
    val sparkline = sparklineValues(history, nowMillis, valueOf = presentation.historyValue)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vital_card_${kind.name.lowercase()}")
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(presentation.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(presentation.icon, contentDescription = null, tint = presentation.color, modifier = Modifier.size(22.dp))
                }
                Text(
                    presentation.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                presentation.display,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heightIn(min = 48.dp).testTag(presentation.valueTag),
            )
            Sparkline(
                values = sparkline,
                color = presentation.color,
                contentDescription = "Tendência de 7 dias de ${presentation.title}. ${sparkline.size} registros reais.",
            )
            StatusChip(classifyVitalStatus(presentation.numeric, presentation.range))
        }
    }
}

private data class VitalPresentation(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val range: ExpectedRangePlaceholder,
    val valueTag: String,
    val numeric: Float?,
    val display: String,
    val historyValue: (HBandSensorMetricEntity) -> Float?,
)
