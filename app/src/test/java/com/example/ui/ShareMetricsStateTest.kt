package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareMetricsStateTest {
    private val row = HBandSensorMetricEntity(
        id = 91, deviceId = "synthetic", timestamp = "saved", timestampMillis = 1234,
        heartRate = 72, systolicBp = 0, diastolicBp = 0, spO2 = 97, temperatureCelsius = 0f,
        steps = 120, calories = 5f, distanceMeters = 3f, hrvScore = 0,
        deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
    )

    @Test fun pending_response_is_distinct_from_confirmed_empty_and_rows_are_preserved() = runTest {
        val source = MutableSharedFlow<List<HBandSensorMetricEntity>>()
        val state = source.shareMetricsState(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertNull(state.value)
        source.emit(emptyList())
        runCurrent()
        assertEquals(emptyList<HBandSensorMetricEntity>(), state.value)
        val result = listOf(row)
        source.emit(result)
        runCurrent()
        assertSame(result, state.value)
        assertEquals(91L, state.value!!.single().id)
    }

    @Test fun last_observer_leaving_discards_replay_and_resume_waits_for_new_response() = runTest {
        val source = MutableSharedFlow<List<HBandSensorMetricEntity>>()
        val state = source.shareMetricsState(backgroundScope)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        source.emit(listOf(row))
        runCurrent()
        assertEquals(listOf(row), state.value)
        first.cancel()
        runCurrent()
        assertNull(state.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertNull(state.value)
        source.emit(emptyList())
        runCurrent()
        assertEquals(emptyList<HBandSensorMetricEntity>(), state.value)
    }

    @Test fun source_is_not_observed_without_a_consumer_and_stops_after_last_consumer() = runTest {
        var starts = 0
        var stops = 0
        val source = flow {
            starts++
            try { emit(listOf(row)); awaitCancellation() } finally { stops++ }
        }
        val state = source.shareMetricsState(backgroundScope)
        runCurrent()
        assertEquals(0, starts)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals(1, starts)
        first.cancel()
        runCurrent()
        assertEquals(0, stops)
        assertEquals(listOf(row), state.value)
        second.cancel()
        runCurrent()
        assertEquals(1, stops)
        assertNull(state.value)
    }
}
