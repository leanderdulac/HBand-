package com.example.ui.components

import android.Manifest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.data.model.HBandDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Activity-result fixtures only: never opens a permission dialog or starts a BLE scan. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientWatchPermissionResultTest {
    @get:Rule val compose = createComposeRule()
    private val registry = PermissionRegistry()
    private val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }
    private var scans = 0

    @Test fun granted_request_runs_original_scan_once() {
        content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.runOnIdle {
            assertEquals(0, scans)
            assertTrue(registry.requested.isNotEmpty())
            registry.respond(granted = true)
            assertEquals(1, scans)
        }
    }

    @Test fun refused_request_keeps_scan_stopped_and_explains_denial() {
        content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.runOnIdle { registry.respond(granted = false) }
        compose.onNodeWithText("A permissão não foi concedida.", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, scans) }
    }

    @Test fun restored_permission_result_requests_explicit_retry_without_claiming_denial() {
        val restoration = content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.respond(granted = true) }
        compose.onNodeWithText("Não foi possível retomar a ação. Toque novamente na opção de busca ou conexão.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A permissão não foi concedida.", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, scans) }
        compose.onNodeWithTag("scan_ble_button").performScrollTo().performClick()
        compose.runOnIdle {
            registry.respond(granted = true)
            assertEquals(1, scans)
        }
    }

    @Test fun interrupted_connection_does_not_reuse_an_old_watch_after_a_new_selection() {
        val first = HBandDevice(deviceId = "watch-fixture-first", name = "Relógio de teste", macAddress = "00:11:22:33:44:55")
        val second = HBandDevice(deviceId = "watch-fixture-second", name = "Relógio de teste", macAddress = "00:11:22:33:44:66")
        val connections = mutableListOf<HBandDevice>()
        val restoration = content(listOf(first, second), { connections += it })
        compose.onNodeWithTag("connect_watch_${first.deviceId}").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            registry.respond(granted = true)
            assertTrue(connections.isEmpty())
        }
        compose.onNodeWithTag("connect_watch_${second.deviceId}").performScrollTo().performClick()
        compose.runOnIdle {
            registry.respond(granted = true)
            assertEquals(1, connections.size)
            assertSame(second, connections.single())
        }
    }

    @Test fun stopping_a_scan_does_not_request_revoked_permissions_or_start_another_scan() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val scanning = mutableStateOf(true)
        var stops = 0
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MyApplicationTheme {
                    PatientWatchScreen(emptyList(), null, scanning.value, { scans++ }, {}, {}, {},
                        onStopScan = { stops++; scanning.value = false })
                }
            }
        }
        compose.onNodeWithTag("stop_ble_scan_button").performScrollTo().performClick()
        compose.onNodeWithTag("stop_ble_scan_button").assertDoesNotExist()
        compose.onNodeWithTag("scan_ble_button").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(1, stops)
            assertEquals(0, scans)
            assertTrue(registry.requested.isEmpty())
        }
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.runOnIdle {
            assertEquals(0, scans)
            assertTrue(registry.requested.contains(Manifest.permission.BLUETOOTH_SCAN))
        }
    }

    private fun content(
        devices: List<HBandDevice> = emptyList(),
        connect: (HBandDevice) -> Unit = {},
    ): StateRestorationTester {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MyApplicationTheme {
                    PatientWatchScreen(devices, null, false, { scans++ }, connect, {}, {})
                }
            }
        }
        return restoration
    }

    private class PermissionRegistry : ActivityResultRegistry() {
        private var lastRequestCode: Int? = null
        var requested: List<String> = emptyList()
            private set

        override fun <I, O> onLaunch(
            requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?,
        ) {
            lastRequestCode = requestCode
            requested = (input as Array<*>).map { it as String }
        }

        fun respond(granted: Boolean) {
            check(requested.isNotEmpty()) { "No permission request captured" }
            dispatchResult(checkNotNull(lastRequestCode), requested.associateWith { granted })
        }
    }
}
