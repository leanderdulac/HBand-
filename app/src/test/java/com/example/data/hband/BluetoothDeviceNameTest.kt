package com.example.data.hband

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 36], application = Application::class)
class BluetoothDeviceNameTest {
    private fun testDevice() = RuntimeEnvironment.getApplication()
        .getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice("00:11:22:33:44:55")

    @Test fun permission_revoked_after_successful_read_keeps_known_name_without_crashing() {
        val application = RuntimeEnvironment.getApplication()
        val device = testDevice()
        shadowOf(device).setName("Relógio de teste")
        shadowOf(device).setShouldThrowSecurityExceptions(true)
        shadowOf(application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertEquals("Relógio de teste", bluetoothDeviceNameOrFallback(device, "Nome conhecido"))

        shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        // Prove the platform read fails; a shadow silently returning a name would not test the regression.
        assertThrows(SecurityException::class.java) { device.name }
        assertEquals("Nome conhecido", bluetoothDeviceNameOrFallback(device, "Nome conhecido"))
        assertEquals("00:11:22:33:44:55", device.address)

        shadowOf(application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertEquals("Relógio de teste", bluetoothDeviceNameOrFallback(device, "Nome conhecido"))
    }

    @Test fun missing_device_or_name_preserves_the_existing_fallback() {
        val device = testDevice()
        assertNull(device.name)
        assertEquals("Nome conhecido", bluetoothDeviceNameOrFallback(device, "Nome conhecido"))
        assertEquals("Nome conhecido", bluetoothDeviceNameOrFallback(null, "Nome conhecido"))
    }

    @Test
    @Config(sdk = [28])
    fun legacy_android_preserves_device_name() {
        val device = testDevice()
        shadowOf(device).setName("Relógio legado de teste")
        assertEquals("Relógio legado de teste", bluetoothDeviceNameOrFallback(device, "Nome conhecido"))
    }
}
