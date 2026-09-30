package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val AI_INSIGHT_REVIEW_LABEL =
    "Resumo automático para revisão, gerado por IA ou por regras locais. Não é diagnóstico nem orientação médica."

/** "Gerado às HH:mm" in the phone's local time, or null when nothing was generated yet. */
internal fun insightGeneratedLabel(generatedAtMillis: Long?, zone: TimeZone = TimeZone.getDefault()): String? =
    generatedAtMillis?.let {
        val format = SimpleDateFormat("HH:mm", Locale("pt", "BR")).apply { timeZone = zone }
        "Gerado às ${format.format(Date(it))}"
    }

/**
 * Highlighted AI summary shown at the top of Visão geral when AI_INSIGHT_ENABLED=true.
 * Theme tokens only (primaryContainer/onPrimaryContainer, primary/onPrimary, onSurfaceVariant),
 * so contrast holds for the current light scheme and any future dark scheme.
 */
@Composable
internal fun AiInsightHighlightCard(
    insightText: String,
    isLoading: Boolean,
    onRefreshInsight: () -> Unit,
    modifier: Modifier = Modifier,
    generatedAtMillis: Long? = null,
    failed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val background = Brush.verticalGradient(
        listOf(colors.primaryContainer, lerp(colors.primaryContainer, colors.surface, 0.45f)),
    )
    Card(
        modifier.fillMaxWidth().testTag("ai_insight_highlight_card"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = colors.onPrimaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().background(background).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Inteligência artificial",
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp).testTag("ai_insight_icon"),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Resumo com IA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        insightGeneratedLabel(generatedAtMillis) ?: "Últimos 7 dias",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.testTag("ai_insight_generated_at"),
                    )
                }
            }
            when {
                isLoading -> Column(
                    Modifier.fillMaxWidth().testTag("ai_insight_loading"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    ShimmerLines()
                    Text("Gerando o resumo…", style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
                }
                failed -> Text(
                    "Não foi possível gerar o resumo agora. Toque em “Atualizar resumo” para tentar de novo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.testTag("ai_insight_error"),
                )
                insightText.isBlank() -> Text(
                    "Assim que o relógio enviar medições, o resumo dos últimos 7 dias aparece aqui.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.testTag("ai_insight_empty"),
                )
                else -> Text(
                    insightText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.testTag("gemini_insight_text"),
                )
            }
            Text(
                AI_INSIGHT_REVIEW_LABEL,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.testTag("ai_insight_review_label"),
            )
            Button(
                onClick = onRefreshInsight,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("refresh_gemini_insight_button"),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Atualizar resumo")
            }
        }
    }
}

@Composable
private fun ShimmerLines() {
    val transition = rememberInfiniteTransition(label = "ai_insight_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ai_insight_shimmer_alpha",
    )
    val bar = MaterialTheme.colorScheme.surface
    listOf(1f, 0.92f, 0.6f).forEach { fraction ->
        Box(
            Modifier.fillMaxWidth(fraction).height(14.dp).alpha(alpha)
                .clip(RoundedCornerShape(7.dp)).background(bar),
        )
    }
}
