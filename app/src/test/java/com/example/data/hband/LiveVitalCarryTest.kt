package com.example.data.hband

import android.app.Application
import com.example.data.ingest.LastValidBloodPressure
import com.example.data.model.HBandDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Live snapshot carry of last valid BP and current steps. No radio or Core. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LiveVitalCarryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var manager: HBandBleManager
    private val device = HBandDevice(macAddress = "00:11:22:33:44:55", name = "Synthetic VE30")

    @Before fun setup() {
        manager = HBandBleManager(RuntimeEnvironment.getApplication(), scope)
    }

    @After fun stop() {
        scope.cancel()
    }

    @Test fun later_hr_only_emit_keeps_last_valid_bp_inside_the_freshness_window() {
        setField("currentHeartRate", 76)
        setField("currentSystolic", 124)
        setField("currentDiastolic", 82)
        setField("currentSteps", 1800)
        setField("currentSpO2", 97)
        invokeEmit()
        val first = manager.latestTelemetry.value!!
        assertEquals(124, first.bloodPressure.systolic)
        assertEquals(82, first.bloodPressure.diastolic)
        assertEquals(1800, first.steps)

        setField("currentSystolic", 0)
        setField("currentDiastolic", 0)
        invokeEmit()
        val carried = manager.latestTelemetry.value!!
        assertEquals(76, carried.heartRate)
        assertEquals(124, carried.bloodPressure.systolic)
        assertEquals(82, carried.bloodPressure.diastolic)
        assertEquals(1800, carried.steps)
        assertEquals(97, carried.spO2)
        assertTrue(carried.isRealSensorData)
    }

    @Test fun snapshot_does_not_invent_bp_when_nothing_valid_was_measured() {
        setField("currentHeartRate", 70)
        setField("currentSteps", 50)
        val snapshot = manager.createTelemetrySnapshot(device)
        assertEquals(0, snapshot.bloodPressure.systolic)
        assertEquals(0, snapshot.bloodPressure.diastolic)
        assertEquals(50, snapshot.steps)
        assertEquals(70, snapshot.heartRate)
    }

    @Test fun history_bp_is_included_in_the_next_live_snapshot() {
        val hold = lastValidBp()
        hold.remember(130, 85, System.currentTimeMillis() - 5 * 60 * 1000L)
        setField("currentHeartRate", 68)
        val snapshot = manager.createTelemetrySnapshot(device)
        assertEquals(130, snapshot.bloodPressure.systolic)
        assertEquals(85, snapshot.bloodPressure.diastolic)
        assertEquals(LastValidBloodPressure.DEFAULT_FRESHNESS_MS, 60 * 60 * 1000L)
    }

    private fun invokeEmit() {
        HBandBleManager::class.java.getDeclaredMethod("emitRealTelemetry", String::class.java, String::class.java)
            .apply { isAccessible = true }
            .invoke(manager, device.macAddress, device.name)
    }

    private fun setField(name: String, value: Any) {
        HBandBleManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }

    private fun lastValidBp(): LastValidBloodPressure {
        val field = HBandBleManager::class.java.getDeclaredField("lastValidBp").apply { isAccessible = true }
        return field.get(manager) as LastValidBloodPressure
    }
}
