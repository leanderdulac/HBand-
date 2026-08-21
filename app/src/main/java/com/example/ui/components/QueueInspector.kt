package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.ui.theme.MinimalBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueueInspector(
    pendingCount: Int,
    syncedCount: Int,
    failedCount: Int,
    queueItems: List<IngestQueueEntity>,
    syncLogs: List<SyncLogEntry>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onRetryFailedItem: (Long) -> Unit,
    onRetryAllFailed: () -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearSynced: () -> Unit,
    onClearAll: () -> Unit,
    onInspectItem: (IngestQueueEntity) -> Unit,
    onRefreshWorkManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QueueStatCard(
                title = "Pendente",
                count = pendingCount,
                color = Color(0xFF00639B),
                icon = Icons.Default.HourglassEmpty,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_pending")
            )
            QueueStatCard(
                title = "Enviado",
                count = syncedCount,
                color = Color(0xFF006E1C),
                icon = Icons.Default.DoneAll,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_synced")
            )
            QueueStatCard(
                title = "Falhou",
                count = failedCount,
                color = Color(0xFFBA1A1A),
                icon = Icons.Default.Error,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_failed")
            )
        }

        // Action Toolbar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Processando...")
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizar Fila ($pendingCount)")
                        }
                    }

                    if (failedCount > 0) {
                        OutlinedButton(
                            onClick = onRetryAllFailed,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MinimalBorder),
                            modifier = Modifier.testTag("retry_all_failed_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00639B))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tentar $failedCount Falhas", color = Color(0xFF00639B))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (syncedCount > 0) {
                        Text(
                            text = "Limpar Enviados",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00639B),
                            modifier = Modifier
                                .clickable { onClearSynced() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (queueItems.isNotEmpty()) {
                        Text(
                            text = "Limpar Toda a Fila",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFBA1A1A),
                            modifier = Modifier
                                .clickable { onClearAll() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Queue Items List
        Text(
            text = "ITENS DA FILA OFFLINE ROOM (${queueItems.size})",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Color(0xFF44474E)
        )

        if (queueItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A fila offline está vazia. As leituras dos sensores serão acumuladas aqui quando offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF44474E)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                queueItems.take(30).forEach { item ->
                    QueueItemCard(
                        item = item,
                        onInspect = { onInspectItem(item) },
                        onRetry = { onRetryFailedItem(item.id) },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // WorkManager Sync History Log Component
        SyncHistoryLog(
            syncLogs = syncLogs,
            onRefreshWorkManager = onRefreshWorkManager,
            onTriggerSyncNow = onSyncNow,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QueueStatCard(
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF44474E))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = color)
            )
        }
    }
}

@Composable
private fun QueueItemCard(
    item: IngestQueueEntity,
    onInspect: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (item.status) {
        QueueStatus.PENDING.name -> Color(0xFF00639B)
        QueueStatus.SYNCED.name -> Color(0xFF006E1C)
        else -> Color(0xFFBA1A1A)
    }

    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = dateFormat.format(Date(item.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = item.status,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "#${item.id}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF44474E)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onInspect, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Code, contentDescription = "Inspecionar JSON", tint = Color(0xFF00639B))
                    }
                    if (item.status == QueueStatus.FAILED.name) {
                        IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Tentar novamente", tint = Color(0xFF00639B))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFBA1A1A))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Payload snippet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F4F9))
                    .padding(10.dp)
            ) {
                Text(
                    text = item.payloadJson.replace("\n", " ").take(100) + "...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = Color(0xFF191C1E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${item.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBA1A1A)
                )
            }
        }
    }
}
