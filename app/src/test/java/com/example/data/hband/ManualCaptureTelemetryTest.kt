package com.example.data.hband

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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

/** Exercise manual capture and real callback/parser using only a synthetic GATT boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [ManualCaptureTelemetryTest.GattBoundary::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ManualCaptureTelemetryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var manager: HBandBleManager
    private lateinit var gatt: BluetoothGatt

    @Before fun setup() {
        GattBoundary.reads = 0
        manager = HBandBleManager(RuntimeEnvironment.getApplication(), scope)
        gatt = Shadow.newInstanceOf(BluetoothGatt::class.java)
        setField("currentGatt", gatt)
        setFlow("_connectedDevice", HBandDevice(macAddress = "00:11:22:33:44:55",
            name = "Synthetic manual watch", isConnected = true))
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

    private fun sensorCallback(heartRate: Int) {
        val callback = HBandBleManager::class.java.getDeclaredField("gattCallback")
            .apply { isAccessible = true }.get(manager) as BluetoothGattCallback
        callback.onCharacteristicChanged(gatt, GattBoundary.characteristic(), byteArrayOf(0, heartRate.toByte()))
    }

    @Test fun manual_request_preserves_last_sample_until_sensor_callback_arrives() {
        sensorCallback(72)
        val original = manager.latestTelemetry.value!!.copy(timestamp = "2026-09-24T12:00:00Z")
        setFlow<HBandTelemetry?>("_latestTelemetry", original)
        val captured = manager.triggerSpotCheck()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("The manual GATT read must still be requested", 1, GattBoundary.reads)
        assertSame("A button press is not a new measurement", original, captured)
        assertSame(original, manager.latestTelemetry.value)
        sensorCallback(81)
        val next = manager.latestTelemetry.value!!
        assertEquals(81, next.heartRate)
        assertEquals(original.deviceId, next.deviceId)
        assertTrue(next.isRealSensorData)
        assertNotSame(original, next)
        assertNotEquals(original.timestamp, next.timestamp)
    }

    @Test fun manual_request_does_not_fabricate_sample_from_cache_when_no_sample_was_published() {
        setField("currentHeartRate", 72)
        setField("hasReceivedRealSensorData", true)
        assertNull(manager.latestTelemetry.value)
        assertNull(manager.triggerSpotCheck())
        assertNull(manager.latestTelemetry.value)
    }

    @Test fun manual_request_without_sensor_data_keeps_absence_instead_of_publishing_zero_sample() {
        assertNull(manager.triggerSpotCheck())
        assertNull(manager.latestTelemetry.value)
    }

    @Implements(BluetoothGatt::class)
    class GattBoundary {
        companion object {
            var reads = 0
            private val hrId = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
            fun characteristic() = BluetoothGattCharacteristic(hrId,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ)
        }
        @Implementation fun getServices(): List<BluetoothGattService> = listOf(
            BluetoothGattService(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
                BluetoothGattService.SERVICE_TYPE_PRIMARY).apply { addCharacteristic(characteristic()) })
        @Implementation fun getService(uuid: UUID): BluetoothGattService? = null
        @Implementation fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
            assertEquals(hrId, characteristic.uuid)
            reads++
            return true // Leave the response pending; a request is not a received measurement.
        }
        @Implementation fun getDevice(): BluetoothDevice? = null
    }
}
