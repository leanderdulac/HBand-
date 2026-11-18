package com.example.data.remote

import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiInsightPromptTest {
    private val now = 2_000_000_000_000L

    private fun metric(
        heartRate: Int = 0,
        hrvScore: Int = 0,
        steps: Int = 0,
        calories: Float = 0f,
        systolicBp: Int = 0,
        diastolicBp: Int = 0,
        deepSleepMinutes: Int = 0,
        lightSleepMinutes: Int = 0,
    ) = HBandSensorMetricEntity(
        id = 1,
        deviceId = "AA",
        timestamp = "t",
        timestampMillis = now - 1_000,
        heartRate = heartRate,
        systolicBp = systolicBp,
        diastolicBp = diastolicBp,
        spO2 = 0,
        temperatureCelsius = 0f,
        steps = steps,
        calories = calories,
        distanceMeters = 0f,
        hrvScore = hrvScore,
        deepSleepMinutes = deepSleepMinutes,
        lightSleepMinutes = lightSleepMinutes,
        awakeMinutes = 0,
    )

    @Test fun prompt_includes_stored_profile_and_present_metrics() {
        val profile = UserProfileEntity(
            fullName = "Alex Rivera",
            age = 32,
            gender = "Masculino",
            heightCm = 178f,
            weightKg = 74f,
            medicalNotes = "Monitoramento cardiovascular preventivo com HBand VE30.",
        )
        val prompt = buildInsightPrompt(listOf(metric(heartRate = 72, hrvScore = 70, steps = 9000)), profile, now)
        assertNotNull(prompt)
        assertTrue(prompt!!.contains("Português do Brasil"))
        assertTrue(prompt.contains("Dados do perfil cadastrado neste aparelho:"))
        assertTrue(prompt.contains("Alex Rivera"))
        assertTrue(prompt.contains("32 anos"))
        assertTrue(prompt.contains("Masculino"))
        assertTrue(prompt.contains("178"))
        assertTrue(prompt.contains("74"))
        assertTrue(prompt.contains("Observações: Monitoramento cardiovascular preventivo com HBand VE30."))
        assertFalse(prompt.contains("nascimento"))
        assertFalse(prompt.contains("medicamento"))
        assertTrue(prompt.contains("Pontuação Média de VFC"))
        assertTrue(prompt.contains("Pico de Passos Diários: 9000"))
        assertTrue(prompt.contains("valor ausente não é zero"))
    }

    @Test fun prompt_omits_missing_zero_metrics() {
        val prompt = buildInsightPrompt(listOf(metric(heartRate = 72)), UserProfileEntity(), now)
        assertNotNull(prompt)
        assertTrue(prompt!!.contains("72 BPM"))
        assertFalse(prompt.contains("Pontuação Média de VFC"))
        assertFalse(prompt.contains("0 passos"))
        assertFalse(prompt.contains("Pico de Passos"))
        assertFalse(prompt.contains("0 / 100"))
        assertTrue(prompt.contains("valor ausente não é zero"))
        val fallback = fallbackFrom(listOf(metric(heartRate = 72)), now)
        assertTrue(fallback.contains("72 BPM"))
        assertFalse(fallback.contains("VFC de 0"))
        assertFalse(fallback.contains("0 passos"))
    }

    @Test fun empty_clinical_returns_android_copy() {
        val sport = metric(steps = 10)
        assertNull(buildInsightPrompt(listOf(sport), UserProfileEntity(), now))
        assertEquals(EMPTY_CLINICAL_INSIGHT, fallbackFrom(listOf(sport), now))
    }

    @Test fun fallback_copy_is_frozen_when_all_present() {
        val text = fallbackInsight(70, 70, 9000, 40)
        assertTrue(text.contains("70 BPM"))
        assertTrue(text.contains("excelente recuperação"))
        assertTrue(text.contains("pontuação de VFC de 70"))
        assertTrue(text.contains("9000 passos máximos"))
    }

    @Test fun fallback_omits_zero_because_missing() {
        val text = fallbackInsight(70, 0, 0, 0)
        assertTrue(text.contains("70 BPM"))
        assertFalse(text.contains("VFC de 0"))
        assertFalse(text.contains("0 passos"))
        assertFalse(text.contains("0 minutos"))
    }
}
