package com.example.data.ingest

import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSyncIsolationTest {
    private fun reading(hr: Int) = HBandTelemetry(
        deviceId = "TEST-WATCH", deviceModel = "Test", timestamp = "2026-09-25T12:00:00Z",
        heartRate = hr, bloodPressure = BloodPressure(), spO2 = 0,
        temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
        hrvScore = 0, sleepSummary = SleepSummary(), isRealSensorData = true,
    )

    @Test fun capture_continues_while_network_is_waiting() = runTest {
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        val saved = mutableListOf<Int>()
        val release = CompletableDeferred<Unit>()
        var sends = 0
        val recorder = backgroundScope.recordLiveReadingsWithSync(readings, { true }, {
            saved += it.heartRate
        }, {
            sends++
            release.await()
        }, { throw it }, { throw it })
        runCurrent()
        readings.value = reading(72); runCurrent()
        readings.value = reading(76); runCurrent()
        readings.value = reading(80); runCurrent()
        try {
            assertEquals(1, sends)
            assertEquals(listOf(72, 76, 80), saved)
        } finally { recorder.cancelAndJoin() }
    }

    @Test fun many_saved_readings_coalesce_into_one_waiting_request() = runTest {
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        val release = CompletableDeferred<Unit>()
        var saved = 0
        var sends = 0
        var active = 0
        var maxActive = 0
        val recorder = backgroundScope.recordLiveReadingsWithSync(readings, { true }, { saved++ }, {
            sends++; active++; maxActive = maxOf(active, maxActive)
            try { if (sends == 1) release.await() } finally { active-- }
        }, { throw it }, { throw it })
        runCurrent()
        repeat(20) { readings.value = reading(70 + it); runCurrent() }
        assertEquals(20, saved); assertEquals(1, sends)
        release.complete(Unit); runCurrent()
        assertEquals(2, sends); assertEquals(1, maxActive)
        recorder.cancelAndJoin()
    }

    @Test fun failed_local_save_does_not_request_sync_and_next_sample_can_recover() = runTest {
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        var saves = 0
        var sends = 0
        var errors = 0
        val recorder = backgroundScope.recordLiveReadingsWithSync(readings, { true }, {
            if (++saves == 1) error("Synthetic storage failure")
        }, { sends++ }, { errors++ }, { throw it })
        runCurrent()
        readings.value = reading(72); runCurrent()
        assertEquals(0, sends)
        readings.value = reading(72).copy(timestamp = "2026-09-25T12:00:01Z"); runCurrent()
        assertEquals(2, saves); assertEquals(1, sends); assertEquals(1, errors)
        recorder.cancelAndJoin()
    }

    @Test fun sync_failure_does_not_reopen_capture_dedup_or_retry_without_new_signal() = runTest {
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        var saves = 0
        var sends = 0
        var syncErrors = 0
        val recorder = backgroundScope.recordLiveReadingsWithSync(readings, { true }, { saves++ }, {
            sends++; error("Synthetic processing failure")
        }, { throw it }, { syncErrors++ })
        runCurrent()
        readings.value = reading(72); runCurrent()
        readings.value = reading(72).copy(timestamp = "2026-09-25T12:00:01Z"); runCurrent()
        assertEquals(1, saves); assertEquals(1, sends); assertEquals(1, syncErrors)
        readings.value = reading(76); runCurrent()
        assertEquals(2, saves); assertEquals(2, sends)
        recorder.cancelAndJoin()
    }

    @Test fun cancelling_owner_stops_capture_and_inflight_sync() = runTest {
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        var saved = 0
        var stopped = false
        val recorder = backgroundScope.recordLiveReadingsWithSync(readings, { true }, { saved++ }, {
            try { awaitCancellation() } finally { stopped = true }
        }, { throw it }, { throw it })
        runCurrent()
        readings.value = reading(72); runCurrent()
        recorder.cancelAndJoin()
        readings.value = reading(76); runCurrent()
        assertTrue(stopped); assertEquals(1, saved)
    }

    @Test fun processor_cancellation_cancels_owner_without_storage_error() = runTest {
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        var saved = 0
        var errors = 0
        val recorder = backgroundScope.recordLiveReadingsWithSync(readings, { true }, { saved++ }, {
            throw CancellationException("Synthetic shutdown")
        }, { errors++ }, { errors++ })
        runCurrent()
        readings.value = reading(72); runCurrent()
        readings.value = reading(76); runCurrent()
        assertTrue(recorder.isCancelled); assertEquals(0, errors); assertEquals(1, saved)
    }

    @Test fun normal_flow_completion_stops_processor_without_cancelling_owner() = runTest {
        var saved = 0
        val recorder = backgroundScope.recordLiveReadingsWithSync(
            kotlinx.coroutines.flow.flowOf(reading(72)), { true }, { saved++ }, {}, { throw it }, { throw it })
        runCurrent()
        recorder.join()
        assertTrue(recorder.isCompleted); assertFalse(recorder.isCancelled); assertEquals(1, saved)
    }
}
