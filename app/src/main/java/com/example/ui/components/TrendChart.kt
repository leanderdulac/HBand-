package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.ExpectedRangePlaceholder
import com.example.ui.theme.PillShape

data class TrendPoint(val timestampMillis: Long, val value: Float)

enum class ChartPeriod(val label: String, val days: Int) {
    D7("7 dias", 7),
    D30("30 dias", 30),
    M3("3 meses", 90),
}

internal fun trendPointsInPeriod(
    metrics: List<HBandSensorMetricEntity>?,
    nowMillis: Long,
    period: ChartPeriod,
    valueOf: (HBandSensorMetricEntity) -> Float?,
): List<TrendPoint> {
    if (metrics.isNullOrEmpty()) return emptyList()
    val start = nowMillis - period.days * 24L * 60 * 60 * 1000
    return metrics.asSequence()
        .filter { it.timestampMillis > start && it.timestampMillis <= nowMillis }
        .mapNotNull { record -> valueOf(record)?.let { TrendPoint(record.timestampMillis, it) } }
        .sortedBy { it.timestampMillis }
        .toList()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartPeriodRow(
    selected: ChartPeriod,
    onSelect: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().testTag("chart_period_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(period.label, style = MaterialTheme.typography.labelLarge) },
                shape = PillShape,
                modifier = Modifier.heightIn(min = 48.dp).testTag("chart_period_${period.name.lowercase()}"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
fun TrendChart(
    points: List<TrendPoint>,
    range: ExpectedRangePlaceholder,
    color: Color,
    modifier: Modifier = Modifier,
    secondaryPoints: List<TrendPoint> = emptyList(),
    secondaryColor: Color = color.copy(alpha = 0.55f),
    chartDescription: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Cada ponto é um registro real deste aparelho. Veja os valores e horários na lista abaixo.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            range.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("expected_range_caption"),
        )
        if (points.isEmpty()) {
            Text("Sem medições neste período.", style = MaterialTheme.typography.titleMedium)
            return
        }
        val axis = MaterialTheme.colorScheme.outline
        val band = color.copy(alpha = 0.12f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("trend_chart")
                .semantics {
                    contentDescription = chartDescription
                        ?: "Gráfico de ${range.label}. Valores e horários disponíveis na lista abaixo."
                },
        ) {
            val inset = 12.dp.toPx()
            val width = (size.width - 2 * inset).coerceAtLeast(1f)
            val height = (size.height - 2 * inset).coerceAtLeast(1f)
            val values = points.map { it.value } + secondaryPoints.map { it.value } +
                listOfNotNull(range.min, range.max)
            val low = values.min()
            val high = values.max()
            val span = (high - low).takeIf { it > 0f } ?: 1f
            fun xOf(timestamp: Long): Float {
                val start = points.minOf { it.timestampMillis }
                val end = points.maxOf { it.timestampMillis }
                val fraction = if (end == start) 0.5f else
                    ((timestamp - start).toDouble() / (end - start)).toFloat()
                return inset + width * fraction
            }
            fun yOf(value: Float): Float = inset + height * (1f - (value - low) / span)

            if (range.isConfigured) {
                val top = range.max ?: high
                val bottom = range.min ?: low
                val yTop = yOf(top)
                val yBottom = yOf(bottom)
                drawRect(
                    color = band,
                    topLeft = Offset(inset, minOf(yTop, yBottom)),
                    size = Size(width, kotlin.math.abs(yBottom - yTop).coerceAtLeast(1f)),
                )
            }

            drawLine(axis, Offset(inset, inset + height), Offset(inset + width, inset + height))
            drawSeries(points, color, ::xOf, ::yOf)
            if (secondaryPoints.isNotEmpty()) drawSeries(secondaryPoints, secondaryColor, ::xOf, ::yOf)
        }
    }
}

private fun DrawScope.drawSeries(
    points: List<TrendPoint>,
    color: Color,
    xOf: (Long) -> Float,
    yOf: (Float) -> Float,
) {
    if (points.isEmpty()) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = xOf(point.timestampMillis)
        val y = yOf(point.value)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    points.forEach { point ->
        drawCircle(color, 4.dp.toPx(), Offset(xOf(point.timestampMillis), yOf(point.value)))
    }
}
