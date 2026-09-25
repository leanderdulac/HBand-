package com.example.data.hband

import android.app.Application
import android.os.Looper
import com.example.data.model.HBandDevice
import com.example.data.model.HBandTelemetry
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.*
import com.veepoo.protocol.model.datas.*
import com.veepoo.protocol.model.settings.ReadOriginSetting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

/** Runs the production post-handshake sequence; only the radio/SDK boundary is synthetic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [VeepooSportRoutingTest.Sdk::class])
@LooperMode(LooperMode.Mode.PAUSED)
class VeepooSportRoutingTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sport = mutableListOf<Pair<String, VeepooSportReading>>()
    private val history = mutableListOf<HBandTelemetry>()
    private lateinit var manager: HBandBleManager

    @Before fun setup() {
        resetSdkSingleton()
        Sdk.holdSport = false; Sdk.waiting = null; Sdk.steps = 2400
        manager = HBandBleManager(RuntimeEnvironment.getApplication(), scope,
            onHistorySamples = { history += it }, onSportReading = { id, reading -> sport += id to reading })
        // Bypass unrelated physical handshake; exercise the actual subsequent SDK orchestration.
        setFlow("_capabilities", DeviceCapabilities(probed = true))
        setFlow("_connectedDevice", HBandDevice(macAddress = "00:11:22:33:44:55", name = "Synthetic VE30"))
    }
    @After fun stop() {
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        resetSdkSingleton()
    }
    private fun resetSdkSingleton() {
        // The SDK keeps its instance across Robolectric shadow configurations.
        // Reset only this synthetic process's singleton, before and after this fixture.
        VPOperateManager::class.java.declaredFields.single {
            java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == VPOperateManager::class.java
        }.apply { isAccessible = true }.set(null, null)
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(name: String, value: T) {
        val field = HBandBleManager::class.java.getDeclaredField(name).apply { isAccessible = true }
        (field.get(manager) as MutableStateFlow<T>).value = value
    }
    private fun read() {
        HBandBleManager::class.java.getDeclaredMethod("startPostHandshakeSync", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(manager, false)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun history_does_not_replace_counters_or_emit_old_vitals_as_live() {
        read()
        assertEquals(org.robolectric.shadows.ShadowLog.getLogs().joinToString { "${it.tag}: ${it.msg}" }, 1, sport.size)
        assertEquals("00:11:22:33:44:55", sport.single().first)
        assertEquals(2400, sport.single().second.steps)
        assertEquals(1250f, sport.single().second.distanceMeters!!, 0f)
        assertEquals(72, history.single().heartRate)
        assertEquals(50, history.single().steps)
        assertEquals(25f, history.single().distanceMeters, 0f)
        assertEquals("00:11:22:33:44:55", history.single().deviceId)
        assertNull(manager.latestTelemetry.value)
        val cache = manager.generateCurrentTelemetry()
        assertEquals(2400, cache.steps)
        assertEquals(0, cache.heartRate)
        assertEquals(0, cache.bloodPressure.systolic)
        assertFalse(cache.isRealSensorData)
    }

    @Test fun later_zero_counter_is_not_replaced_by_an_old_daily_value() {
        read()
        Sdk.steps = 0
        read()
        assertEquals(listOf(2400, 0), sport.map { it.second.steps })
        assertEquals(0, manager.generateCurrentTelemetry().steps)
        assertNull(manager.latestTelemetry.value)
    }

    @Test fun cancelled_query_ignores_late_sdk_sport_callback() {
        Sdk.holdSport = true
        read()
        assertNotNull(Sdk.waiting)
        scope.cancel()
        Sdk.waiting!!.onSportDataChange(SportData().apply { step = 2400; dis = 1.25; kcal = 82.5 })
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(sport.isEmpty())
        assertTrue(history.isEmpty())
        assertNull(manager.latestTelemetry.value)
    }

    @Implements(VPOperateManager::class)
    class Sdk {
        companion object {
            var holdSport = false
            var waiting: ISportDataListener? = null
            var steps = 2400
        }
        @Implementation fun readBattery(write: IBleWriteResponse, listener: IBatteryDataListener) {
            listener.onDataChange(BatteryData().apply { batteryPercent = 80 })
        }
        @Implementation fun readSportStep(write: IBleWriteResponse, listener: ISportDataListener) {
            if (holdSport) waiting = listener else listener.onSportDataChange(
                SportData().apply { step = steps; kcal = 82.5; dis = 1.25 })
        }
        @Implementation fun readOriginDataBySetting(write: IBleWriteResponse, listener: IOriginProgressListener, setting: ReadOriginSetting) {
            (listener as IOriginDataListener).onOringinFiveMinuteDataChange(OriginData().apply {
                date = "2026-09-23"; setmTime(TimeData(2026, 9, 23, 8, 15, 0))
                rateValue = 72; highValue = 120; lowValue = 80
                stepValue = 50; calValue = 2.5; disValue = 0.025
            })
            listener.onReadOriginComplete()
        }
        @Implementation fun readSleepDataFromDay(write: IBleWriteResponse, listener: ISleepDataListener, day: Int, watchday: Int) {
            listener.onReadSleepComplete()
        }
    }
}
