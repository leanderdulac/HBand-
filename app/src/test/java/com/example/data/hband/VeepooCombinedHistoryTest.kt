package com.example.data.hband

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.*
import com.veepoo.protocol.model.datas.*
import com.veepoo.protocol.model.settings.ReadOriginSetting
import com.veepoo.protocol.model.settings.ReadSleepSetting
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

/** Real history client, synthetic SDK callbacks; no radio or patient data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class,
    instrumentedPackages = ["com.veepoo.protocol.VPOperateManager"],
    shadows = [VeepooCombinedHistoryTest.Boundary::class])
@LooperMode(LooperMode.Mode.PAUSED)
class VeepooCombinedHistoryTest {
    private val sdk = org.robolectric.shadow.api.Shadow.newInstanceOf(VPOperateManager::class.java)
    private val sync = VeepooHistorySync(sdk, Handler(Looper.getMainLooper()))
    private fun caps(version: Int) = DeviceCapabilities(
        probed = true, originProtocolVersion = version, historyDays = 3,
        isSupportHrv = true, isSupportSpo2 = true,
    )

    @Before fun reset() {
        assertTrue(org.robolectric.shadow.api.Shadow.extract<Any>(sdk) is Boundary)
        Boundary.calls.clear()
        Boundary.failOrigin = false
        Boundary.failSleep = false
        Boundary.emptyCombined = false
    }

    @Test fun protocol_5_preserves_combined_records_without_repeating_legacy_queries() = combined(5)
    @Test fun protocol_3_preserves_combined_records_without_repeating_legacy_queries() = combined(3)

    private fun combined(version: Int) = runTest {
        val pull = sync.pullHistory(caps(version), "fixture-device", "fixture-model") {}
        assertEquals(listOf("origin-combined", "sleep"), Boundary.calls)
        assertEquals("done", pull.state.phase)
        assertEquals(3, pull.samples.size)
        assertEquals(1, pull.state.originSamples)
        assertEquals(1, pull.state.hrvSamples)
        assertEquals(1, pull.state.spo2Samples)
        assertEquals(3, pull.state.totalSamples)
        assertEquals(setOf(VeepooHistoryMapper.MappedSample.Kind.ORIGIN,
            VeepooHistoryMapper.MappedSample.Kind.HRV, VeepooHistoryMapper.MappedSample.Kind.SPO2),
            pull.samples.map { it.kind }.toSet())
        pull.samples.forEach {
            assertEquals("fixture-device", it.telemetry.deviceId)
            assertEquals("fixture-model", it.telemetry.deviceModel)
            assertEquals(VeepooHistoryMapper.epochMsOf(Boundary.time(), null), it.epochMs)
            assertEquals(VeepooHistoryMapper.isoUtc(it.epochMs), it.telemetry.timestamp)
        }
        assertEquals(62, pull.samples.single { it.kind == VeepooHistoryMapper.MappedSample.Kind.HRV }.telemetry.hrvScore)
        assertEquals(97, pull.samples.single { it.kind == VeepooHistoryMapper.MappedSample.Kind.SPO2 }.telemetry.spO2)
    }

    @Test fun legacy_protocol_keeps_separate_hrv_and_oxygen_reads() = legacy(2)
    @Test fun protocol_4_is_not_assumed_to_use_the_combined_listener() = legacy(4)

    private fun legacy(version: Int) = runTest {
        val pull = sync.pullHistory(caps(version), "fixture-device", "fixture-model") {}
        assertEquals(listOf("origin-legacy", "sleep", "hrv", "spo2"), Boundary.calls)
        assertEquals("done", pull.state.phase)
        assertEquals(3, pull.samples.size)
        assertEquals(1, pull.state.originSamples)
        assertEquals(1, pull.state.hrvSamples)
        assertEquals(1, pull.state.spo2Samples)
    }

    @Test fun failed_combined_read_stays_incomplete_without_unsupported_fallback_queries() = runTest {
        Boundary.failOrigin = true
        val pull = sync.pullHistory(caps(5), "fixture-device", "fixture-model") {}
        assertEquals(listOf("origin-combined", "origin-combined", "origin-combined", "sleep"), Boundary.calls)
        assertEquals("incomplete", pull.state.phase)
        assertNull(pull.state.lastCompletedAtMs)
        assertNotNull(pull.state.lastError)
        assertTrue(pull.samples.isEmpty())
    }

    @Test fun sleep_failure_preserves_already_received_combined_records() = runTest {
        Boundary.failSleep = true
        val pull = sync.pullHistory(caps(5), "fixture-device", "fixture-model") {}
        assertEquals("incomplete", pull.state.phase)
        assertNull(pull.state.lastCompletedAtMs)
        assertEquals(3, pull.samples.size)
        assertEquals(1, pull.state.hrvSamples)
        assertEquals(1, pull.state.spo2Samples)
        assertFalse(Boundary.calls.any { it == "hrv" || it == "spo2" })
    }

    @Test fun completed_empty_combined_callbacks_do_not_fabricate_measurements() = runTest {
        Boundary.emptyCombined = true
        val pull = sync.pullHistory(caps(5), "fixture-device", "fixture-model") {}
        assertEquals(listOf("origin-combined", "sleep"), Boundary.calls)
        assertEquals("done", pull.state.phase)
        assertEquals(0, pull.state.totalSamples)
        assertTrue(pull.samples.isEmpty())
    }

    @Implements(VPOperateManager::class)
    class Boundary {
        companion object {
            val calls = mutableListOf<String>()
            var failOrigin = false
            var failSleep = false
            var emptyCombined = false
            fun time() = TimeData(2026, 9, 10, 8, 15, 0)
            fun hrv() = HRVOriginData().apply { setmTime(time()); hrvValue = 62; rate = "71" }
            fun spo2() = Spo2hOriginData().apply { setmTime(time()); oxygenValue = 97; heartValue = 68 }
        }

        @Implementation
        fun readOriginDataBySetting(ack: IBleWriteResponse, listener: IOriginProgressListener, setting: ReadOriginSetting) {
            calls += if (listener is IOriginData3Listener) "origin-combined" else "origin-legacy"
            if (failOrigin) { listener.onReadTimeout(1); return }
            if (listener is IOriginData3Listener) {
                if (!emptyCombined) {
                    listener.onOriginFiveMinuteListDataChange(mutableListOf(OriginData3().apply { setmTime(time()); stepValue = 320 }))
                    listener.onOriginHRVOriginListDataChange(mutableListOf(hrv()))
                    listener.onOriginSpo2OriginListDataChange(mutableListOf(spo2()))
                }
            } else {
                (listener as IOriginDataListener).onOringinFiveMinuteDataChange(OriginData().apply { setmTime(time()); stepValue = 320 })
            }
            listener.onReadOriginComplete()
        }

        @Implementation
        fun readSleepDataBySetting(ack: IBleWriteResponse, listener: ISleepDataListener, setting: ReadSleepSetting) {
            calls += "sleep"
            if (failSleep) throw IllegalStateException("Synthetic sleep failure")
            listener.onReadSleepComplete()
        }

        @Implementation
        fun readSleepDataFromDay(ack: IBleWriteResponse, listener: ISleepDataListener, day: Int, watchday: Int) {
            readSleepDataBySetting(ack, listener, ReadSleepSetting(day, false, watchday))
        }

        @Implementation
        fun readHRVOriginBySetting(ack: IBleWriteResponse, listener: IHRVOriginDataListener, setting: ReadOriginSetting) {
            calls += "hrv"
            listener.onHRVOriginListener(hrv())
            listener.onReadOriginComplete()
        }

        @Implementation
        fun readSpo2hOriginBySetting(ack: IBleWriteResponse, listener: ISpo2hOriginDataListener, setting: ReadOriginSetting) {
            calls += "spo2"
            listener.onSpo2hOriginListener(spo2())
            listener.onReadOriginComplete()
        }
    }
}
