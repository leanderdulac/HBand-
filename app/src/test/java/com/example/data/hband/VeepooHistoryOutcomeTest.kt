package com.example.data.hband

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.ISleepDataListener
import com.veepoo.protocol.model.settings.ReadSleepSetting
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog

/** Executes the history client; only SDK radio callbacks are replaced. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class,
    instrumentedPackages = ["com.veepoo.protocol.VPOperateManager"],
    shadows = [VeepooHistoryOutcomeTest.HistoryBoundary::class])
@LooperMode(LooperMode.Mode.PAUSED)
class VeepooHistoryOutcomeTest {
    // A fresh SDK boundary avoids sharing its singleton/shadow with other connection tests.
    private val sdk = org.robolectric.shadow.api.Shadow.newInstanceOf(VPOperateManager::class.java)
    private val sync = VeepooHistorySync(sdk, Handler(Looper.getMainLooper()))
    private val sleepOnly = DeviceCapabilities(probed = true, historyDays = 0)

    @Before fun resetBoundary() {
        assertTrue(org.robolectric.shadow.api.Shadow.extract<Any>(sdk) is HistoryBoundary)
        HistoryBoundary.calls = 0
        HistoryBoundary.read = { it.onReadSleepComplete() }
    }

    @Test fun unverified_capabilities_are_not_an_empty_successful_read() = runTest {
        val updates = mutableListOf<HistorySyncUiState>()
        val pull = sync.pullHistory(DeviceCapabilities(), "test", "test", updates::add)
        assertEquals("unavailable", pull.state.phase)
        assertNull(pull.state.lastCompletedAtMs)
        assertEquals(listOf(pull.state), updates)
        assertEquals(0, HistoryBoundary.calls)
        assertFalse(historyReadStatusText(pull.state).contains("não retornou registros"))
    }

    @Test fun cancellation_before_read_never_starts_the_sdk() = runTest {
        sync.cancelled = true
        val pull = sync.pullHistory(sleepOnly, "test", "test") {}
        assertEquals("cancelado", pull.state.phase)
        assertFalse(pull.state.isRunning)
        assertNull(pull.state.lastCompletedAtMs)
        assertEquals(0, HistoryBoundary.calls)
    }

    @Test fun cancellation_during_sdk_callback_is_not_reported_as_completed() = runTest {
        HistoryBoundary.read = { sync.cancelled = true; it.onReadSleepComplete() }
        val pull = sync.pullHistory(sleepOnly, "test", "test") {}
        assertEquals("cancelado", pull.state.phase)
        assertNull(pull.state.lastCompletedAtMs)
        assertTrue(historyReadStatusText(pull.state).contains("interrompida"))
    }

    @Test fun completed_empty_callback_is_distinguished_from_failed_attempts() = runTest {
        val pull = sync.pullHistory(sleepOnly, "test", "test") {}
        assertEquals("done", pull.state.phase)
        assertNotNull(pull.state.lastCompletedAtMs)
        assertNull(pull.state.lastError)
        assertEquals(1, HistoryBoundary.calls)
        assertTrue(historyReadStatusText(pull.state).contains("não retornou registros nesta consulta"))
    }

    @Test fun failed_sdk_and_fallback_exhaust_retries_without_claiming_no_data() = runTest {
        HistoryBoundary.read = { throw IllegalStateException("SDK test failure") }
        val pull = sync.pullHistory(sleepOnly, "test", "test") {}
        assertEquals("incomplete", pull.state.phase)
        assertNull(pull.state.lastCompletedAtMs)
        assertNotNull(pull.state.lastError)
        assertEquals(6, HistoryBoundary.calls) // Three attempts, each with its existing fallback.
        val text = historyReadStatusText(pull.state)
        assertTrue(text.contains("não confirma ausência de dados"))
        assertFalse(text.contains("não retornou registros"))
    }

    @Test fun cancelled_coroutine_does_not_retry_or_accept_a_late_completion() = runTest {
        lateinit var callback: ISleepDataListener
        HistoryBoundary.read = { callback = it }
        var returned = false
        val updates = mutableListOf<HistorySyncUiState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            sync.pullHistory(sleepOnly, "test", "test", updates::add)
            returned = true
        }
        job.cancelAndJoin()
        callback.onReadSleepComplete()
        assertFalse(returned)
        assertEquals(1, HistoryBoundary.calls)
        assertTrue(updates.none { it.lastCompletedAtMs != null })
        assertFalse(ShadowLog.getLogsForTag("VeepooHistorySync").any { it.msg.contains("SDK call failed") })
    }

    @Implements(VPOperateManager::class)
    class HistoryBoundary {
        companion object {
            var calls = 0
            var read: (ISleepDataListener) -> Unit = {}
        }

        @Implementation
        fun readSleepDataFromDay(ack: IBleWriteResponse, listener: ISleepDataListener, days: Int, offset: Int) {
            calls++
            read(listener)
        }

        @Implementation
        fun readSleepDataBySetting(ack: IBleWriteResponse, listener: ISleepDataListener, setting: ReadSleepSetting) {
            calls++
            read(listener)
        }
    }
}
