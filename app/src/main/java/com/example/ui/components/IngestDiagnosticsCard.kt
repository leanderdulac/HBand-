package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ingest.IngestDiagnostics

@Composable
fun IngestDiagnosticsCard(diagnostics: IngestDiagnostics, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("ingest_diagnostics_card"), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnóstico de envio", style = MaterialTheme.typography.titleMedium)
            if (diagnostics.readFailed) {
                Text("Não foi possível ler a fila. Tente a leitura novamente na tela Envios.")
            } else if (!diagnostics.loaded) {
                Text("Carregando configuração e registros locais…")
            } else {
                val config = diagnostics.configuration
                Text("Destino: ${config.origin}")
                Text("Chave: ${if (config.keyConfigured) "configurada" else "ausente ou inválida"} (${if (config.usingSettingsOverride) "ajustes do aparelho" else "configuração do aplicativo"}).")
                Text("Aguardando envio: ${diagnostics.pending}. Com falha: ${diagnostics.failed}.")
                Text("Concluídos no aplicativo: ${diagnostics.synced}. Estado desconhecido: ${diagnostics.unknown}.")
                if (diagnostics.authorizationPaused) Text(com.example.data.ingest.QueueAuthorization.PAUSED_MESSAGE)
                config.configurationError?.let { Text(it) }
                Text("Configuração e resposta de conexão não comprovam autorização, recebimento das leituras ou visibilidade pela equipe.")
            }
        }
    }
}
