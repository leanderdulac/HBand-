package com.example.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import com.example.data.local.HBandSensorMetricEntity
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientInsightReviewTest {
    private fun review(scope: CoroutineScope, enabled: Boolean, generate: suspend () -> String) =
        PatientInsightReview(scope, enabled, flowOf(emptyList())) { generate() }

    @Test fun explicit_request_waits_for_cold_storage_source_instead_of_initial_empty_sentinel() = runTest {
        val row = HBandSensorMetricEntity(deviceId = "SYNTHETIC", timestamp = "2026-09-28T00:00:00Z",
            timestampMillis = 1790553600000L, heartRate = 72, systolicBp = 0, diastolicBp = 0,
            spO2 = 0, temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
            hrvScore = 0, deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0)
        val finishRead = CompletableDeferred<List<HBandSensorMetricEntity>>()
        var reads = 0
        val received = mutableListOf<List<HBandSensorMetricEntity>>()
        val source = flow { reads++; emit(finishRead.await()) }
        val review = PatientInsightReview(backgroundScope, true, source) { received += it; "SYNTHETIC" }
        runCurrent(); assertEquals(0, reads)
        review.request(); runCurrent(); review.request(); runCurrent()
        assertEquals(1, reads); assertTrue(received.isEmpty()); assertTrue(review.state.value.isLoading)
        finishRead.complete(listOf(row)); runCurrent()
        assertEquals(listOf(listOf(row)), received)
        assertEquals(InsightReviewState("SYNTHETIC"), review.state.value)
    }

    @Test fun storage_failure_does_not_generate_from_unknown_data_and_allows_explicit_retry() = runTest {
        var reads = 0
        var generations = 0
        val source = flow<List<HBandSensorMetricEntity>> {
            if (++reads == 1) error("synthetic-private-storage-error")
            emit(emptyList())
        }
        val review = PatientInsightReview(backgroundScope, true, source) {
            generations++; assertTrue(it.isEmpty()); "CONFIRMED EMPTY SOURCE"
        }
        review.request(); runCurrent()
        assertEquals(0, generations); assertFalse(review.state.value.isLoading)
        assertFalse(review.state.value.text.contains("synthetic-private-storage-error"))
        review.request(); runCurrent()
        assertEquals(2, reads); assertEquals(1, generations)
        assertEquals(InsightReviewState("CONFIRMED EMPTY SOURCE"), review.state.value)
    }

    @Test fun construction_and_elapsed_time_never_generate_automatically() = runTest {
        var calls = 0
        val review = review(backgroundScope, true) { calls++; "SYNTHETIC" }
        runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(0, calls)
        assertEquals(InsightReviewState(), review.state.value)
    }

    @Test fun disabled_review_never_calls_generator_even_when_requested() = runTest {
        var calls = 0
        var reads = 0
        val metrics = flow<List<HBandSensorMetricEntity>> { reads++; emit(emptyList()) }
        val review = PatientInsightReview(backgroundScope, false, metrics) { calls++; "SYNTHETIC" }
        repeat(3) { review.request() }; runCurrent()
        assertEquals(0, calls); assertEquals(0, reads)
        assertEquals(InsightReviewState(), review.state.value)
    }

    @Test fun repeated_taps_create_only_one_pending_request() = runTest {
        var calls = 0
        val finish = CompletableDeferred<String>()
        val review = review(backgroundScope, true) { calls++; finish.await() }
        review.request(); assertTrue(review.state.value.isLoading)
        review.request(); runCurrent(); review.request(); runCurrent()
        assertEquals(1, calls)
        finish.complete("SYNTHETIC RESULT"); runCurrent()
        assertEquals(InsightReviewState("SYNTHETIC RESULT"), review.state.value)
    }

    @Test fun failure_unlocks_explicit_retry_without_exposing_details_or_replaying() = runTest {
        var calls = 0
        val review = review(backgroundScope, true) {
            if (++calls == 1) error("synthetic-private-detail")
            "SYNTHETIC RETRY"
        }
        review.request(); runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, calls); assertFalse(review.state.value.isLoading)
        assertFalse(review.state.value.text.contains("synthetic-private-detail"))
        review.request(); runCurrent()
        assertEquals(2, calls); assertEquals(InsightReviewState("SYNTHETIC RETRY"), review.state.value)
    }

    @Test fun cancellation_before_start_does_not_leave_loading_stuck() = runTest {
        val job = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        var calls = 0
        val review = review(scope, true) { calls++; "SYNTHETIC" }
        job.cancel(); review.request(); runCurrent()
        assertEquals(0, calls); assertEquals(InsightReviewState(), review.state.value)
    }

    @Test fun cancellation_during_generation_does_not_report_a_result_or_retry() = runTest {
        val job = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        var calls = 0
        val review = review(scope, true) { calls++; awaitCancellation() }
        review.request(); runCurrent(); job.cancel(); runCurrent()
        assertEquals(1, calls); assertEquals(InsightReviewState(), review.state.value)
    }

    @Test fun late_result_after_clear_cannot_restore_text_or_unlock_a_new_request() = runTest {
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        var calls = 0
        val review = review(backgroundScope, true) {
            if (++calls == 1) withContext(NonCancellable) { first.await() } else second.await()
        }
        review.request(); runCurrent(); review.clear()
        assertEquals(InsightReviewState(), review.state.value)
        review.request(); runCurrent()
        first.complete("STALE"); runCurrent()
        assertEquals(InsightReviewState(isLoading = true), review.state.value)
        second.complete("CURRENT"); runCurrent()
        assertEquals(InsightReviewState("CURRENT"), review.state.value)
    }
}
