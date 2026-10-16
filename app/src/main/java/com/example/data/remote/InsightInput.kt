package com.example.data.remote

import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.UserProfileEntity

/** Activity-only observations must not enter the existing clinical insight path as zero vitals. */
internal fun clinicalInsightRecords(records: List<HBandSensorMetricEntity>): List<HBandSensorMetricEntity> =
    records.filter { row ->
        row.heartRate > 0 || row.systolicBp > 0 || row.diastolicBp > 0 || row.spO2 > 0 ||
            (row.temperatureCelsius.isFinite() && row.temperatureCelsius > 0f) || row.hrvScore > 0 ||
            row.deepSleepMinutes > 0 || row.lightSleepMinutes > 0 || row.awakeMinutes > 0
    }

internal const val EMPTY_CLINICAL_INSIGHT =
    "Os registros disponíveis não contêm medições clínicas para esta análise. Contadores de atividade permanecem no histórico local."

private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

/** Prompt lines for metrics that are actually present. Zero-because-missing is omitted. */
internal fun availableInsightLines(recent: List<HBandSensorMetricEntity>): List<String> {
    val lines = mutableListOf("- Amostras Registradas: ${recent.size} métricas")
    val hrs = recent.map { it.heartRate }.filter { it > 0 }
    if (hrs.isNotEmpty()) {
        lines += "- Frequência Cardíaca Média: ${hrs.average().toInt()} BPM (Mín: ${hrs.min()}, Máx: ${hrs.max()})"
    }
    val hrvs = recent.map { it.hrvScore }.filter { it > 0 }
    if (hrvs.isNotEmpty()) {
        lines += "- Pontuação Média de VFC (HRV): ${hrvs.average().toInt()} / 100"
    }
    val sys = recent.map { it.systolicBp }.filter { it > 0 }
    val dia = recent.map { it.diastolicBp }.filter { it > 0 }
    if (sys.isNotEmpty() && dia.isNotEmpty()) {
        lines += "- Pressão Arterial Média: ${sys.average().toInt()}/${dia.average().toInt()} mmHg"
    }
    val steps = recent.map { it.steps }.filter { it > 0 }
    if (steps.isNotEmpty()) {
        lines += "- Pico de Passos Diários: ${steps.max()} passos"
    }
    val activeMins = (recent.count { it.heartRate >= 75 || it.steps > 0 } * 3.5).toInt()
    if (activeMins > 0) {
        lines += "- Movimento Ativo: ~$activeMins minutos"
    }
    val calories = recent.map { it.calories }.filter { it.isFinite() && it > 0f }
    if (calories.isNotEmpty()) {
        lines += "- Calorias Diárias Estimadas: ${calories.max().toInt()} kcal"
    }
    val sleep = recent.map { it.deepSleepMinutes + it.lightSleepMinutes }.filter { it > 0 }
    if (sleep.isNotEmpty()) {
        val maxSleep = sleep.max()
        lines += "- Sono Registrado: ${maxSleep / 60}h ${maxSleep % 60}m"
    }
    return lines
}

/**
 * Same opening sentence as the previous Android prompt, plus stored profile fields only
 * (no invented clinical data). Missing 0-metrics are not listed.
 */
internal fun buildInsightPrompt(
    metrics: List<HBandSensorMetricEntity>,
    profile: UserProfileEntity?,
    nowMillis: Long,
): String? {
    val clinical = clinicalInsightRecords(metrics)
    if (clinical.isEmpty()) return null
    val recent = clinical.filter { it.timestampMillis >= nowMillis - SEVEN_DAYS_MS }.ifEmpty { clinical }
    val lines = availableInsightLines(recent)
    val profileBlock = profile?.let {
        """
            
            Dados do perfil cadastrado neste aparelho:
            - Nome: ${it.fullName}
            - Idade: ${it.age} anos
            - Gênero: ${it.gender}
            - Altura: ${it.heightCm} cm
            - Peso: ${it.weightKg} kg
            - Observações: ${it.medicalNotes}
        """.trimIndent()
    }.orEmpty()
    return """
            Você é um assistente pessoal de saúde com IA analisando a telemetria dos sensores de um dispositivo vestível de um usuário nos últimos 7 dias a partir do banco de dados local. Responda ESTRITAMENTE em Português do Brasil (PT-BR).
            $profileBlock
            
            Resumo dos Dados dos Últimos 7 Dias:
            ${lines.joinToString("\n")}
            
            Forneça um resumo de saúde conciso de 2 a 3 frases e uma recomendação prática. Comente só as métricas listadas acima. NÃO mencione VFC, passos, calorias, sono, pressão ou movimento se essa linha não aparecer — valor ausente não é zero. Seja encorajador, objetivo e profissional. NÃO use títulos em markdown nem listas com marcadores. Escreva totalmente em português do Brasil.
        """.trimIndent()
}

/** Frozen sentence when HR + HRV + activity are all present; otherwise omit zeros. */
internal fun fallbackInsight(avgHr: Int, avgHrv: Int, steps: Int, activeMins: Int): String {
    if (avgHr > 0 && avgHrv > 0 && (steps > 0 || activeMins > 0)) {
        val hrvStatus = if (avgHrv >= 65) "excelente recuperação do sistema nervoso autônomo" else "capacidade moderada de recuperação"
        val hrStatus = if (avgHr in 60..80) "faixa cardiovascular de repouso estável" else "esforço fisiológico elevado"
        val actStatus = if (steps >= 8000 || activeMins >= 30) "ótimos níveis de atividade física" else "movimento diário constante"
        return "Nos últimos 7 dias, sua frequência cardíaca média foi de $avgHr BPM, refletindo uma $hrStatus, com pontuação de VFC de $avgHrv ($hrvStatus). Você registrou ~$activeMins minutos ativos e $steps passos máximos, mantendo $actStatus nos seus registros de telemetria."
    }
    return fallbackAvailable(
        avgHr = avgHr.takeIf { it > 0 },
        avgHrv = avgHrv.takeIf { it > 0 },
        steps = steps.takeIf { it > 0 },
        activeMins = activeMins.takeIf { it > 0 },
    )
}

internal fun fallbackFrom(metrics: List<HBandSensorMetricEntity>, nowMillis: Long): String {
    val clinical = clinicalInsightRecords(metrics)
    if (clinical.isEmpty()) return EMPTY_CLINICAL_INSIGHT
    val recent = clinical.filter { it.timestampMillis >= nowMillis - SEVEN_DAYS_MS }.ifEmpty { clinical }
    val hrs = recent.map { it.heartRate }.filter { it > 0 }
    val hrvs = recent.map { it.hrvScore }.filter { it > 0 }
    val steps = recent.map { it.steps }.filter { it > 0 }
    val activeMins = (recent.count { it.heartRate >= 75 || it.steps > 0 } * 3.5).toInt()
    return fallbackInsight(
        avgHr = hrs.averageOrZero(),
        avgHrv = hrvs.averageOrZero(),
        steps = steps.maxOrNull() ?: 0,
        activeMins = activeMins,
    )
}

private fun fallbackAvailable(avgHr: Int?, avgHrv: Int?, steps: Int?, activeMins: Int?): String {
    val clauses = mutableListOf<String>()
    if (avgHr != null) {
        val hrStatus = if (avgHr in 60..80) "faixa cardiovascular de repouso estável" else "esforço fisiológico elevado"
        clauses += "sua frequência cardíaca média foi de $avgHr BPM, refletindo uma $hrStatus"
    }
    if (avgHrv != null) {
        val hrvStatus = if (avgHrv >= 65) "excelente recuperação do sistema nervoso autônomo" else "capacidade moderada de recuperação"
        clauses += "com pontuação de VFC de $avgHrv ($hrvStatus)"
    }
    val activity = buildList {
        if (activeMins != null) add("~$activeMins minutos ativos")
        if (steps != null) add("$steps passos máximos")
    }
    if (activity.isNotEmpty()) {
        clauses += "você registrou ${activity.joinToString(" e ")}"
    }
    if (clauses.isEmpty()) return EMPTY_CLINICAL_INSIGHT
    val body = clauses.joinToString(", ").replaceFirstChar { it.uppercase() }
    return "Nos últimos 7 dias, $body nos seus registros de telemetria."
}

private fun List<Int>.averageOrZero(): Int = if (isEmpty()) 0 else average().toInt()
