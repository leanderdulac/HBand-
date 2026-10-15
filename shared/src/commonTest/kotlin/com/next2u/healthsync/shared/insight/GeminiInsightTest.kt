package com.next2u.healthsync.shared.insight

import com.next2u.healthsync.shared.store.PatientStore
import com.next2u.healthsync.shared.store.SensorMetric
import com.next2u.healthsync.shared.store.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiInsightTest {
    @Test
    fun highlight_flag_mirrors_android_true() {
        assertTrue(GeminiInsight.isHighlightEnabled("true"))
        assertTrue(GeminiInsight.isHighlightEnabled("YES"))
        assertTrue(GeminiInsight.isHighlightEnabled("1"))
        assertFalse(GeminiInsight.isHighlightEnabled("false"))
        assertFalse(GeminiInsight.isHighlightEnabled(null))
    }

    @Test
    fun placeholder_keys_match_android() {
        assertTrue(GeminiInsight.isPlaceholderKey("YOUR_GEMINI_API_KEY"))
        assertTrue(GeminiInsight.isPlaceholderKey("MY_GEMINI_API_KEY"))
        assertTrue(GeminiInsight.isPlaceholderKey(""))
        assertFalse(GeminiInsight.isPlaceholderKey("real-key"))
    }

    @Test
    fun prompt_uses_android_sentences_and_stored_profile_only() {
        val now = 2_000_000_000_000L
        val metrics = listOf(
            SensorMetric(id = 1, deviceId = "AA", timestamp = "t", timestampMillis = now - 1_000, heartRate = 72, hrvScore = 70, steps = 9000),
        )
        val profile = UserProfile(
            fullName = "Alex Rivera",
            age = 32,
            gender = "Masculino",
            heightCm = 178f,
            weightKg = 74f,
            medicalNotes = "Monitoramento cardiovascular preventivo com HBand VE30.",
        )
        val prompt = GeminiInsight.buildPrompt(metrics, profile, now)
        assertNotNull(prompt)
        assertTrue(prompt.contains("Português do Brasil"))
        assertTrue(prompt.contains("Dados do perfil cadastrado neste aparelho:"))
        assertTrue(prompt.contains("Alex Rivera"))
        assertTrue(prompt.contains("32 anos"))
        assertTrue(prompt.contains("Masculino"))
        assertTrue(prompt.contains("178"))
        assertTrue(prompt.contains("74"))
        assertTrue(prompt.contains("Observações: Monitoramento cardiovascular preventivo com HBand VE30."))
        assertFalse(prompt.contains("nascimento"))
        assertFalse(prompt.contains("medicamento"))
        assertTrue(prompt.contains("NÃO use títulos em markdown"))
    }

    @Test
    fun empty_clinical_returns_android_copy() {
        val now = 1L
        val metrics = listOf(SensorMetric(id = 1, deviceId = "AA", timestamp = "t", timestampMillis = now, heartRate = 0, steps = 10))
        assertNull(GeminiInsight.buildPrompt(metrics, UserProfile(), now))
        assertEquals(GeminiInsight.EMPTY_CLINICAL, GeminiInsight.fallbackFrom(metrics, now))
    }

    @Test
    fun store_helpers_do_not_invent_clinical_fields() {
        PatientStore.resetForTests()
        assertNull(GeminiInsight.buildPromptFromStore(1L))
        assertEquals(GeminiInsight.EMPTY_CLINICAL, GeminiInsight.fallbackFromStore(1L))
        assertTrue(GeminiInsight.generateUrl("real-key").contains("gemini-3.6-flash"))
        assertTrue(GeminiInsight.generateUrl("real-key").contains("key=real-key"))
    }

    @Test
    fun fallback_copy_is_frozen() {
        val text = GeminiInsight.fallbackInsight(70, 70, 9000, 40)
        assertTrue(text.contains("70 BPM"))
        assertTrue(text.contains("excelente recuperação"))
        assertEquals(
            "Resumo automático para revisão, gerado por IA ou por regras locais. Não é diagnóstico nem orientação médica.",
            GeminiInsight.reviewLabel(),
        )
        assertEquals("Resumo com IA", GeminiInsight.highlightTitle())
        assertEquals("Atualizar resumo", GeminiInsight.refreshLabel())
    }
}
