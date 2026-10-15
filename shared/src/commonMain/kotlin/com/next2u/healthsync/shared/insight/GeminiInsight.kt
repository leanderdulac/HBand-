package com.next2u.healthsync.shared.insight

import com.next2u.healthsync.shared.store.PatientStore
import com.next2u.healthsync.shared.store.SensorMetric
import com.next2u.healthsync.shared.store.UserProfile

/**
 * Prompt and fallback copy from Android [GeminiHealthAnalyzer].
 * Profile lines use only UserProfile fields (no invented clinical data).
 */
object GeminiInsight {
    const val MODEL = "gemini-3.6-flash"
    const val REVIEW_LABEL =
        "Resumo automático para revisão, gerado por IA ou por regras locais. Não é diagnóstico nem orientação médica."
    const val EMPTY_CLINICAL =
        "Os registros disponíveis não contêm medições clínicas para esta análise. Contadores de atividade permanecem no histórico local."
    const val VALIDATION_TITLE = "Resumo automático em validação"
    const val VALIDATION_BODY =
        "Este resumo ainda precisa de revisão antes de ser apresentado como informação de saúde."
    const val VALIDATION_HINT = "Você pode consultar os registros disponíveis na aba Histórico."
    const val EMPTY_CARD =
        "Assim que o relógio enviar medições, o resumo dos últimos 7 dias aparece aqui."
    const val ERROR_CARD =
        "Não foi possível gerar o resumo agora. Toque em “Atualizar resumo” para tentar de novo."
    const val LOADING = "Gerando o resumo…"
    const val HIGHLIGHT_TITLE = "Resumo com IA"
    const val REFRESH = "Atualizar resumo"
    const val SEVEN_DAYS_LABEL = "Últimos 7 dias"
    const val GENERATE_HOST =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"

    private val PLACEHOLDERS = setOf(
        "",
        "MY_GEMINI_API_KEY",
        "YOUR_GEMINI_API_KEY",
        "YOUR_GEMINI_API_KEY_HERE",
    )

    fun isPlaceholderKey(key: String?): Boolean {
        val trimmed = key?.trim().orEmpty()
        return trimmed in PLACEHOLDERS
    }

    /** Android reads AI_INSIGHT_ENABLED=true from .env; iOS YES/true also enable the highlight card. */
    fun isHighlightEnabled(flag: String?): Boolean {
        val value = flag?.trim().orEmpty()
        return value.equals("true", ignoreCase = true) || value.equals("YES", ignoreCase = true) || value == "1"
    }

    /** Function accessors so Swift does not depend on Kotlin `const val` Obj-C names. */
    fun highlightTitle(): String = HIGHLIGHT_TITLE
    fun reviewLabel(): String = REVIEW_LABEL
    fun refreshLabel(): String = REFRESH
    fun loadingLabel(): String = LOADING
    fun errorCard(): String = ERROR_CARD
    fun emptyCard(): String = EMPTY_CARD
    fun sevenDaysLabel(): String = SEVEN_DAYS_LABEL
    fun validationTitle(): String = VALIDATION_TITLE
    fun validationBody(): String = VALIDATION_BODY
    fun validationHint(): String = VALIDATION_HINT

    fun generateUrl(apiKey: String): String = "$GENERATE_HOST?key=$apiKey"

    /** Avoids passing Kotlin List from Swift (Xcode 27 NSArray bridging). */
    fun buildPromptFromStore(nowMillis: Long): String? =
        buildPrompt(PatientStore.metrics(), PatientStore.profile(), nowMillis)

    fun fallbackFromStore(nowMillis: Long): String =
        fallbackFrom(PatientStore.metrics(), nowMillis)

    fun clinicalRecords(metrics: List<SensorMetric>): List<SensorMetric> = metrics.filter { row ->
        row.heartRate > 0 || row.systolicBp > 0 || row.diastolicBp > 0 || row.spO2 > 0 ||
            (row.temperatureCelsius.isFinite() && row.temperatureCelsius > 0f) || row.hrvScore > 0 ||
            row.deepSleepMinutes > 0 || row.lightSleepMinutes > 0 || row.awakeMinutes > 0
    }

    fun buildPrompt(metrics: List<SensorMetric>, profile: UserProfile, nowMillis: Long): String? {
        val clinical = clinicalRecords(metrics)
        if (clinical.isEmpty()) return null
        val start = nowMillis - 7L * 24 * 60 * 60 * 1000
        val recent = clinical.filter { it.timestampMillis >= start }.ifEmpty { clinical }
        val recordCount = recent.size
        val hrs = recent.map { it.heartRate }.filter { it > 0 }
        val avgHr = hrs.averageOrZero()
        val minHr = hrs.minOrNull() ?: 0
        val maxHr = hrs.maxOrNull() ?: 0
        val avgHrv = recent.map { it.hrvScore }.averageOrZero()
        val avgSys = recent.map { it.systolicBp }.filter { it > 0 }.averageOrZero()
        val avgDia = recent.map { it.diastolicBp }.filter { it > 0 }.averageOrZero()
        val totalSteps = recent.maxOfOrNull { it.steps } ?: 0
        val totalCalories = recent.maxOfOrNull { it.calories }?.toInt() ?: 0
        val maxSleepMins = recent.maxOfOrNull { it.deepSleepMinutes + it.lightSleepMinutes } ?: 0
        val activeMins = (recent.count { it.heartRate >= 75 || it.steps > 0 } * 3.5).toInt()
        return """
            Você é um assistente pessoal de saúde com IA analisando a telemetria dos sensores de um dispositivo vestível de um usuário nos últimos 7 dias a partir do banco de dados local. Responda ESTRITAMENTE em Português do Brasil (PT-BR).
            
            Dados do perfil cadastrado neste aparelho:
            - Nome: ${profile.fullName}
            - Idade: ${profile.age} anos
            - Gênero: ${profile.gender}
            - Altura: ${profile.heightCm} cm
            - Peso: ${profile.weightKg} kg
            - Observações: ${profile.medicalNotes}
            
            Resumo dos Dados dos Últimos 7 Dias:
            - Amostras Registradas: $recordCount métricas
            - Frequência Cardíaca Média: $avgHr BPM (Mín: $minHr, Máx: $maxHr)
            - Pontuação Média de VFC (HRV): $avgHrv / 100
            - Pressão Arterial Média: $avgSys/$avgDia mmHg
            - Pico de Passos Diários: $totalSteps passos
            - Movimento Ativo: ~$activeMins minutos
            - Calorias Diárias Estimadas: $totalCalories kcal
            - Sono Registrado: ${maxSleepMins / 60}h ${maxSleepMins % 60}m
            
            Forneça um resumo de saúde conciso de 2 a 3 frases e uma recomendação prática. Foque no equilíbrio cardiovascular, na recuperação/VFC e no nível de atividade. Seja encorajador, objetivo e profissional. NÃO use títulos em markdown nem listas com marcadores. Escreva totalmente em português do Brasil.
        """.trimIndent()
    }

    fun fallbackInsight(avgHr: Int, avgHrv: Int, steps: Int, activeMins: Int): String {
        val hrvStatus = if (avgHrv >= 65) "excelente recuperação do sistema nervoso autônomo" else "capacidade moderada de recuperação"
        val hrStatus = if (avgHr in 60..80) "faixa cardiovascular de repouso estável" else "esforço fisiológico elevado"
        val actStatus = if (steps >= 8000 || activeMins >= 30) "ótimos níveis de atividade física" else "movimento diário constante"
        return "Nos últimos 7 dias, sua frequência cardíaca média foi de $avgHr BPM, refletindo uma $hrStatus, com pontuação de VFC de $avgHrv ($hrvStatus). Você registrou ~$activeMins minutos ativos e $steps passos máximos, mantendo $actStatus nos seus registros de telemetria."
    }

    fun fallbackFrom(metrics: List<SensorMetric>, nowMillis: Long): String {
        val clinical = clinicalRecords(metrics)
        if (clinical.isEmpty()) return EMPTY_CLINICAL
        val start = nowMillis - 7L * 24 * 60 * 60 * 1000
        val recent = clinical.filter { it.timestampMillis >= start }.ifEmpty { clinical }
        val hrs = recent.map { it.heartRate }.filter { it > 0 }
        val avgHr = hrs.averageOrZero()
        val avgHrv = recent.map { it.hrvScore }.averageOrZero()
        val steps = recent.maxOfOrNull { it.steps } ?: 0
        val activeMins = (recent.count { it.heartRate >= 75 || it.steps > 0 } * 3.5).toInt()
        return fallbackInsight(avgHr, avgHrv, steps, activeMins)
    }

    private fun List<Int>.averageOrZero(): Int = if (isEmpty()) 0 else average().toInt()
}
