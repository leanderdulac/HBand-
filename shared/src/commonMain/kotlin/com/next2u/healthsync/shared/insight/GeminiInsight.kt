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
        val lines = availableSummaryLines(recent)
        if (lines.isEmpty()) return null
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
            ${lines.joinToString("\n")}
            
            Forneça um resumo de saúde conciso de 2 a 3 frases e uma recomendação prática. Comente só as métricas listadas acima. NÃO mencione VFC, passos, calorias, sono, pressão ou movimento se essa linha não aparecer — valor ausente não é zero. Seja encorajador, objetivo e profissional. NÃO use títulos em markdown nem listas com marcadores. Escreva totalmente em português do Brasil.
        """.trimIndent()
    }

    fun fallbackInsight(avgHr: Int, avgHrv: Int, steps: Int, activeMins: Int): String {
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

    fun fallbackFrom(metrics: List<SensorMetric>, nowMillis: Long): String {
        val clinical = clinicalRecords(metrics)
        if (clinical.isEmpty()) return EMPTY_CLINICAL
        val start = nowMillis - 7L * 24 * 60 * 60 * 1000
        val recent = clinical.filter { it.timestampMillis >= start }.ifEmpty { clinical }
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

    private fun availableSummaryLines(recent: List<SensorMetric>): List<String> {
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
        if (clauses.isEmpty()) return EMPTY_CLINICAL
        val body = clauses.joinToString(", ").replaceFirstChar { it.uppercase() }
        return "Nos últimos 7 dias, $body nos seus registros de telemetria."
    }

    private fun List<Int>.averageOrZero(): Int = if (isEmpty()) 0 else average().toInt()
}
