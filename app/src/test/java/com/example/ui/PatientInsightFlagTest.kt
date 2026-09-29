package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI_INSIGHT_ENABLED gating: off keeps the PR #6 pilot guard, on restores automatic summaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientInsightFlagTest {
    private val debounce = AUTOMATIC_INSIGHT_DEBOUNCE_MS
    private val interval = AUTOMATIC_INSIGHT_MIN_INTERVAL_MS

    private fun row(millis: Long, heartRate: Int = 72) = HBandSensorMetricEntity(
        deviceId = "SYNTHETIC", timestamp = "2026-09-28T00:00:00Z", timestampMillis = millis,
        heartRate = heartRate, systolicBp = 0, diastolicBp = 0, spO2 = 0, temperatureCelsius = 0f,
        steps = 120, calories = 0f, distanceMeters = 0f, hrvScore = 0, deepSleepMinutes = 0,
        lightSleepMinutes = 0, awakeMinutes = 0,
    )

    private fun TestScope.review(
        scope: CoroutineScope,
        metrics: Flow<List<HBandSensorMetricEntity>>,
        policy: InsightPolicy,
        generate: suspend (List<HBandSensorMetricEntity>) -> String,
    ) = PatientInsightReview(
        scope, policy.manualRequests, metrics, automatic = policy.automatic,
        now = { testScheduler.currentTime }, generate = generate,
    )

    @Test fun flag_off_keeps_pilot_guard_in_debug_and_release() {
        assertEquals(InsightPolicy(manualRequests = true, automatic = false, showInsight = false),
            insightPolicy(debug = true, aiInsightEnabled = false))
        assertEquals(InsightPolicy(manualRequests = false, automatic = false, showInsight = false),
            insightPolicy(debug = false, aiInsightEnabled = false))
    }

    @Test fun flag_on_enables_automatic_summary_in_debug_and_release() {
        val on = InsightPolicy(manualRequests = true, automatic = true, showInsight = true)
        assertEquals(on, insightPolicy(debug = true, aiInsightEnabled = true))
        assertEquals(on, insightPolicy(debug = false, aiInsightEnabled = true))
    }

    @Test fun flag_off_never_generates_automatically_even_with_new_readings() = runTest {
        for (debug in listOf(true, false)) {
            var calls = 0
            var reads = 0
            val source = MutableStateFlow(listOf(row(1_000)))
            val metrics = flow { reads++; source.collect { emit(it) } }
            val review = review(backgroundScope, metrics, insightPolicy(debug, false)) { calls++; "SYNTHETIC" }
            runCurrent(); advanceTimeBy(debounce + 1); runCurrent()
            source.value = listOf(row(1_000), row(2_000)); advanceTimeBy(2 * interval); runCurrent()
            assertEquals(0, calls); assertEquals(0, reads)
            assertEquals(InsightReviewState(), review.state.value)
        }
    }

    @Test fun flag_off_release_rejects_manual_request_and_debug_allows_it() = runTest {
        var calls = 0
        val metrics = MutableStateFlow(listOf(row(1_000)))
        review(backgroundScope, metrics, insightPolicy(debug = false, aiInsightEnabled = false)) { calls++; "X" }
            .request()
        runCurrent(); assertEquals(0, calls)
        review(backgroundScope, metrics, insightPolicy(debug = true, aiInsightEnabled = false)) { calls++; "X" }
            .request()
        runCurrent(); assertEquals(1, calls)
    }

    @Test fun flag_on_generates_once_on_startup_after_readings_settle() = runTest {
        var calls = 0
        val metrics = MutableStateFlow(listOf(row(1_000)))
        val review = review(backgroundScope, metrics, insightPolicy(debug = false, aiInsightEnabled = true)) {
            calls++; "SYNTHETIC INSIGHT"
        }
        runCurrent(); advanceTimeBy(debounce - 1); runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(2); runCurrent()
        assertEquals(1, calls)
        assertEquals(InsightReviewState("SYNTHETIC INSIGHT"), review.state.value)
        metrics.value = listOf(row(1_000)) // same readings re-emitted
        advanceTimeBy(2 * interval); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun flag_on_collapses_bursts_and_throttles_new_readings_to_latest_data() = runTest {
        val received = mutableListOf<Int>()
        val metrics = MutableStateFlow(listOf(row(1_000)))
        review(backgroundScope, metrics, insightPolicy(debug = true, aiInsightEnabled = true)) {
            received += it.size; "SYNTHETIC"
        }
        runCurrent()
        metrics.value = listOf(row(1_000), row(2_000)); advanceTimeBy(debounce / 2); runCurrent()
        metrics.value = listOf(row(1_000), row(2_000), row(3_000)); advanceTimeBy(debounce + 1); runCurrent()
        assertEquals(listOf(3), received) // burst -> one call with the latest readings

        metrics.value = metrics.value + row(4_000); runCurrent()
        metrics.value = metrics.value + row(5_000); advanceTimeBy(debounce + 1); runCurrent()
        assertEquals(listOf(3), received) // still inside the minimum interval
        advanceTimeBy(interval); runCurrent()
        assertEquals(listOf(3, 5), received) // exactly one deferred call, latest data
        advanceTimeBy(2 * interval); runCurrent()
        assertEquals(listOf(3, 5), received)
    }

    @Test fun flag_on_does_not_overlap_a_pending_request() = runTest {
        var calls = 0
        val finish = CompletableDeferred<String>()
        val metrics = MutableStateFlow(listOf(row(1_000)))
        val review = review(backgroundScope, metrics, insightPolicy(debug = false, aiInsightEnabled = true)) {
            calls++; finish.await()
        }
        runCurrent(); advanceTimeBy(debounce + 1); runCurrent()
        assertEquals(1, calls); assertTrue(review.state.value.isLoading)
        review.request(); runCurrent()
        assertEquals(1, calls)
        finish.complete("DONE"); runCurrent()
        assertEquals(InsightReviewState("DONE"), review.state.value)
    }

    @Test fun flag_on_ignores_empty_or_activity_only_readings() = runTest {
        var calls = 0
        val metrics = MutableStateFlow(emptyList<HBandSensorMetricEntity>())
        review(backgroundScope, metrics, insightPolicy(debug = false, aiInsightEnabled = true)) { calls++; "X" }
        runCurrent(); advanceTimeBy(debounce + 1); runCurrent()
        metrics.value = listOf(row(1_000, heartRate = 0)); advanceTimeBy(debounce + 1); runCurrent()
        assertEquals(0, calls)
        metrics.value = listOf(row(1_000, heartRate = 0), row(2_000)); advanceTimeBy(debounce + 1); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun automatic_flag_without_enabled_review_never_generates() = runTest {
        var calls = 0
        val review = PatientInsightReview(backgroundScope, false, MutableStateFlow(listOf(row(1_000))),
            automatic = true, now = { testScheduler.currentTime }) { calls++; "X" }
        runCurrent(); advanceTimeBy(2 * interval); runCurrent()
        assertEquals(0, calls); assertEquals(InsightReviewState(), review.state.value)
    }

    @Test fun flag_on_storage_failure_does_not_generate_or_crash_and_manual_retry_works() = runTest {
        var reads = 0
        var calls = 0
        val metrics = flow {
            if (++reads == 1) error("synthetic-private-storage-error")
            emit(listOf(row(1_000)))
        }
        val review = review(backgroundScope, metrics, insightPolicy(debug = false, aiInsightEnabled = true)) {
            calls++; "MANUAL"
        }
        runCurrent(); advanceTimeBy(2 * interval); runCurrent()
        assertEquals(0, calls); assertFalse(review.state.value.isLoading)
        review.request(); runCurrent()
        assertEquals(1, calls); assertEquals(InsightReviewState("MANUAL"), review.state.value)
    }
}
