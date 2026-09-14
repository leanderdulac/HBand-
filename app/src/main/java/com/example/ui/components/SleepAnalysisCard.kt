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
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MinimalBorder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SleepAnalysisSummary(
    val weeklyScore: Int,
    val scoreGrade: String,
    val scoreColor: Color,
    val totalSleepMinutes: Int,
    val deepSleepMins: Int,
    val remSleepMins: Int,
    val lightSleepMins: Int,
    val awakeMins: Int,
    val deepPercentage: Int,
    val remPercentage: Int,
    val lightPercentage: Int,
    val awakePercentage: Int,
    val dailySleepMinutes: List<Pair<String, Int>>
)

fun analyzeSleepMetrics(
    metrics: List<HBandSensorMetricEntity>,
    now: Long = System.currentTimeMillis(),
): SleepAnalysisSummary? {
    val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
    val recentMetrics = metrics.filter { it.timestampMillis >= sevenDaysAgo }
    val withSleep = recentMetrics.filter { (it.deepSleepMinutes + it.lightSleepMinutes) > 0 }
    if (withSleep.isEmpty()) return null

    val calendar = Calendar.getInstance()
    val dayFormat = SimpleDateFormat("EEE", Locale.forLanguageTag("pt-BR"))
    val dailyMap = linkedMapOf<String, Int>()
    for (i in 6 downTo 0) {
        calendar.timeInMillis = now - (i * 24 * 60 * 60 * 1000L)
        dailyMap[dayFormat.format(calendar.time)] = 0
    }
    for (m in withSleep) {
        calendar.timeInMillis = m.timestampMillis
        val dayName = dayFormat.format(calendar.time)
        val totalMins = m.deepSleepMinutes + m.lightSleepMinutes
        if (totalMins > (dailyMap[dayName] ?: 0)) {
            dailyMap[dayName] = totalMins
        }
    }

    val latestWithSleep = withSleep.maxByOrNull { it.timestampMillis } ?: return null
    val rawDeep = latestWithSleep.deepSleepMinutes.coerceAtLeast(0)
    val rawLight = latestWithSleep.lightSleepMinutes.coerceAtLeast(0)
    val rawAwake = latestWithSleep.awakeMinutes.coerceAtLeast(0)
    val totalMins = (rawDeep + rawLight + rawAwake).coerceAtLeast(1)
    val deepPct = ((rawDeep.toFloat() / totalMins) * 100).toInt()
    val lightPct = ((rawLight.toFloat() / totalMins) * 100).toInt()
    val awakePct = ((rawAwake.toFloat() / totalMins) * 100).toInt()

    val durationScore = ((totalMins / 480f) * 100).coerceIn(0f, 100f) * 0.6f
    val deepScore = ((deepPct / 20f) * 100).coerceIn(0f, 100f) * 0.4f
    val computedScore = (durationScore + deepScore).toInt().coerceIn(0, 100)
    val (grade, color) = when {
        computedScore >= 85 -> Pair("Ótimo", Color(0xFF2E7D32))
        computedScore >= 75 -> Pair("Bom", Color(0xFF0288D1))
        computedScore >= 65 -> Pair("Regular", Color(0xFFE65100))
        else -> Pair("Agitado", Color(0xFFD32F2F))
    }

    return SleepAnalysisSummary(
        weeklyScore = computedScore,
        scoreGrade = grade,
        scoreColor = color,
        totalSleepMinutes = totalMins,
        deepSleepMins = rawDeep,
        remSleepMins = 0,
        lightSleepMins = rawLight,
        awakeMins = rawAwake,
        deepPercentage = deepPct,
        remPercentage = 0,
        lightPercentage = lightPct,
        awakePercentage = awakePct,
        dailySleepMinutes = dailyMap.map { Pair(it.key, it.value) }
    )
}

@Composable
fun SleepAnalysisCard(
    metrics: List<HBandSensorMetricEntity>,
    modifier: Modifier = Modifier
) {
    val sleepAnalysis = remember(metrics) { analyzeSleepMetrics(metrics) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sleep_analysis_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (sleepAnalysis == null) {
                Text(
                    text = "Aguardando dados reais de sono da pulseira (readSleepDataFromDay).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF44474E),
                    modifier = Modifier.testTag("sleep_waiting_empty_state")
                )
                return@Column
            }

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
                            .background(Color(0xFF312E81)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = Color(0xFFC7D2FE),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Análise Semanal do Sono",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Text(
                            text = "Telemetria Room • Fases e Pontuação de Recuperação",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF44474E)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = sleepAnalysis.scoreColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, sleepAnalysis.scoreColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = sleepAnalysis.scoreColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${sleepAnalysis.weeklyScore}/100 • ${sleepAnalysis.scoreGrade}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = sleepAnalysis.scoreColor,
                            modifier = Modifier.testTag("weekly_sleep_score_display")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Total Duration Highlight Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1B4B)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Duração Total do Sono",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA5B4FC)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${sleepAnalysis.totalSleepMinutes / 60}h ${sleepAnalysis.totalSleepMinutes % 60}m",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "média/noite",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFC7D2FE),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Qualidade do Sono",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA5B4FC)
                        )
                        Text(
                            text = "${sleepAnalysis.weeklyScore}% Ótimo",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF818CF8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DETALHAMENTO DAS FASES DO SONO",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = Color(0xFF475569)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Segmented Progress Bar representing Sleep Stages
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(sleepAnalysis.deepPercentage.coerceAtLeast(1).toFloat())
                        .background(Color(0xFF312E81))
                )
                if (sleepAnalysis.remSleepMins > 0) {
                    Box(
                        modifier = Modifier
                            .weight(sleepAnalysis.remPercentage.coerceAtLeast(1).toFloat())
                            .background(Color(0xFF7C3AED))
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(sleepAnalysis.lightPercentage.coerceAtLeast(1).toFloat())
                        .background(Color(0xFF0288D1))
                )
                Box(
                    modifier = Modifier
                        .weight(sleepAnalysis.awakePercentage.coerceAtLeast(1).toFloat())
                        .background(Color(0xFFF59E0B))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Stage Cards Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepStageItem(
                    stage = "Profundo",
                    duration = "${sleepAnalysis.deepSleepMins / 60}h ${sleepAnalysis.deepSleepMins % 60}m",
                    percentage = "${sleepAnalysis.deepPercentage}%",
                    color = Color(0xFF312E81),
                    testTag = "sleep_stage_deep_display",
                    modifier = Modifier.weight(1f)
                )

                SleepStageItem(
                    stage = "REM",
                    duration = if (sleepAnalysis.remSleepMins > 0) {
                        "${sleepAnalysis.remSleepMins / 60}h ${sleepAnalysis.remSleepMins % 60}m"
                    } else {
                        "--"
                    },
                    percentage = if (sleepAnalysis.remSleepMins > 0) "${sleepAnalysis.remPercentage}%" else "n/d",
                    color = Color(0xFF7C3AED),
                    testTag = "sleep_stage_rem_display",
                    modifier = Modifier.weight(1f)
                )

                SleepStageItem(
                    stage = "Leve",
                    duration = "${sleepAnalysis.lightSleepMins / 60}h ${sleepAnalysis.lightSleepMins % 60}m",
                    percentage = "${sleepAnalysis.lightPercentage}%",
                    color = Color(0xFF0288D1),
                    testTag = "sleep_stage_light_display",
                    modifier = Modifier.weight(1f)
                )

                SleepStageItem(
                    stage = "Acordado",
                    duration = "${sleepAnalysis.awakeMins}m",
                    percentage = "${sleepAnalysis.awakePercentage}%",
                    color = Color(0xFFF59E0B),
                    testTag = "sleep_stage_awake_display",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7-Day Sleep Duration Trend Bar Chart
            Text(
                text = "TENDÊNCIA DE DURAÇÃO DO SONO (7 DIAS)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = Color(0xFF475569)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                val maxDailyMins = (sleepAnalysis.dailySleepMinutes.maxOfOrNull { it.second } ?: 480).coerceAtLeast(300)

                sleepAnalysis.dailySleepMinutes.forEach { (day, mins) ->
                    val barHeightRatio = (mins.toFloat() / maxDailyMins).coerceIn(0.2f, 1f)
                    val hours = mins / 60f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1fh", hours),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((60 * barHeightRatio).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (hours >= 7f) Color(0xFF4338CA) else Color(0xFF818CF8))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepStageItem(
    stage: String,
    duration: String,
    percentage: String,
    color: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stage,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = Color(0xFF1E293B)
            )
            Text(
                text = duration,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF0F172A)
            )
            Text(
                text = percentage,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color(0xFF64748B)
            )
        }
    }
}
