package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.local.HBandSensorMetricEntity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CsvExportCard(
    metrics: List<HBandSensorMetricEntity>?,
    onShowNotification: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showPreview by remember { mutableStateOf(false) }
    Column(modifier.testTag("csv_export_card")) {
        PatientSection("Arquivo para suporte") {
            var preparing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            fun prepareThen(action: suspend (String) -> Unit) {
                val availableMetrics = metrics?.takeIf { it.isNotEmpty() } ?: return
                if (preparing) return
                preparing = true
                scope.launch {
                    try {
                        val csvString = withContext(Dispatchers.Default) { buildCsvString(availableMetrics) }
                        action(csvString)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onShowNotification("Não foi possível preparar o arquivo. Tente novamente.")
                    } finally {
                        preparing = false
                    }
                }
            }
            Text("Arquivo com os registros salvos", style = MaterialTheme.typography.titleLarge)
            Text(
                if (metrics == null) "Carregando registros…" else "${metrics.size} registros disponíveis neste celular.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("O formato CSV permite abrir os registros em uma planilha. O arquivo mantém os campos originais, inclusive valores cuja medição não foi confirmada.", style = MaterialTheme.typography.bodyLarge)
            Text("Confira o destinatário antes de compartilhar seus dados de saúde.", style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { prepareThen { shareCsvFile(context, it, onShowNotification) } },
                enabled = !metrics.isNullOrEmpty() && !preparing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("share_csv_button"),
            ) { Text("Escolher com quem compartilhar") }
            OutlinedButton(
                onClick = {
                    prepareThen {
                        copyCsvToClipboard(context, it)
                        onShowNotification("Conteúdo copiado. Você pode colá-lo no local escolhido.")
                    }
                },
                enabled = !metrics.isNullOrEmpty() && !preparing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("copy_csv_button"),
            ) { Text("Copiar conteúdo do arquivo") }
            if (preparing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Preparando arquivo…", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(
                onClick = { if (metrics != null) showPreview = !showPreview },
                enabled = metrics != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("toggle_csv_preview"),
            ) { Text(if (showPreview) "Fechar prévia do arquivo" else "Ver prévia do arquivo") }
            if (showPreview && metrics != null) {
                val preview = remember(metrics) { buildCsvString(metrics.take(5)) }
                Text("Cabeçalho e até cinco registros. Deslize para o lado para ver as colunas.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("csv_preview"),
                )
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

private suspend fun shareCsvFile(
    context: Context,
    csvContent: String,
    onShowNotification: (String) -> Unit
) {
    try {
        val uri = withContext(Dispatchers.IO) {
            val exportDir = File(context.applicationContext.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            // Each request keeps its own snapshot, including while another app reads an earlier export.
            val csvFile = File.createTempFile("hband_health_metrics_export_", ".csv", exportDir)
            csvFile.writeText(csvContent)

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "next2u SAÚDE — arquivo de registros")
            putExtra(Intent.EXTRA_TEXT, "Arquivo CSV com os registros salvos no aplicativo next2u SAÚDE.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Compartilhar arquivo de registros")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)

        onShowNotification("Arquivo preparado. Escolha com quem compartilhar.")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        onShowNotification("Não foi possível preparar o arquivo. Tente novamente.")
    }
}

private fun copyCsvToClipboard(context: Context, csvContent: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("HBand Health Metrics CSV", csvContent)
    clipboard.setPrimaryClip(clip)
}
