package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig

@Composable
fun GeminiHealthInsightCard(
    insightText: String,
    isLoading: Boolean,
    onRefreshInsight: () -> Unit,
    modifier: Modifier = Modifier,
    showInsight: Boolean = BuildConfig.AI_INSIGHT_ENABLED,
    generatedAtMillis: Long? = null,
    failed: Boolean = false,
) {
    if (showInsight) {
        AiInsightHighlightCard(insightText, isLoading, onRefreshInsight, modifier, generatedAtMillis, failed)
        return
    }
    Card(
        modifier.fillMaxWidth().testTag("gemini_health_insight_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Resumo automático em validação", style = MaterialTheme.typography.titleLarge)
            Text("Este resumo ainda precisa de revisão antes de ser apresentado como informação de saúde.", style = MaterialTheme.typography.bodyLarge)
            Text("Você pode consultar os registros disponíveis na aba Histórico.", style = MaterialTheme.typography.bodyLarge)
            if (BuildConfig.DEBUG) PatientSection("Resumo para revisão técnica") {
                Text("Desenvolvimento: origem, período e cálculos não confirmados. O texto pode vir do serviço de IA ou de regras locais. Não usar como orientação de saúde.", style = MaterialTheme.typography.bodyLarge)
                if (isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Aguardando o resumo…", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        insightText.ifBlank { "Nenhum texto disponível para revisão." },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("gemini_insight_text"),
                    )
                }
                OutlinedButton(
                    onClick = onRefreshInsight,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("refresh_gemini_insight_button"),
                ) { Text("Gerar texto para revisão") }
            }
        }
    }
}
