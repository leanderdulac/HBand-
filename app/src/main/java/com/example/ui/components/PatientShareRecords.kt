package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.data.local.HBandSensorMetricEntity
import com.example.util.ShareProgressData

/** CSV needs only history; a combined card also needs the two diary totals. */
@Composable
fun PatientShareRecords(
    metrics: List<HBandSensorMetricEntity>?,
    hydrationMl: Int?,
    breathingSeconds: Int?,
    metricsReadFailed: Boolean,
    diaryReadFailed: Boolean,
    onRetryRead: () -> Unit,
    onGenerate: (ShareProgressData) -> Unit,
    onNotify: (String) -> Unit,
) {
    if (metricsReadFailed) {
        LocalReadNotice("os registros para compartilhar", onRetryRead)
        return
    }
    if (diaryReadFailed) LocalReadNotice("os registros do diário para o cartão", onRetryRead)
    else ShareProgressCard(metrics, hydrationMl, breathingSeconds, onGenerate)
    CsvExportCard(metrics, onNotify)
}
