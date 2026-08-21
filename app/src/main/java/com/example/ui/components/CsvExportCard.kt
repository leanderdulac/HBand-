package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MinimalBorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CsvExportCard(
    metrics: List<HBandSensorMetricEntity>,
    onShowNotification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPreview by remember { mutableStateOf(false) }

    val csvString = remember(metrics) {
        buildCsvString(metrics)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("csv_export_card"),
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
                            .background(Color(0xFFE3F2FD)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = Color(0xFF0288D1),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Exportar Banco de Dados de Saúde",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Text(
                            text = "${metrics.size} registros de saúde salvos no Room",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF44474E)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F4F9)
                ) {
                    Text(
                        text = "Formato CSV",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00639B),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (metrics.isEmpty()) {
                            onShowNotification("Nenhum registro de saúde no banco para exportar.")
                        } else {
                            shareCsvFile(context, csvString, onShowNotification)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_csv_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compartilhar CSV", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }

                OutlinedButton(
                    onClick = {
                        if (metrics.isEmpty()) {
                            onShowNotification("Nenhum registro no banco para copiar.")
                        } else {
                            copyCsvToClipboard(context, csvString)
                            onShowNotification("CSV copiado para a área de transferência (${metrics.size} registros)")
                        }
                    },
                    modifier = Modifier.testTag("copy_csv_button"),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF00639B)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00639B))
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar CSV", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expand Preview Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8F9FF))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pré-visualização do CSV",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF44474E)
                )

                IconButton(
                    onClick = { showPreview = !showPreview },
                    modifier = Modifier.size(28.dp).testTag("toggle_csv_preview")
                ) {
                    Icon(
                        imageVector = if (showPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle CSV Preview",
                        tint = Color(0xFF00639B)
                    )
                }
            }

            AnimatedVisibility(visible = showPreview) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    val previewLines = csvString.lines().take(6).joinToString("\n")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (previewLines.isNotBlank()) previewLines else "No CSV data available",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun buildCsvString(metrics: List<HBandSensorMetricEntity>): String {
    val sb = StringBuilder()
    // CSV Header
    sb.append("ID,Device ID,Timestamp,Timestamp_ms,HeartRate_BPM,Systolic_BP_mmHg,Diastolic_BP_mmHg,SpO2_Percent,Temp_Celsius,Steps,Calories_kcal,Distance_Meters,HRV_ms,DeepSleep_min,LightSleep_min,Awake_min\n")

    metrics.forEach { item ->
        sb.append("${item.id},")
        sb.append("\"${item.deviceId.replace("\"", "\"\"")}\",")
        sb.append("\"${item.timestamp.replace("\"", "\"\"")}\",")
        sb.append("${item.timestampMillis},")
        sb.append("${item.heartRate},")
        sb.append("${item.systolicBp},")
        sb.append("${item.diastolicBp},")
        sb.append("${item.spO2},")
        sb.append("${item.temperatureCelsius},")
        sb.append("${item.steps},")
        sb.append("${item.calories},")
        sb.append("${item.distanceMeters},")
        sb.append("${item.hrvScore},")
        sb.append("${item.deepSleepMinutes},")
        sb.append("${item.lightSleepMinutes},")
        sb.append("${item.awakeMinutes}\n")
    }
    return sb.toString()
}

private fun shareCsvFile(
    context: Context,
    csvContent: String,
    onShowNotification: (String) -> Unit
) {
    try {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val csvFile = File(exportDir, "hband_health_metrics_export.csv")
        csvFile.writeText(csvContent)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "HBand Wearable Health Metrics Export (Room SQLite)")
            putExtra(Intent.EXTRA_TEXT, "Attached is the exported CSV file containing Room database health metrics from HBand Wearable.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Export Health Metrics CSV")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)

        onShowNotification("CSV export file generated securely")
    } catch (e: Exception) {
        onShowNotification("Failed to export CSV: ${e.message}")
    }
}

private fun copyCsvToClipboard(context: Context, csvContent: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("HBand Health Metrics CSV", csvContent)
    clipboard.setPrimaryClip(clip)
}
