package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.repository.ApiHealthState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApiHeader(
    apiHealth: ApiHealthState,
    onRefreshHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Zero is the repository's explicit "not checked" sentinel. Do not infer failure.
    val checked = apiHealth.lastCheckTime > 0
    val status = when {
        !checked -> "Ainda não verificado"
        apiHealth.isOnline -> "Serviço respondeu à última verificação"
        apiHealth.statusCode == 401 || apiHealth.statusCode == 403 -> "Verificação recusada pelo serviço"
        else -> "Última verificação do serviço falhou"
    }
    Card(
        modifier = modifier.fillMaxWidth().testTag("api_header_card"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Verificação do serviço de envio", style = MaterialTheme.typography.titleMedium)
            Column(
                Modifier.testTag("api_health_result").semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(status, style = MaterialTheme.typography.bodyLarge)
                if (checked) {
                    Text(
                        "Horário no celular: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))
                            .format(Date(apiHealth.lastCheckTime)),
                        modifier = Modifier.testTag("api_health_checked_at"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                "Esta consulta não confirma autorização para enviar leituras nem o recebimento de registros pela equipe de saúde.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(
                onClick = onRefreshHealth,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ping_api_button"),
            ) { Text("Verificar serviço de envio") }
        }
    }
}
