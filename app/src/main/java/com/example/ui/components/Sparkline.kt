package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.local.HBandSensorMetricEntity

@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String = "Tendência dos últimos 7 dias",
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag("vital_sparkline")
            .semantics { this.contentDescription = contentDescription },
    ) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        if (values.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.dp.toPx(),
            )
            return@Canvas
        }
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.lastIndex).coerceAtLeast(1)
            val y = size.height - ((value - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = stroke)
    }
}

internal fun sparklineValues(
    metrics: List<HBandSensorMetricEntity>?,
    nowMillis: Long,
    days: Int = 7,
    valueOf: (HBandSensorMetricEntity) -> Float?,
): List<Float> {
    if (metrics.isNullOrEmpty()) return emptyList()
    val start = nowMillis - days * 24L * 60 * 60 * 1000
    return metrics.asSequence()
        .filter { it.timestampMillis > start && it.timestampMillis <= nowMillis }
        .sortedBy { it.timestampMillis }
        .mapNotNull(valueOf)
        .toList()
}
