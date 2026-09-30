package com.example.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Timestamp and failure metadata behind the highlighted card ("Gerado às HH:mm", error state). */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientInsightMetaTest {
    @Test fun success_records_generation_time_and_clear_resets_it() = runTest {
        val finish = CompletableDeferred<String>()
        val review = PatientInsightReview(backgroundScope, true, flowOf(emptyList()),
            now = { testScheduler.currentTime }) { finish.await() }
        assertEquals(InsightMeta(), review.meta.value)
        advanceTimeBy(5_000); review.request(); runCurrent()
        assertEquals(InsightMeta(), review.meta.value)
        advanceTimeBy(2_000); finish.complete("SYNTHETIC"); runCurrent()
        assertEquals(InsightMeta(generatedAtMillis = 7_000), review.meta.value)
        review.clear()
        assertEquals(InsightMeta(), review.meta.value)
    }

    @Test fun failure_is_flagged_without_timestamp_and_retry_clears_it() = runTest {
        var calls = 0
        val review = PatientInsightReview(backgroundScope, true, flowOf(emptyList()),
            now = { testScheduler.currentTime }) {
            if (++calls == 1) error("synthetic-private-detail")
            "SYNTHETIC RETRY"
        }
        review.request(); runCurrent()
        assertEquals(InsightMeta(failed = true), review.meta.value)
        advanceTimeBy(1_000); review.request(); runCurrent()
        assertEquals(InsightMeta(generatedAtMillis = 1_000), review.meta.value)
        assertEquals(InsightReviewState("SYNTHETIC RETRY"), review.state.value)
    }
}
