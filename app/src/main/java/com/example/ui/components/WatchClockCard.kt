package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.hband.WatchClock
import com.example.data.hband.WatchClockUiState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Ajustes > Relógio: acerto manual da hora (também feito automaticamente a cada conexão). */
@Composable
internal fun WatchClockCard(
    state: WatchClockUiState,
    enabled: Boolean,
    onSyncTime: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("watch_clock_card"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val last = state.lastSyncEpochMs.takeIf { it > 0 }?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = if (state.lastResultOk == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("watch_clock_message"))
        }
        Text(WatchClock.lastSyncLabel(last), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("watch_clock_last_sync"))
        Button(
            onClick = onSyncTime,
            enabled = enabled && !state.syncing,
            modifier = Modifier.testTag("watch_clock_sync_button"),
        ) { Text(if (state.syncing) "Sincronizando…" else "Sincronizar hora") }
    }
}
