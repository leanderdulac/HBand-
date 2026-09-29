package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class SyncDisplayStatus {
    LOADING,
    READ_ERROR,
    SYNCING,
    OFFLINE,
    FULLY_SYNCED,
    PENDING_QUEUE,
    AUTH_REQUIRED,
    FAILED
}

@Composable
fun SyncStatusIndicator(
    syncStatus: SyncDisplayStatus,
    pendingCount: Int,
    syncedCount: Int,
    failedCount: Int,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier,
    consecutiveFailures: Int = 0,
    onRefreshHealth: (() -> Unit)? = null,
    onRetryAll: (() -> Unit)? = null,
    onRetryRead: (() -> Unit)? = null,
) {
    val syncing = syncStatus == SyncDisplayStatus.SYNCING
    val readable = syncStatus != SyncDisplayStatus.LOADING && syncStatus != SyncDisplayStatus.READ_ERROR
    val title = when (syncStatus) {
        SyncDisplayStatus.LOADING -> "Carregando fila…"
        SyncDisplayStatus.READ_ERROR -> "Não foi possível ler a fila"
        SyncDisplayStatus.SYNCING -> "Enviando registros"
        SyncDisplayStatus.OFFLINE -> "Última verificação do serviço falhou"
        SyncDisplayStatus.FULLY_SYNCED -> "Envio de dados"
        SyncDisplayStatus.PENDING_QUEUE -> "Há registros aguardando envio"
        SyncDisplayStatus.FAILED -> "Não foi possível enviar alguns registros"
        SyncDisplayStatus.AUTH_REQUIRED -> "Envios pausados: acesso não autorizado"
    }
    val description = when (syncStatus) {
        SyncDisplayStatus.LOADING -> "Aguarde a consulta dos registros salvos no aplicativo."
        SyncDisplayStatus.READ_ERROR -> "Os registros não foram apagados por esta tentativa. Tente ler a fila novamente. Essa ação não envia dados."
        SyncDisplayStatus.SYNCING -> "Aguarde enquanto o aplicativo tenta enviar os registros."
        SyncDisplayStatus.OFFLINE -> "Confira a conexão e a configuração do serviço. Se precisar, peça ajuda à equipe responsável pelo aplicativo."
        SyncDisplayStatus.FULLY_SYNCED -> "A fila exibida não tem envios pendentes. Isso não confirma o recebimento pela equipe de saúde."
        SyncDisplayStatus.PENDING_QUEUE -> "Os registros continuam na fila do aplicativo. Você pode tentar enviá-los agora."
        SyncDisplayStatus.FAILED -> "Há registros que não puderam ser enviados. Confira os detalhes da falha antes de tentar novamente."
        SyncDisplayStatus.AUTH_REQUIRED -> com.example.data.ingest.QueueAuthorization.PAUSED_MESSAGE
    }
    Card(
        modifier = modifier.fillMaxWidth().testTag("workmanager_sync_status_card"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.testTag("sync_status_message").semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyLarge)
            }
            if (syncing || syncStatus == SyncDisplayStatus.LOADING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (syncStatus == SyncDisplayStatus.READ_ERROR && onRetryRead != null) {
                OutlinedButton(onClick = onRetryRead, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("retry_queue_read")) {
                    Text("Tentar ler a fila novamente")
                }
            }
            if (readable && pendingCount > 0) Text("Aguardando envio: $pendingCount", style = MaterialTheme.typography.bodyLarge)
            if (readable && failedCount > 0) Text("Registros com falha no envio: $failedCount", style = MaterialTheme.typography.bodyLarge)
            if (readable && (syncStatus != SyncDisplayStatus.FULLY_SYNCED || pendingCount > 0 || failedCount > 0)) {
                Button(
                    onClick = if ((failedCount > 0 || syncStatus == SyncDisplayStatus.AUTH_REQUIRED) && onRetryAll != null) onRetryAll else onTriggerSync,
                    enabled = !syncing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("trigger_workmanager_sync_button")
                ) {
                    Text(if (syncing) "Enviando…" else if (syncStatus == SyncDisplayStatus.AUTH_REQUIRED) "Tentar após corrigir o acesso" else if (failedCount > 0) "Tentar enviar novamente" else "Enviar registros")
                }
            }
            if (syncStatus == SyncDisplayStatus.OFFLINE && onRefreshHealth != null) {
                OutlinedButton(
                    onClick = onRefreshHealth,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("check_connectivity_health_button")
                ) { Text("Verificar serviço de envio") }
            }
        }
    }
}
