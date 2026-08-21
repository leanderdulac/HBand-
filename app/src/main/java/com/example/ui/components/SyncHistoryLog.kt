package com.example.ui.components

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MinimalBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncLogEntry(
    val id: String,
    val source: String, // "WorkManager Task" or "Room Local Queue"
    val timestampMillis: Long,
    val status: String, // "SUCCEEDED", "RUNNING", "ENQUEUED", "SYNCED", "FAILED"
    val summary: String,
    val details: String? = null,
    val attemptCount: Int = 1
)

@Composable
fun SyncHistoryLog(
    syncLogs: List<SyncLogEntry>,
    onRefreshWorkManager: () -> Unit,
    onTriggerSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(syncLogs, selectedFilter) {
        when (selectedFilter) {
            "SUCCESS" -> syncLogs.filter { it.status == "SUCCEEDED" || it.status == "SYNCED" }
            "FAILED" -> syncLogs.filter { it.status == "FAILED" }
            "RUNNING" -> syncLogs.filter { it.status == "RUNNING" || it.status == "ENQUEUED" || it.status == "PENDING" }
            else -> syncLogs
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_history_log_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Title
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
                            .background(Color(0xFFE8F0FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF1A73E8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Histórico de Sincronização WorkManager",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Text(
                            text = "Registro de tarefas em segundo plano e envios via API",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF44474E)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshWorkManager,
                        modifier = Modifier.testTag("refresh_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar WorkManager",
                            tint = Color(0xFF00639B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill("Todos (${syncLogs.size})", selectedFilter == "ALL") { selectedFilter = "ALL" }
                FilterPill("Sucesso (${syncLogs.count { it.status == "SUCCEEDED" || it.status == "SYNCED" }})", selectedFilter == "SUCCESS") { selectedFilter = "SUCCESS" }
                FilterPill("Falhou (${syncLogs.count { it.status == "FAILED" }})", selectedFilter == "FAILED") { selectedFilter = "FAILED" }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum registro de histórico encontrado para o filtro selecionado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF44474E)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredLogs.take(20).forEach { log ->
                        SyncLogEntryCard(log = log)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF00639B),
            selectedLabelColor = Color.White,
            containerColor = Color(0xFFF1F4F9),
            labelColor = Color(0xFF44474E)
        )
    )
}

@Composable
private fun SyncLogEntryCard(log: SyncLogEntry) {
    val dateFormat = SimpleDateFormat("dd/MM, HH:mm:ss", Locale.forLanguageTag("pt-BR"))
    val formattedTime = dateFormat.format(Date(log.timestampMillis))

    val (badgeColor, statusLabel, icon) = when (log.status) {
        "SUCCEEDED", "SYNCED" -> Triple(Color(0xFF2E7D32), "SUCESSO", Icons.Default.CheckCircle)
        "RUNNING" -> Triple(Color(0xFF00639B), "EXECUTANDO", Icons.Default.Sync)
        "ENQUEUED", "PENDING" -> Triple(Color(0xFFE65100), "EM FILA", Icons.Default.HourglassTop)
        else -> Triple(Color(0xFFC62828), "FALHOU", Icons.Default.Error)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFE))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.source,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = log.summary,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF1E293B)
            )

            if (!log.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.details,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = if (log.status == "FAILED") Color(0xFFC62828) else Color(0xFF64748B)
                )
            }
        }
    }
}
