package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MinimalBorder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailySummaryMetrics(
    val activeMinutes: Int,
    val totalCalories: Int,
    val activeCalories: Int,
    val bmrCalories: Int,
    val steps: Int,
    val distanceKm: Float,
    val avgHeartRate: Int,
    val avgHrv: Int,
    val totalSleepMinutes: Int,
    val roomRecordCount: Int
)

@Composable
fun DailyHealthSummaryCard(
    metrics: List<HBandSensorMetricEntity>,
    modifier: Modifier = Modifier
) {
    val dailySummary = remember(metrics) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMs = cal.timeInMillis

        // Filter Room database entries for today (or fallback to latest 24h metrics if no entries strictly today)
        var todayEntries = metrics.filter { it.timestampMillis >= startOfDayMs }
        if (todayEntries.isEmpty() && metrics.isNotEmpty()) {
            val oneDayAgo = now - 86_400_000L
            todayEntries = metrics.filter { it.timestampMillis >= oneDayAgo }
        }
        if (todayEntries.isEmpty() && metrics.isNotEmpty()) {
            todayEntries = metrics.take(20)
        }

        if (todayEntries.isNotEmpty()) {
            val count = todayEntries.size
            // Active minutes calculation: count entries with active movement (steps > 0 or HR >= 75)
            val activeEntries = todayEntries.count { it.heartRate >= 75 || it.steps > 0 }
            val computedActiveMins = (activeEntries * 3.5).toInt().coerceAtLeast(12)

            val maxSteps = todayEntries.maxOf { it.steps }
            val maxDistanceMeters = todayEntries.maxOf { it.distanceMeters }
            val maxLoggedCalories = todayEntries.maxOf { it.calories }

            val avgHr = todayEntries.map { it.heartRate }.average().toInt()
            val avgHrv = todayEntries.map { it.hrvScore }.average().toInt()

            val latestEntry = todayEntries.maxByOrNull { it.timestampMillis } ?: todayEntries.first()
            val sleepMins = latestEntry.deepSleepMinutes + latestEntry.lightSleepMinutes

            // Calorie calculation: Active burn derived from steps/movement + BMR allowance
            val activeCals = if (maxLoggedCalories > 0f) maxLoggedCalories.toInt() else (maxSteps * 0.045f).toInt()
            val bmrCals = 1450 // standard daily BMR baseline
            val totalCals = activeCals + bmrCals

            DailySummaryMetrics(
                activeMinutes = computedActiveMins,
                totalCalories = totalCals,
                activeCalories = activeCals,
                bmrCalories = bmrCals,
                steps = maxSteps,
                distanceKm = maxDistanceMeters / 1000f,
                avgHeartRate = avgHr,
                avgHrv = avgHrv,
                totalSleepMinutes = sleepMins,
                roomRecordCount = count
            )
        } else {
            DailySummaryMetrics(
                activeMinutes = 0,
                totalCalories = 1450,
                activeCalories = 0,
                bmrCalories = 1450,
                steps = 0,
                distanceKm = 0f,
                avgHeartRate = 0,
                avgHrv = 0,
                totalSleepMinutes = 0,
                roomRecordCount = 0
            )
        }
    }

    val todayDateStr = remember {
        SimpleDateFormat("EEEE, dd 'de' MMM", Locale.forLanguageTag("pt-BR")).format(Date())
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_health_summary_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Resumo Diário de Saúde",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Text(
                            text = todayDateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF44474E)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${dailySummary.roomRecordCount} Linhas Room",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Highlight Cards: Active Minutes & Calories Burned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active Minutes Card
                MetricHighlightBox(
                    title = "Minutos Ativos",
                    value = "${dailySummary.activeMinutes}",
                    unit = "min",
                    subtitle = "Meta: 30 min/dia",
                    progress = (dailySummary.activeMinutes / 30f).coerceIn(0f, 1f),
                    icon = Icons.Default.Timer,
                    color = Color(0xFF2E7D32),
                    bgColor = Color(0xFFE8F5E9),
                    testTag = "active_minutes_display",
                    modifier = Modifier.weight(1f)
                )

                // Estimated Calories Burned Card
                MetricHighlightBox(
                    title = "Calorias Queimadas",
                    value = "${dailySummary.totalCalories}",
                    unit = "kcal",
                    subtitle = "${dailySummary.activeCalories} ativos + ${dailySummary.bmrCalories} TMB",
                    progress = (dailySummary.totalCalories / 2200f).coerceIn(0f, 1f),
                    icon = Icons.Default.LocalFireDepartment,
                    color = Color(0xFFE65100),
                    bgColor = Color(0xFFFFF3E0),
                    testTag = "calories_burned_display",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary Stats Grid (Steps, Distance, Avg HR, Sleep)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryStatItem(
                    label = "Passos",
                    value = "${dailySummary.steps}",
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    color = Color(0xFF00639B),
                    testTag = "steps_progress_display",
                    modifier = Modifier.weight(1f)
                )

                SecondaryStatItem(
                    label = "Distância",
                    value = String.format(Locale.forLanguageTag("pt-BR"), "%.2f km", dailySummary.distanceKm),
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    color = Color(0xFF0288D1),
                    testTag = "distance_display",
                    modifier = Modifier.weight(1f)
                )

                SecondaryStatItem(
                    label = "FC Média",
                    value = if (dailySummary.avgHeartRate > 0) "${dailySummary.avgHeartRate} BPM" else "--",
                    icon = Icons.Default.Favorite,
                    color = Color(0xFFD32F2F),
                    testTag = "avg_hr_display",
                    modifier = Modifier.weight(1f)
                )

                SecondaryStatItem(
                    label = "Sono",
                    value = if (dailySummary.totalSleepMinutes > 0) "${dailySummary.totalSleepMinutes / 60}h ${dailySummary.totalSleepMinutes % 60}m" else "--",
                    icon = Icons.Default.Nightlight,
                    color = Color(0xFF673AB7),
                    testTag = "sleep_summary_display",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricHighlightBox(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    progress: Float,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = color
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF191C1E)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF44474E),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color(0xFF526070)
            )
        }
    }
}

@Composable
private fun SecondaryStatItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8F9FF),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF191C1E)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color(0xFF64748B)
            )
        }
    }
}
