package com.example.data.ingest

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveReadingRecorderTest {
    private fun reading(hr: Int = 72, real: Boolean = true) = HBandTelemetry(
        deviceId = "TEST-WATCH", deviceModel = "Test", timestamp = "2026-09-24T12:00:00Z",
        heartRate = hr, bloodPressure = BloodPressure(0, 0), spO2 = 0,
        temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
        hrvScore = 0, sleepSummary = SleepSummary(0, 0, 0), isRealSensorData = real,
    )

    @Test fun screen_observer_ending_does_not_stop_recording_and_reopening_does_not_duplicate() = runTest {
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        val saved = mutableListOf<HBandTelemetry>()
        backgroundScope.recordLiveReadings(readings, { true }, { saved += it }, { throw it })
        val screen = backgroundScope.launch { readings.collect {} }
        runCurrent()
        readings.value = reading(72)
        runCurrent()
        screen.cancelAndJoin()
        readings.value = reading(76)
        runCurrent()
        assertEquals(listOf(72, 76), saved.map { it.heartRate })
        val reopenedScreen = backgroundScope.launch { readings.collect {} }
        runCurrent()
        assertEquals(2, saved.size)
        readings.value = reading(80)
        runCurrent()
        assertEquals(listOf(72, 76, 80), saved.map { it.heartRate })
        reopenedScreen.cancelAndJoin()
    }

    @Test fun saving_toggle_is_checked_for_each_reading_without_replaying_ignored_data() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        var enabled = false
        val saved = mutableListOf<HBandTelemetry>()
        backgroundScope.recordLiveReadings(readings, { enabled }, { saved += it }, { throw it })
        runCurrent()
        readings.emit(reading(72))
        enabled = true
        runCurrent()
        assertTrue(saved.isEmpty())
        readings.emit(reading(76))
        runCurrent()
        enabled = false
        readings.emit(reading(80))
        runCurrent()
        assertEquals(listOf(76), saved.map { it.heartRate })
    }

    @Test fun null_synthetic_invalid_and_repeated_readings_keep_existing_ingest_rules() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        val saved = mutableListOf<HBandTelemetry>()
        backgroundScope.recordLiveReadings(readings, { true }, { saved += it }, { throw it })
        runCurrent()
        for (value in listOf(null, reading(real = false), reading(0), reading(72), reading(72), reading(73))) {
            readings.emit(value)
            runCurrent()
        }
        assertEquals(listOf(72, 73), saved.map { it.heartRate })
    }

    @Test fun one_failed_save_is_reported_and_does_not_end_the_next_reading() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        val errors = mutableListOf<Exception>()
        val saved = mutableListOf<HBandTelemetry>()
        val failure = IllegalStateException("Test storage unavailable")
        backgroundScope.recordLiveReadings(readings, { true }, {
            if (it.heartRate == 72) throw failure
            saved += it
        }, errors::add)
        runCurrent()
        readings.emit(reading(72))
        runCurrent()
        readings.emit(reading(76))
        runCurrent()
        assertEquals(listOf(failure), errors)
        assertEquals(listOf(76), saved.map { it.heartRate })
    }

    @Test fun cancellation_during_save_is_not_a_storage_failure_or_an_automatic_restart() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        var attempts = 0
        val errors = mutableListOf<Exception>()
        val recorder = backgroundScope.recordLiveReadings(readings, { true }, {
            attempts++
            awaitCancellation()
        }, errors::add)
        runCurrent()
        readings.emit(reading())
        runCurrent()
        recorder.cancelAndJoin()
        readings.emit(reading(80))
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(errors.isEmpty())
        assertTrue(recorder.isCancelled)
    }

    @Test fun save_cancellation_propagates_without_accepting_later_samples() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        val errors = mutableListOf<Exception>()
        val recorder = backgroundScope.recordLiveReadings(readings, { true }, {
            throw CancellationException("Test shutdown")
        }, errors::add)
        runCurrent()
        readings.emit(reading())
        runCurrent()
        assertTrue(recorder.isCancelled)
        assertTrue(errors.isEmpty())
    }

    @Test fun failed_save_does_not_suppress_next_received_reading_with_same_values() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        val saved = mutableListOf<HBandTelemetry>()
        val errors = mutableListOf<Exception>()
        var attempts = 0
        backgroundScope.recordLiveReadings(readings, { true }, {
            attempts++
            if (attempts == 1) error("Synthetic write failure")
            saved += it
        }, errors::add)
        runCurrent()
        val next = reading().copy(timestamp = "2026-09-24T12:00:01Z")
        for (value in listOf(reading(), next, next.copy(timestamp = "2026-09-24T12:00:02Z"))) {
            readings.emit(value)
            runCurrent()
        }
        assertEquals(2, attempts)
        assertEquals(1, errors.size)
        assertEquals(listOf(next), saved)
    }

    @Test fun failed_different_reading_keeps_last_successful_signature() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        val saved = mutableListOf<HBandTelemetry>()
        val errors = mutableListOf<Exception>()
        var failuresRemaining = 1
        backgroundScope.recordLiveReadings(readings, { true }, {
            if (it.heartRate == 80 && failuresRemaining-- > 0) error("Synthetic failure")
            saved += it
        }, errors::add)
        runCurrent()
        for (value in listOf(reading(72), reading(80), reading(72), reading(80))) {
            readings.emit(value)
            runCurrent()
        }
        assertEquals(listOf(72, 80), saved.map { it.heartRate })
        assertEquals(1, errors.size)
    }

    @Test fun repeated_storage_failures_do_not_claim_capture_or_stop_collection() = runTest {
        val readings = MutableSharedFlow<HBandTelemetry?>()
        val saved = mutableListOf<HBandTelemetry>()
        val errors = mutableListOf<Exception>()
        var attempts = 0
        backgroundScope.recordLiveReadings(readings, { true }, {
            attempts++
            if (attempts <= 2) error("Synthetic unavailable store")
            saved += it
        }, errors::add)
        runCurrent()
        repeat(4) {
            readings.emit(reading())
            runCurrent()
        }
        assertEquals(3, attempts)
        assertEquals(2, errors.size)
        assertEquals(1, saved.size)
    }
}
