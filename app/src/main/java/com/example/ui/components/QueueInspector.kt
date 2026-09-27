package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface QueueRemoval {
    data object All : QueueRemoval
    data object Completed : QueueRemoval
    data class One(val id: Long) : QueueRemoval
}

@Composable
fun QueueInspector(
    pendingCount: Int,
    syncedCount: Int,
    failedCount: Int,
    queueItems: List<IngestQueueEntity>?,
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
    p1LocationHint: String? = null,
    modifier: Modifier = Modifier,
) {
    // Leave the loaded composition entirely, including any remembered removal dialog.
    // No queue operation is offered until a fresh query response is available.
    if (queueItems == null) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Envios", style = MaterialTheme.typography.headlineMedium)
            SyncStatusIndicator(SyncDisplayStatus.LOADING, 0, 0, 0, onSyncNow)
            PatientSection("Ver registros da fila") {
                Text("Aguarde a consulta dos registros salvos no aplicativo.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    var removal by remember { mutableStateOf<QueueRemoval?>(null) }
    val ordered = remember(queueItems) {
        queueItems.sortedWith(compareBy<IngestQueueEntity> {
            when (it.status) { QueueStatus.FAILED.name -> 0; QueueStatus.PENDING.name -> 1; else -> 2 }
        }.thenByDescending { it.createdAt }).take(30)
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Envios", style = MaterialTheme.typography.headlineMedium)
        SyncStatusIndicator(
            syncStatus = when {
                isSyncing -> SyncDisplayStatus.SYNCING
                queueItems.any(com.example.data.ingest.QueueAuthorization::isBlocked) -> SyncDisplayStatus.AUTH_REQUIRED
                failedCount > 0 -> SyncDisplayStatus.FAILED
                pendingCount > 0 -> SyncDisplayStatus.PENDING_QUEUE
                else -> SyncDisplayStatus.FULLY_SYNCED
            },
            pendingCount = pendingCount, syncedCount = syncedCount, failedCount = failedCount,
            onTriggerSync = onSyncNow, onRetryAll = onRetryAllFailed,
        )
        if (syncedCount > 0) {
            Text("Concluídos no aplicativo: $syncedCount", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Esta tela mostra a fila do aplicativo. Ela não confirma que os dados chegaram à equipe de saúde.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PatientSection("Ver registros da fila") {
            if (ordered.isEmpty()) Text("A lista exibida está vazia.", style = MaterialTheme.typography.bodyLarge)
            else Text("Até 30 registros, com pendências primeiro.", style = MaterialTheme.typography.bodyLarge)
            ordered.forEach { item ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(queueStatusLabel(item.status), style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (item.createdAt > 0) "Entrou na fila em " + SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(item.createdAt))
                            else "Data de entrada na fila indisponível",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (item.status == QueueStatus.FAILED.name) {
                            Text("O aplicativo não conseguiu concluir este envio.", style = MaterialTheme.typography.bodyLarge)
                            Button(
                                onClick = { onRetryFailedItem(item.id) }, enabled = !isSyncing,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("retry_queue_${item.id}"),
                            ) { Text("Tentar enviar novamente") }
                        }
                    }
                }
            }
        }
        if (BuildConfig.DEBUG) {
            PatientSection("Detalhes para suporte") {
                Text("Ferramentas de desenvolvimento. Apagar itens pode impedir o envio de registros.", style = MaterialTheme.typography.bodyLarge)
                if (syncedCount > 0) OutlinedButton(
                    onClick = { removal = QueueRemoval.Completed }, enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Apagar concluídos da fila") }
                if (queueItems.isNotEmpty()) OutlinedButton(
                    onClick = { removal = QueueRemoval.All }, enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("clear_queue_button"),
                ) { Text("Apagar toda a fila") }
                ordered.forEach { item ->
                    Text("Registro #${item.id}: ${queueStatusLabel(item.status)}", style = MaterialTheme.typography.titleMedium)
                    item.errorMessage?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(
                        onClick = { onInspectItem(item) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Ver dados técnicos do registro #${item.id}") }
                    OutlinedButton(
                        onClick = { removal = QueueRemoval.One(item.id) }, enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("delete_queue_${item.id}"),
                    ) { Text("Apagar registro #${item.id} da fila") }
                }
                SyncHistoryLog(syncLogs, onRefreshWorkManager, onSyncNow)
            }
        }
    }
    removal?.let { requested ->
        AlertDialog(
            onDismissRequest = { removal = null },
            title = { Text("Apagar registros da fila?") },
            text = { Text(when (requested) {
                QueueRemoval.All -> "Todos os registros da fila serão apagados, inclusive os que ainda não foram enviados. Essa ação não pode ser desfeita pelo aplicativo."
                QueueRemoval.Completed -> "Os registros marcados como concluídos serão apagados da fila. Essa ação não pode ser desfeita pelo aplicativo."
                is QueueRemoval.One -> "O registro #${requested.id} será apagado da fila. Se ainda não foi enviado, ele deixará de ser enviado por esta fila."
            } + " A exclusão será recusada se a fila estiver ocupada. Ela não desfaz envios anteriores.") },
            confirmButton = {
                Button(
                    onClick = {
                        removal = null
                        when (requested) {
                            QueueRemoval.All -> onClearAll()
                            QueueRemoval.Completed -> onClearSynced()
                            is QueueRemoval.One -> onDeleteItem(requested.id)
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier.heightIn(min = 56.dp).testTag("confirm_queue_removal"),
                ) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { removal = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") }
            },
        )
    }
}

internal fun queueStatusLabel(status: String): String = when (status) {
    QueueStatus.PENDING.name -> "Aguardando envio"
    QueueStatus.FAILED.name -> "Falha no envio"
    QueueStatus.SYNCED.name -> "Concluído no aplicativo"
    else -> "Situação do envio indisponível"
}
