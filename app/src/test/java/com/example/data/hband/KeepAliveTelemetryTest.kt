package com.example.data.hband

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Looper
import com.example.data.model.HBandDevice
import com.example.data.model.HBandTelemetry
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
import org.robolectric.shadow.api.Shadow
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Production timer and telemetry publisher; synthetic GATT only, no radio or backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [KeepAliveTelemetryTest.GattBoundary::class])
@LooperMode(LooperMode.Mode.PAUSED)
class KeepAliveTelemetryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var manager: HBandBleManager
    private lateinit var gatt: BluetoothGatt
    private val device = HBandDevice(macAddress = "00:11:22:33:44:55", name = "Synthetic generic watch", isConnected = true)

    @Before fun setup() {
        GattBoundary.cycleEntered = CompletableDeferred()
        GattBoundary.reads.set(0)
        manager = HBandBleManager(RuntimeEnvironment.getApplication(), scope)
        gatt = Shadow.newInstanceOf(BluetoothGatt::class.java)
        setField("currentGatt", gatt)
        setFlow("_connectedDevice", device)
    }

    @After fun stop() = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun setField(name: String, value: Any) {
        HBandBleManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(name: String, value: T) {
        val field = HBandBleManager::class.java.getDeclaredField(name).apply { isAccessible = true }
        (field.get(manager) as MutableStateFlow<T>).value = value
    }

    private fun callback() = HBandBleManager::class.java.getDeclaredField("gattCallback")
        .apply { isAccessible = true }.get(manager) as BluetoothGattCallback

    private fun sensorCallback(heartRate: Int) {
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        callback().onCharacteristicChanged(gatt, characteristic, byteArrayOf(0, heartRate.toByte()))
    }

    private suspend fun oneKeepAliveCycle() {
        HBandBleManager::class.java.getDeclaredMethod("startKeepAliveLoop")
            .apply { isAccessible = true }.invoke(manager)
        // Wait for the real five-second coroutine timer to reach the synthetic GATT boundary.
        // Joining cancellation lets the non-suspending cycle finish before asserting its effects.
        withTimeout(15_000) { GattBoundary.cycleEntered.await() }
        val job = HBandBleManager::class.java.getDeclaredField("keepAliveJob")
            .apply { isAccessible = true }.get(manager) as Job
        job.cancelAndJoin()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Battery read must still be requested", 1, GattBoundary.reads.get())
        val battery = BluetoothGattCharacteristic(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"),
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
        callback().onCharacteristicRead(gatt, battery, byteArrayOf(80), BluetoothGatt.GATT_SUCCESS)
        assertEquals(80, manager.connectedDevice.value!!.batteryLevel)
    }

    @Test fun keepalive_keeps_last_measurement_and_new_sensor_callback_still_publishes() = runBlocking {
        sensorCallback(72)
        // Make time inequality deterministic without modifying the system clock.
        val original = manager.latestTelemetry.value!!.copy(timestamp = "2026-09-24T12:00:00Z")
        setFlow<HBandTelemetry?>("_latestTelemetry", original)
        oneKeepAliveCycle()
        assertSame("A timer is not a new measurement", original, manager.latestTelemetry.value)
        sensorCallback(81)
        assertEquals(81, manager.latestTelemetry.value!!.heartRate)
        assertEquals(device.macAddress, manager.latestTelemetry.value!!.deviceId)
        assertTrue(manager.latestTelemetry.value!!.isRealSensorData)
        assertNotSame(original, manager.latestTelemetry.value)
    }

    @Test fun keepalive_does_not_create_a_reading_from_cached_values_without_a_published_sample() = runBlocking {
        setField("currentHeartRate", 72)
        setField("hasReceivedRealSensorData", true)
        assertNull(manager.latestTelemetry.value)
        oneKeepAliveCycle()
        assertNull(manager.latestTelemetry.value)
    }

    @Implements(BluetoothGatt::class)
    class GattBoundary {
        companion object {
            lateinit var cycleEntered: CompletableDeferred<Unit>
            val reads = AtomicInteger()
            private val batteryId = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
            private val batteryLevelId = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        }

        @Implementation fun getService(uuid: UUID): BluetoothGattService? {
            if (uuid != batteryId) return null
            cycleEntered.complete(Unit)
            return BluetoothGattService(batteryId, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                addCharacteristic(BluetoothGattCharacteristic(batteryLevelId,
                    BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
            }
        }

        @Implementation fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
            assertEquals(batteryLevelId, characteristic.uuid)
            reads.incrementAndGet()
            return true
        }

        @Implementation fun getDevice(): BluetoothDevice? = null // Use the explicitly seeded synthetic session identity.
    }
}
