package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.shouldGenerateAiInsight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Consent gate on top of AI_INSIGHT_ENABLED: no Gemini call until the patient authorizes. */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientInsightConsentTest {
    private val debounce = AUTOMATIC_INSIGHT_DEBOUNCE_MS
    private val interval = AUTOMATIC_INSIGHT_MIN_INTERVAL_MS
    private val policyOn = insightPolicy(debug = false, aiInsightEnabled = true)

    private fun row(millis: Long, heartRate: Int = 72) = HBandSensorMetricEntity(
        deviceId = "SYNTHETIC", timestamp = "2026-09-28T00:00:00Z", timestampMillis = millis,
        heartRate = heartRate, systolicBp = 0, diastolicBp = 0, spO2 = 0, temperatureCelsius = 0f,
        steps = 120, calories = 0f, distanceMeters = 0f, hrvScore = 0, deepSleepMinutes = 0,
        lightSleepMinutes = 0, awakeMinutes = 0,
    )

    @Test fun flag_on_without_consent_never_calls_generate_automatically_or_manually() = runTest {
        var calls = 0
        var consented = false
        val metrics = MutableStateFlow(listOf(row(1_000)))
        val review = PatientInsightReview(
            backgroundScope, policyOn.manualRequests, metrics, automatic = policyOn.automatic,
            now = { testScheduler.currentTime }, consented = { consented },
        ) { metricsList ->
            check(shouldGenerateAiInsight(policyOn.showInsight, consented)) { "Gemini must not run without consent" }
            calls++
            "LEAK"
        }
        runCurrent(); advanceTimeBy(debounce + 1); runCurrent()
        review.request(); runCurrent()
        metrics.value = listOf(row(1_000), row(2_000))
        advanceTimeBy(2 * interval); runCurrent()
        assertEquals(0, calls)
        assertEquals(InsightReviewState(), review.state.value)
        assertFalse(review.state.value.isLoading)
    }

    @Test fun granting_consent_allows_one_generate_and_keeps_the_text() = runTest {
        var calls = 0
        var consented = false
        val metrics = MutableStateFlow(listOf(row(1_000)))
        val review = PatientInsightReview(
            backgroundScope, policyOn.manualRequests, metrics, automatic = policyOn.automatic,
            now = { testScheduler.currentTime }, consented = { consented },
        ) { calls++; "RESUMO AUTORIZADO" }
        runCurrent(); advanceTimeBy(debounce + 1); runCurrent()
        assertEquals(0, calls)
        consented = true
        review.request(); runCurrent()
        assertEquals(1, calls)
        assertEquals(InsightReviewState("RESUMO AUTORIZADO"), review.state.value)
    }

    @Test fun withdraw_clears_cached_text_and_blocks_further_calls() = runTest {
        var calls = 0
        var consented = true
        val metrics = MutableStateFlow(listOf(row(1_000)))
        val review = PatientInsightReview(
            backgroundScope, policyOn.manualRequests, metrics, automatic = policyOn.automatic,
            now = { testScheduler.currentTime }, consented = { consented },
        ) { calls++; "CACHE" }
        runCurrent(); advanceTimeBy(debounce + 1); runCurrent()
        assertEquals(1, calls)
        assertEquals("CACHE", review.state.value.text)

        consented = false
        review.clear()
        runCurrent()
        assertEquals(InsightReviewState(), review.state.value)
        assertEquals(InsightMeta(), review.meta.value)

        review.request(); runCurrent()
        metrics.value = listOf(row(1_000), row(2_000))
        advanceTimeBy(2 * interval); runCurrent()
        assertEquals(1, calls)
        assertTrue(review.state.value.text.isEmpty())
    }

    @Test fun generate_lambda_safety_net_does_not_count_as_a_gemini_call_without_consent() = runTest {
        var geminiCalls = 0
        var consented = false
        val metrics = MutableStateFlow(listOf(row(1_000)))
        val review = PatientInsightReview(
            backgroundScope, true, metrics, automatic = false,
            now = { testScheduler.currentTime }, consented = { consented },
        ) { records ->
            if (!shouldGenerateAiInsight(true, consented)) return@PatientInsightReview ""
            geminiCalls++
            "X"
        }
        review.request(); runCurrent()
        assertEquals(0, geminiCalls)
        assertEquals(InsightReviewState(), review.state.value)
    }
}
