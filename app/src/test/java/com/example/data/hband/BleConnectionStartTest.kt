package com.example.data.hband

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Looper
import com.example.data.model.HBandDevice
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IConnectResponse
import com.veepoo.protocol.listener.base.INotifyResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowBluetoothAdapter
import java.time.Duration

/** Runs the manager with Android shadows; no physical radio or patient records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class BleConnectionStartTest {
    private val app = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val adapter = app.getSystemService(BluetoothManager::class.java).adapter
    private val device = HBandDevice(
        deviceId = "00:11:22:33:44:55",
        macAddress = "00:11:22:33:44:55",
        name = "Galaxy Watch de teste",
        isConnected = false,
    )

    @After fun release() { scope.cancel() }

    private fun restoreSavedWatch(name: String, initiallyStopped: Boolean = false): HBandBleManager {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        app.getSharedPreferences("hband_settings", Context.MODE_PRIVATE).edit()
            .putBoolean(HBandBleManager.PREF_AUTO_RECONNECT, true)
            .putString(HBandBleManager.PREF_LAST_MAC, device.macAddress)
            .putString(HBandBleManager.PREF_LAST_NAME, name).commit()
        return HBandBleManager(app, scope, initiallyDisconnectedByUser = initiallyStopped)
    }

    @Test fun stop_during_startup_survives_ready_and_manual_connect_still_works() = kotlinx.coroutines.runBlocking {
        val startupApp = com.example.HBandHealthSyncApp()
        try {
            startupApp.requestBleSessionStop()
            assertNull(startupApp.readyBleManagerOrNull())
            lateinit var manager: HBandBleManager
            startupApp.storageStartup.initialize({}, {
                manager = restoreSavedWatch(device.name, startupApp.startupStopRequested)
            })
            assertTrue(startupApp.storageStartup.isReady)
            assertTrue(startupApp.startupStopRequested)
            assertTrue(shadowOf(remote()).bluetoothGatts.isEmpty())
            manager.reconnectLastDevice()
            assertTrue(shadowOf(remote()).bluetoothGatts.isEmpty())
            assertTrue(manager.connectDevice(device))
            assertEquals(1, shadowOf(remote()).bluetoothGatts.size)
            manager.disconnectDevice()
        } finally { startupApp.bleScope.cancel() }
    }

    @Test fun cold_start_with_saved_generic_watch_initializes_scan_and_gatt_before_reconnect() {
        val manager = restoreSavedWatch(device.name)
        assertEquals(device.macAddress, manager.connectedDevice.value?.macAddress)
        assertDisconnected(manager)
        assertNotNull(shadowOf(lastGatt()).gattCallback)
        manager.isAutoReconnectEnabled = false
        manager.disconnectDevice()
    }

    @Test
    @Config(shadows = [VeepooConnectBoundary::class])
    fun cold_start_with_saved_veepoo_watch_reaches_connection_setup_without_lazy_initialization_failure() {
        VeepooConnectBoundary.requestedAddress = null
        val manager = restoreSavedWatch("VE30 de teste")
        assertEquals(device.macAddress, manager.connectedDevice.value?.macAddress)
        assertEquals(device.macAddress, VeepooConnectBoundary.requestedAddress)
        assertDisconnected(manager)
        assertFalse(org.robolectric.shadows.ShadowLog.getLogsForTag("HBandBleManager")
            .any { it.msg.contains("Error checking bonded devices") })
        manager.isAutoReconnectEnabled = false
        manager.disconnectDevice()
    }

    /** Only the radio/Android-native SDK boundary is replaced; manager setup runs normally. */
    @Implements(VPOperateManager::class)
    class VeepooConnectBoundary {
        companion object { var requestedAddress: String? = null }

        @Implementation
        fun connectDevice(address: String, name: String, connect: IConnectResponse, notify: INotifyResponse) {
            requestedAddress = address
        }
    }

    private fun readyManager(): HBandBleManager {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        return HBandBleManager(app, scope).also { it.isAutoReconnectEnabled = false }
    }

    private fun remote() = adapter.getRemoteDevice(device.macAddress)
    private fun lastGatt() = shadowOf(remote()).bluetoothGatts.last()
    private fun report(gatt: BluetoothGatt, status: Int, state: Int) {
        shadowOf(gatt).gattCallback!!.onConnectionStateChange(gatt, status, state)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertDisconnected(manager: HBandBleManager) {
        assertFalse(manager.isHardwareConnected.value)
        assertFalse(manager.connectedDevice.value?.isConnected == true)
        assertNull(manager.latestTelemetry.value)
    }

    @Test fun bluetooth_off_cannot_create_a_connected_placeholder() {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(adapter).setState(BluetoothAdapter.STATE_OFF)
        val manager = HBandBleManager(app, scope)
        assertFalse(manager.connectDevice(device))
        assertDisconnected(manager)
        assertNotNull(manager.sessionMessage.value)
        assertFalse(manager.connectDevice(device.copy(name = "VE30 de teste")))
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
    }

    @Test fun invalid_addresses_are_rejected_before_generic_or_veepoo_side_effects() {
        val manager = readyManager()
        for (name in listOf("Galaxy Watch de teste", "VE30 de teste")) {
            assertFalse(manager.connectDevice(device.copy(name = name, macAddress = "invalid")))
            assertDisconnected(manager)
            assertNull(manager.connectedDevice.value)
            assertTrue(manager.sessionMessage.value!!.contains("inválido"))
            assertFalse(manager.hasPersistedSession())
        }
        assertTrue(shadowOf(remote()).bluetoothGatts.isEmpty())
    }

    @Test fun permission_loss_blocks_both_paths_and_granting_it_allows_retry() {
        val manager = readyManager()
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        for (name in listOf("Galaxy Watch de teste", "VE30 de teste")) {
            assertFalse(manager.connectDevice(device.copy(name = name)))
            assertDisconnected(manager)
            assertTrue(manager.sessionMessage.value!!.contains("permissões"))
        }
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertTrue(manager.connectDevice(device))
        assertDisconnected(manager) // Request accepted, not connected yet.
        assertEquals(1, shadowOf(remote()).bluetoothGatts.size)
    }

    @Test fun generic_connection_waits_for_success_before_persisting_or_marking_connected() {
        val manager = readyManager()
        assertTrue(manager.connectDevice(device))
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        assertTrue(manager.isHardwareConnected.value)
        assertTrue(manager.connectedDevice.value!!.isConnected)
        assertEquals(device.macAddress, manager.connectedDevice.value!!.macAddress)
        assertTrue(manager.hasPersistedSession())
        manager.disconnectDevice()
    }

    @Test fun disabling_auto_reconnect_cancels_an_already_scheduled_attempt() {
        val manager = readyManager()
        manager.isAutoReconnectEnabled = true
        assertTrue(manager.connectDevice(device))
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        manager.isAutoReconnectEnabled = false
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(1, shadowOf(remote()).bluetoothGatts.size)
        assertDisconnected(manager)
    }

    @Test fun toggling_back_on_does_not_revive_the_cancelled_attempt() {
        val manager = readyManager()
        manager.isAutoReconnectEnabled = true
        assertTrue(manager.connectDevice(device))
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        manager.isAutoReconnectEnabled = false
        manager.isAutoReconnectEnabled = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(1, shadowOf(remote()).bluetoothGatts.size)
        manager.disconnectDevice()
    }

    @Test fun service_reconnect_respects_disabled_preference_but_manual_connect_still_works() {
        val manager = readyManager()
        assertTrue(manager.connectDevice(device))
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertTrue(manager.hasPersistedSession())
        manager.reconnectLastDevice()
        assertEquals(1, shadowOf(remote()).bluetoothGatts.size)
        assertDisconnected(manager)
        assertTrue(manager.connectDevice(device))
        assertEquals(2, shadowOf(remote()).bluetoothGatts.size)
        manager.disconnectDevice()
    }

    @Test fun disabling_auto_reconnect_preserves_the_current_connection() {
        val manager = readyManager()
        manager.isAutoReconnectEnabled = true
        assertTrue(manager.connectDevice(device))
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        manager.isAutoReconnectEnabled = false
        assertTrue(manager.isHardwareConnected.value)
        assertTrue(manager.connectedDevice.value!!.isConnected)
        assertFalse(shadowOf(lastGatt()).isClosed)
        manager.disconnectDevice()
    }

    @Test fun enabled_auto_reconnect_still_requests_a_connection_after_link_loss() {
        val manager = readyManager()
        manager.isAutoReconnectEnabled = true
        assertTrue(manager.connectDevice(device))
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(2, shadowOf(remote()).bluetoothGatts.size)
        assertDisconnected(manager) // A new request is not proof of connection.
        manager.disconnectDevice()
    }

    @Test fun failed_callback_is_not_a_connection_even_with_a_connected_state_code() {
        val manager = readyManager()
        assertTrue(manager.connectDevice(device))
        val failed = lastGatt()
        report(failed, BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_CONNECTED)
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
        assertNotNull(manager.sessionMessage.value)
        assertTrue(shadowOf(failed).isClosed)
        report(failed, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        assertDisconnected(manager)
    }

    @Test fun late_callback_from_abandoned_attempt_cannot_mark_the_new_attempt_connected() {
        val manager = readyManager()
        assertTrue(manager.connectDevice(device))
        val old = lastGatt()
        assertTrue(manager.connectDevice(device))
        val current = lastGatt()
        assertNotSame(old, current)
        assertTrue(shadowOf(old).isClosed)
        report(old, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
        report(current, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        assertTrue(manager.isHardwareConnected.value)
        manager.disconnectDevice()
        report(current, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        assertFalse(manager.isHardwareConnected.value)
        assertFalse(manager.connectedDevice.value!!.isConnected)
    }

    @Test fun failure_inside_platform_connect_does_not_create_a_placeholder_and_can_retry() {
        val manager = readyManager()
        shadowOf(remote()).setGattConnectionInterceptor { throw SecurityException("Permission revoked during connect") }
        assertFalse(manager.connectDevice(device))
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
        assertNotNull(manager.sessionMessage.value)
        shadowOf(remote()).setGattConnectionInterceptor { }
        assertTrue(manager.connectDevice(device))
        assertDisconnected(manager)
    }

    @Test fun rejected_attempt_preserves_the_current_session_and_last_successful_address() {
        val manager = readyManager()
        assertTrue(manager.connectDevice(device))
        report(lastGatt(), BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        val previous = manager.connectedDevice.value
        assertFalse(manager.connectDevice(device.copy(macAddress = "invalid")))
        assertEquals(previous, manager.connectedDevice.value)
        assertTrue(manager.isHardwareConnected.value)
        assertEquals(device.macAddress, app.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)
            .getString(HBandBleManager.PREF_LAST_MAC, null))
        manager.disconnectDevice()
    }

    @Test fun callback_arriving_before_connect_returns_is_applied_after_handle_is_available() {
        val manager = readyManager()
        shadowOf(remote()).setGattConnectionInterceptor { gatt ->
            shadowOf(gatt).gattCallback!!.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        }
        assertTrue(manager.connectDevice(device))
        assertDisconnected(manager)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(manager.isHardwareConnected.value)
        assertTrue(manager.connectedDevice.value!!.isConnected)
        manager.disconnectDevice()
    }

    @Test fun callback_queued_from_background_before_disconnect_cannot_revive_session() {
        val manager = readyManager()
        assertTrue(manager.connectDevice(device))
        val gatt = lastGatt()
        val worker = Thread {
            shadowOf(gatt).gattCallback!!.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        }
        worker.start()
        worker.join()
        manager.disconnectDevice()
        shadowOf(Looper.getMainLooper()).idle()
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
    }

    @Test
    @Config(sdk = [28])
    fun legacy_android_still_accepts_a_permitted_request_without_claiming_connection() {
        val manager = readyManager()
        assertTrue(manager.connectDevice(device))
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
    }

    @Test fun unavailable_adapter_does_not_start_or_persist_a_connection() {
        ShadowBluetoothAdapter.setIsBluetoothSupported(false)
        val manager = HBandBleManager(app, scope)
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        assertFalse(manager.connectDevice(device))
        assertDisconnected(manager)
        assertFalse(manager.hasPersistedSession())
        assertTrue(manager.sessionMessage.value!!.contains("não está disponível"))
    }

    @Test
    @Config(sdk = [31, 36], shadows = [RevokedAdapter::class])
    fun permission_race_reading_adapter_state_is_safe_during_init_scan_precheck_and_connect() {
        val manager = readyManager()
        manager.checkBondedOrAutoConnect()
        assertFalse(manager.connectDevice(device))
        assertDisconnected(manager)
        assertTrue(manager.sessionMessage.value!!.contains("permissões"))
        assertFalse(manager.hasPersistedSession())
    }

    @Implements(BluetoothAdapter::class)
    class RevokedAdapter : ShadowBluetoothAdapter() {
        @Implementation
        public override fun isEnabled(): Boolean = throw SecurityException("Permission revoked after precheck")
    }
}
