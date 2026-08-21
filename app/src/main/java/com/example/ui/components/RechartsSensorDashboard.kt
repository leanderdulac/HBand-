package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MinimalBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChartMetricType(val label: String) {
    SEVEN_DAY_SUMMARY("Tendências (7 dias)"),
    HEART_RATE("Freq. Cardíaca"),
    BLOOD_PRESSURE("Pressão Arterial"),
    SPO2("Oxigênio SpO2"),
    TEMPERATURE("Temp. Corporal"),
    ACTIVITY("Passos e Calorias")
}

@Composable
fun RechartsSensorDashboard(
    sensorMetrics: List<HBandSensorMetricEntity>,
    onSimulateBatch: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true
) {
    var selectedMetric by remember { mutableStateOf(ChartMetricType.SEVEN_DAY_SUMMARY) }

    val columnModifier = if (isScrollable) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("recharts_sensor_dashboard")
    } else {
        modifier
            .fillMaxWidth()
            .testTag("recharts_sensor_dashboard")
    }

    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Room DB Stream Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                                .clip(CircleShape)
                                .background(Color(0xFFD1E4FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color(0xFF004A77),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Análise do Banco de Dados Room",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                            Text(
                                text = "Visualização em tempo real das métricas salvas no SQLite",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF44474E)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${sensorMetrics.size} Registros",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metric Selector Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recharts_metric_selector")
                ) {
                    itemsIndexed(ChartMetricType.values()) { _, type ->
                        FilterChip(
                            selected = selectedMetric == type,
                            onClick = { selectedMetric = type },
                            label = { Text(type.label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00639B),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF1F4F9),
                                labelColor = Color(0xFF44474E)
                            )
                        )
                    }
                }
            }
        }

        // Empty state handler if 0 metrics in Room DB
        if (sensorMetrics.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MinimalBorder),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = Color(0xFF00639B),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nenhuma Métrica Registrada Ainda",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gere métricas de teste do sensor HBand para popular o banco de dados Room SQLite local e visualizar os gráficos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF44474E)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { onSimulateBatch(5) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B))
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adicionar 5 Métricas de Teste")
                    }
                }
            }
        } else {
            // Summary Stats Bar for Active Metric
            MetricStatsBar(metrics = sensorMetrics, activeMetric = selectedMetric)

            // Main Dynamic Chart Card
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            Icon(
                                imageVector = when (selectedMetric) {
                                    ChartMetricType.SEVEN_DAY_SUMMARY -> Icons.Default.Analytics
                                    ChartMetricType.HEART_RATE -> Icons.Default.Favorite
                                    ChartMetricType.BLOOD_PRESSURE -> Icons.Default.Speed
                                    ChartMetricType.SPO2 -> Icons.Default.WaterDrop
                                    ChartMetricType.TEMPERATURE -> Icons.Default.Thermostat
                                    ChartMetricType.ACTIVITY -> Icons.AutoMirrored.Filled.DirectionsRun
                                },
                                contentDescription = null,
                                tint = Color(0xFF00639B)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gráfico - ${selectedMetric.label}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                        }

                        OutlinedButton(
                            onClick = { onSimulateBatch(1) },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MinimalBorder)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00639B))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+1 Registro", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00639B))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Render chart according to selected metric
                    when (selectedMetric) {
                        ChartMetricType.SEVEN_DAY_SUMMARY -> RechartsSevenDaySummaryCard(
                            metrics = sensorMetrics
                        )

                        ChartMetricType.HEART_RATE -> RechartsAreaChart(
                            metrics = sensorMetrics,
                            valueExtractor = { it.heartRate.toFloat() },
                            unit = "BPM",
                            lineColor = Color(0xFFE53935),
                            gradientStart = Color(0xFFFFEBEE),
                            minScale = 50f,
                            maxScale = 140f
                        )

                        ChartMetricType.BLOOD_PRESSURE -> RechartsDualLineChart(
                            metrics = sensorMetrics,
                            sysExtractor = { it.systolicBp.toFloat() },
                            diaExtractor = { it.diastolicBp.toFloat() }
                        )

                        ChartMetricType.SPO2 -> RechartsAreaChart(
                            metrics = sensorMetrics,
                            valueExtractor = { it.spO2.toFloat() },
                            unit = "%",
                            lineColor = Color(0xFF00897B),
                            gradientStart = Color(0xFFE0F2F1),
                            minScale = 90f,
                            maxScale = 100f
                        )

                        ChartMetricType.TEMPERATURE -> RechartsAreaChart(
                            metrics = sensorMetrics,
                            valueExtractor = { it.temperatureCelsius },
                            unit = "°C",
                            lineColor = Color(0xFFFB8C00),
                            gradientStart = Color(0xFFFFF3E0),
                            minScale = 35f,
                            maxScale = 40f
                        )

                        ChartMetricType.ACTIVITY -> RechartsBarChart(
                            metrics = sensorMetrics
                        )
                    }
                }
            }

            // Sleep & HRV Donut / Radial Gauge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MinimalBorder),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF00639B))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Distribuição das Fases do Sono",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    RechartsRadialSleepGauge(latestMetric = sensorMetrics.firstOrNull())
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MetricStatsBar(
    metrics: List<HBandSensorMetricEntity>,
    activeMetric: ChartMetricType
) {
    val (avgVal, minVal, maxVal, unit) = remember(metrics, activeMetric) {
        if (metrics.isEmpty()) return@remember listOf("0", "0", "0", "")
        when (activeMetric) {
            ChartMetricType.SEVEN_DAY_SUMMARY -> {
                val hrVals = metrics.map { it.heartRate }
                val stepVals = metrics.map { it.steps }
                listOf(
                    if (hrVals.isNotEmpty()) String.format("%.0f", hrVals.average()) else "72",
                    if (stepVals.isNotEmpty()) "${stepVals.minOrNull() ?: 0}" else "5000",
                    if (stepVals.isNotEmpty()) "${stepVals.maxOrNull() ?: 0}" else "10000",
                    "7d Avg HR / Steps"
                )
            }
            ChartMetricType.HEART_RATE -> {
                val vals = metrics.map { it.heartRate }
                listOf(
                    String.format("%.0f", vals.average()),
                    "${vals.minOrNull() ?: 0}",
                    "${vals.maxOrNull() ?: 0}",
                    "BPM"
                )
            }
            ChartMetricType.BLOOD_PRESSURE -> {
                val sysVals = metrics.map { it.systolicBp }
                listOf(
                    String.format("%.0f", sysVals.average()),
                    "${sysVals.minOrNull() ?: 0}",
                    "${sysVals.maxOrNull() ?: 0}",
                    "mmHg"
                )
            }
            ChartMetricType.SPO2 -> {
                val vals = metrics.map { it.spO2 }
                listOf(
                    String.format("%.1f", vals.average()),
                    "${vals.minOrNull() ?: 0}",
                    "${vals.maxOrNull() ?: 0}",
                    "%"
                )
            }
            ChartMetricType.TEMPERATURE -> {
                val vals = metrics.map { it.temperatureCelsius }
                listOf(
                    String.format("%.1f", vals.average()),
                    String.format("%.1f", vals.minOrNull() ?: 0f),
                    String.format("%.1f", vals.maxOrNull() ?: 0f),
                    "°C"
                )
            }
            ChartMetricType.ACTIVITY -> {
                val vals = metrics.map { it.steps }
                listOf(
                    String.format("%.0f", vals.average()),
                    "${vals.minOrNull() ?: 0}",
                    "${vals.maxOrNull() ?: 0}",
                    "steps"
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatPillCard("AVG", "$avgVal $unit", Color(0xFF00639B), Modifier.weight(1f))
        StatPillCard("MIN", "$minVal $unit", Color(0xFF2E7D32), Modifier.weight(1f))
        StatPillCard("MAX", "$maxVal $unit", Color(0xFFC62828), Modifier.weight(1f))
    }
}

@Composable
private fun StatPillCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF44474E))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accentColor)
        }
    }
}

/**
 * Recharts-style Area Chart Component built with Jetpack Compose Canvas
 */
@Composable
private fun RechartsAreaChart(
    metrics: List<HBandSensorMetricEntity>,
    valueExtractor: (HBandSensorMetricEntity) -> Float,
    unit: String,
    lineColor: Color,
    gradientStart: Color,
    minScale: Float,
    maxScale: Float
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val displayList = remember(metrics) { metrics.take(15).reversed() }

    Column(modifier = Modifier.testTag("recharts_area_chart")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFE))
                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(16.dp))
                .pointerInput(displayList) {
                    detectTapGestures { offset ->
                        if (displayList.isNotEmpty()) {
                            val stepX = size.width / (displayList.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / stepX)
                                .toInt()
                                .coerceIn(0, displayList.size - 1)
                            selectedIndex = idx
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (displayList.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val padding = 32f

                val drawWidth = width - (padding * 2)
                val drawHeight = height - (padding * 2)

                val values = displayList.map { valueExtractor(it) }
                val minV = values.minOrNull()?.coerceAtMost(minScale) ?: minScale
                val maxV = values.maxOrNull()?.coerceAtLeast(maxScale) ?: maxScale
                val rangeV = (maxV - minV).coerceAtLeast(1f)

                // 1. Cartesian Grid Lines (Recharts <CartesianGrid strokeDasharray="3 3" />)
                val gridLines = 4
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                for (i in 0..gridLines) {
                    val y = padding + (drawHeight / gridLines) * i
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(padding, y),
                        end = Offset(width - padding, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                // 2. Compute Points
                val points = displayList.mapIndexed { idx, item ->
                    val x = padding + (drawWidth / (displayList.size - 1).coerceAtLeast(1)) * idx
                    val v = valueExtractor(item)
                    val y = padding + drawHeight - ((v - minV) / rangeV) * drawHeight
                    Offset(x, y)
                }

                // 3. Draw Area Gradient (Recharts <area fill="url(#colorGrad)" />)
                if (points.size >= 2) {
                    val areaPath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val cx = (p1.x + p2.x) / 2f
                            cubicTo(cx, p1.y, cx, p2.y, p2.x, p2.y)
                        }
                        lineTo(points.last().x, height - padding)
                        lineTo(points.first().x, height - padding)
                        close()
                    }

                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(gradientStart.copy(alpha = 0.6f), Color.Transparent),
                            startY = padding,
                            endY = height - padding
                        )
                    )

                    // 4. Draw Line Path (Recharts <Area type="monotone" />)
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val cx = (p1.x + p2.x) / 2f
                            cubicTo(cx, p1.y, cx, p2.y, p2.x, p2.y)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 5. Draw Data Points
                points.forEachIndexed { idx, pt ->
                    val isSelected = idx == selectedIndex
                    val radius = if (isSelected) 7.dp.toPx() else 4.dp.toPx()

                    if (isSelected) {
                        drawCircle(
                            color = lineColor.copy(alpha = 0.3f),
                            radius = 12.dp.toPx(),
                            center = pt
                        )
                    }

                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = pt
                    )
                    drawCircle(
                        color = lineColor,
                        radius = radius - 2.dp.toPx(),
                        center = pt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Recharts Tooltip Overlay on Tap
            if (selectedIndex in displayList.indices) {
                val item = displayList[selectedIndex]
                val value = valueExtractor(item)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${item.timestamp.takeLast(8)} • ${value.toInt()} $unit",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // X-Axis Time Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val first = displayList.firstOrNull()?.timestamp?.takeLast(8) ?: ""
            val mid = displayList.getOrNull(displayList.size / 2)?.timestamp?.takeLast(8) ?: ""
            val last = displayList.lastOrNull()?.timestamp?.takeLast(8) ?: ""

            Text(first, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
            Text(mid, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
            Text(last, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
        }
    }
}

/**
 * Recharts Dual Line Chart for Blood Pressure (SYS vs DIA)
 */
@Composable
private fun RechartsDualLineChart(
    metrics: List<HBandSensorMetricEntity>,
    sysExtractor: (HBandSensorMetricEntity) -> Float,
    diaExtractor: (HBandSensorMetricEntity) -> Float
) {
    val displayList = remember(metrics) { metrics.take(12).reversed() }

    Column {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF00639B)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Sistólica", style = MaterialTheme.typography.labelSmall, color = Color(0xFF191C1E))
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF00897B)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Diastólica", style = MaterialTheme.typography.labelSmall, color = Color(0xFF191C1E))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFE))
                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(16.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (displayList.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val padding = 32f
                val drawWidth = width - (padding * 2)
                val drawHeight = height - (padding * 2)

                val minV = 50f
                val maxV = 160f
                val rangeV = maxV - minV

                // Grid
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                for (i in 0..3) {
                    val y = padding + (drawHeight / 3) * i
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(padding, y),
                        end = Offset(width - padding, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                // Points
                val sysPoints = displayList.mapIndexed { idx, item ->
                    val x = padding + (drawWidth / (displayList.size - 1).coerceAtLeast(1)) * idx
                    val v = sysExtractor(item)
                    val y = padding + drawHeight - ((v - minV) / rangeV) * drawHeight
                    Offset(x, y)
                }

                val diaPoints = displayList.mapIndexed { idx, item ->
                    val x = padding + (drawWidth / (displayList.size - 1).coerceAtLeast(1)) * idx
                    val v = diaExtractor(item)
                    val y = padding + drawHeight - ((v - minV) / rangeV) * drawHeight
                    Offset(x, y)
                }

                // Draw Sys Line
                val sysPath = Path().apply {
                    moveTo(sysPoints.first().x, sysPoints.first().y)
                    for (i in 0 until sysPoints.size - 1) {
                        lineTo(sysPoints[i + 1].x, sysPoints[i + 1].y)
                    }
                }
                drawPath(sysPath, color = Color(0xFF00639B), style = Stroke(width = 3.dp.toPx()))

                // Draw Dia Line
                val diaPath = Path().apply {
                    moveTo(diaPoints.first().x, diaPoints.first().y)
                    for (i in 0 until diaPoints.size - 1) {
                        lineTo(diaPoints[i + 1].x, diaPoints[i + 1].y)
                    }
                }
                drawPath(diaPath, color = Color(0xFF00897B), style = Stroke(width = 3.dp.toPx()))

                // Circles
                sysPoints.forEach { drawCircle(Color(0xFF00639B), radius = 4.dp.toPx(), center = it) }
                diaPoints.forEach { drawCircle(Color(0xFF00897B), radius = 4.dp.toPx(), center = it) }
            }
        }
    }
}

/**
 * Recharts Bar Chart Component for Steps & Calories
 */
@Composable
private fun RechartsBarChart(
    metrics: List<HBandSensorMetricEntity>
) {
    val displayList = remember(metrics) { metrics.take(10).reversed() }

    Column(modifier = Modifier.testTag("recharts_bar_chart")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFE))
                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(16.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (displayList.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val padding = 32f
                val drawWidth = width - (padding * 2)
                val drawHeight = height - (padding * 2)

                val maxSteps = displayList.maxOfOrNull { it.steps }?.coerceAtLeast(5000) ?: 10000

                val barGroupWidth = drawWidth / displayList.size
                val barWidth = barGroupWidth * 0.5f

                displayList.forEachIndexed { idx, item ->
                    val groupLeft = padding + idx * barGroupWidth
                    val barX = groupLeft + (barGroupWidth - barWidth) / 2f

                    val barHeight = (item.steps.toFloat() / maxSteps) * drawHeight
                    val barY = padding + drawHeight - barHeight

                    // Draw Bar
                    drawRoundRect(
                        color = Color(0xFF00639B),
                        topLeft = Offset(barX, barY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Recharts Radial / Donut Sleep Distribution Chart
 */
@Composable
private fun RechartsRadialSleepGauge(
    latestMetric: HBandSensorMetricEntity?
) {
    val deep = latestMetric?.deepSleepMinutes ?: 180
    val light = latestMetric?.lightSleepMinutes ?: 240
    val awake = latestMetric?.awakeMinutes ?: 30
    val total = (deep + light + awake).coerceAtLeast(1)

    val deepAngle = (deep.toFloat() / total) * 360f
    val lightAngle = (light.toFloat() / total) * 360f
    val awakeAngle = (awake.toFloat() / total) * 360f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 18.dp.toPx()
                var startAngle = -90f

                // Deep Sleep Arc
                drawArc(
                    color = Color(0xFF1A237E),
                    startAngle = startAngle,
                    sweepAngle = deepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += deepAngle

                // Light Sleep Arc
                drawArc(
                    color = Color(0xFF3949AB),
                    startAngle = startAngle,
                    sweepAngle = lightAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += lightAngle

                // Awake Arc
                drawArc(
                    color = Color(0xFFFFB74D),
                    startAngle = startAngle,
                    sweepAngle = awakeAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${total / 60}h ${total % 60}m", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF191C1E))
                Text("Sono Total", style = MaterialTheme.typography.labelSmall, color = Color(0xFF44474E))
            }
        }

        // Legend Breakdown
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepLegendItem("Sono Profundo", "${deep / 60}h ${deep % 60}m", Color(0xFF1A237E))
            SleepLegendItem("Sono Leve", "${light / 60}h ${light % 60}m", Color(0xFF3949AB))
            SleepLegendItem("Acordado", "${awake}m", Color(0xFFFFB74D))
        }
    }
}

@Composable
private fun SleepLegendItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF44474E))
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF191C1E))
    }
}
