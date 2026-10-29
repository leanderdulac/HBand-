package com.example.data.hband

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** No radio is started: exercise the actual session callbacks and Android deadline scheduler. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class BleScanSessionTest {
    private var allowed = true
    private var startFailure = false
    private var stopFailure = false
    private var synchronousFailure = false
    private var unavailableReason: BleScanFailure? = null
    private val started = mutableListOf<ScanCallback>()
    private val stopped = mutableListOf<ScanCallback>()
    private val received = mutableListOf<ScanResult>()
    private val scanning = mutableListOf<Boolean>()
    private val failures = mutableListOf<BleScanFailure>()
    private val session = BleScanSession(
        handler = Handler(Looper.getMainLooper()),
        canScan = { allowed },
        startScan = {
            started += it
            if (startFailure) throw SecurityException("permission lost at start")
            unavailableReason?.let { reason -> throw BleScanUnavailableException(reason) }
            if (synchronousFailure) it.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        },
        stopScan = {
            stopped += it
            if (stopFailure) throw SecurityException("permission lost at stop")
        },
        onResults = { received += it },
        onScanningChanged = { scanning += it },
        onFailure = { failures += it },
    )

    private fun advance(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    private fun result(): ScanResult {
        val device = RuntimeEnvironment.getApplication().getSystemService(BluetoothManager::class.java)
            .adapter.getRemoteDevice("00:11:22:33:44:55")
        return ScanResult(device, 0, 1, 0, 0, 0, -50, 0, null, 1L)
    }

    @Test fun restarting_gets_a_full_deadline_and_ignores_old_callbacks() {
        session.start()
        val old = started.single()
        advance(6)
        session.stop()
        session.start()
        val current = started.last()
        val observation = result()
        old.onScanResult(0, observation)
        old.onBatchScanResults(mutableListOf(observation))
        old.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        advance(6) // The old deadline would have stopped the new scan here.
        assertEquals(listOf(true, false, true), scanning)
        assertEquals(listOf(old), stopped)
        assertTrue(received.isEmpty())
        assertTrue(failures.isEmpty())
        current.onScanResult(0, observation)
        current.onBatchScanResults(mutableListOf(observation))
        assertEquals(listOf(observation, observation), received)
        advance(6)
        assertEquals(listOf(old, current), stopped)
        assertEquals(listOf(true, false, true, false), scanning)
        current.onScanResult(0, observation)
        assertEquals(2, received.size)
    }

    @Test fun repeated_start_and_stop_do_not_duplicate_platform_operations() {
        session.start()
        session.start()
        assertEquals(1, started.size)
        session.stop()
        session.stop()
        advance(20)
        assertEquals(1, stopped.size)
        assertEquals(listOf(true, false), scanning)
    }

    @Test fun failed_scan_cancels_its_deadline_before_retry() {
        session.start()
        advance(6)
        started.single().onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        assertEquals(listOf(true, false), scanning)
        assertEquals(1, failures.size)
        session.start()
        advance(6)
        assertTrue(scanning.last())
        advance(6)
        assertFalse(scanning.last())
        assertEquals(2, stopped.size)
    }

    @Test fun missing_permission_or_failure_during_start_allows_a_later_retry() {
        allowed = false
        session.start()
        assertTrue(started.isEmpty())
        assertFalse(scanning.last())
        allowed = true
        startFailure = true
        session.start()
        assertFalse(scanning.last())
        startFailure = false
        session.start()
        assertTrue(scanning.last())
        advance(12)
        assertFalse(scanning.last())
        assertEquals(2, failures.size)
        assertEquals(listOf(BleScanFailure.PERMISSION_REQUIRED, BleScanFailure.PERMISSION_REQUIRED), failures)
    }

    @Test fun permission_revoked_during_results_closes_session_even_if_stop_fails() {
        session.start()
        val old = started.single()
        allowed = false
        stopFailure = true
        old.onScanResult(0, result())
        assertFalse(scanning.last())
        assertTrue(received.isEmpty())
        allowed = true
        stopFailure = false
        session.start()
        old.onScanResult(0, result())
        assertTrue(received.isEmpty())
        started.last().onScanResult(0, result())
        assertEquals(1, received.size)
    }

    @Test fun synchronous_failure_does_not_leave_a_deadline_for_the_next_scan() {
        synchronousFailure = true
        session.start()
        assertEquals(listOf(true, false), scanning)
        advance(6)
        synchronousFailure = false
        session.start()
        advance(6)
        assertTrue(scanning.last())
        advance(6)
        assertFalse(scanning.last())
        assertEquals(2, stopped.size)
    }

    @Test fun queued_background_result_cannot_repopulate_a_stopped_scan() {
        session.start()
        val callback = started.single()
        val observation = result()
        val worker = Thread { callback.onScanResult(0, observation) }
        worker.start()
        worker.join()
        assertTrue(received.isEmpty())
        session.stop()
        session.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(received.isEmpty())
        assertTrue(scanning.last())
        started.last().onScanResult(0, observation)
        assertEquals(listOf(observation), received)
    }

    @Test fun bluetooth_off_and_unavailable_scanner_keep_their_observed_reason() {
        unavailableReason = BleScanFailure.BLUETOOTH_OFF
        session.start()
        assertFalse(scanning.last())
        assertEquals(BleScanFailure.BLUETOOTH_OFF, failures.last())
        unavailableReason = BleScanFailure.SCANNER_UNAVAILABLE
        session.start()
        assertFalse(scanning.last())
        assertEquals(BleScanFailure.SCANNER_UNAVAILABLE, failures.last())
        unavailableReason = null
        session.start()
        started.last().onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
        assertEquals(BleScanFailure.SCAN_FAILED, failures.last())
    }
}
