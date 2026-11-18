package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.hband.WatchAutoMeasureState
import com.example.data.hband.WatchFace

/** Ajustes > Relógio: ver se a medição automática está ligada. */
@Composable
internal fun WatchFaceCard(
    autoMeasure: WatchAutoMeasureState,
) {
    WatchFace.autoMeasureLabel(autoMeasure)?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().testTag("watch_auto_measure_label"),
        )
    }
}
