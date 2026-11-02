package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.hband.WatchAutoMeasureState
import com.example.data.hband.WatchFace
import com.example.data.hband.WatchFaceUiState

/** Ajustes > Relógio: trocar a foto do mostrador e ver se a medição automática está ligada. */
@Composable
internal fun WatchFaceCard(
    state: WatchFaceUiState,
    autoMeasure: WatchAutoMeasureState,
    enabled: Boolean,
    supported: Boolean,
    onPhotoPicked: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotoPicked(uri)
    }
    Column(Modifier.fillMaxWidth().testTag("watch_face_card"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WatchFace.autoMeasureLabel(autoMeasure)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("watch_auto_measure_label"))
        }
        if (supported) {
            Text("Coloque uma foto sua no mostrador do relógio.", style = MaterialTheme.typography.bodyMedium)
            state.progress?.let { p ->
                LinearProgressIndicator(progress = { p.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = if (state.lastResultOk == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("watch_face_message").semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = enabled && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("watch_face_pick_button"),
            ) { Text(if (state.busy) "Enviando foto…" else "Trocar foto do mostrador") }
        }
    }
}
