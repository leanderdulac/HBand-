package com.example.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.example.data.model.HBandDevice
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Fake Android registry/settings; no permission UI, BLE or operational VM. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientWatchRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val registry = Registry()
    private val watch = HBandDevice(deviceId = "recovery-watch", name = "Relógio de teste", macAddress = "00:11:22:33:44:55")
    private var scans = 0
    private var connections = 0
    private var settingsAttempts = 0
    private var settingsFail = true

    private fun content() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            val base = LocalContext.current
            val context = remember(base) { object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) {
                    settingsAttempts++
                    if (settingsFail) throw ActivityNotFoundException("SYNTHETIC_INTERNAL_SETTINGS")
                }
            } }
            CompositionLocalProvider(LocalContext provides context, LocalActivityResultRegistryOwner provides owner) {
                MyApplicationTheme { PatientWatchScreen(listOf(watch), null, false, { scans++ }, { connections++ }, {}, {}) }
            }
        }
    }

    @Test fun pending_scan_is_not_replaced_by_a_second_connection_request() {
        content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.onNodeWithTag("connect_watch_${watch.deviceId}").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, registry.launches)
            registry.respond(true)
            assertEquals(1, scans)
            assertEquals(0, connections)
        }
    }

    @Test fun unavailable_permission_prompt_allows_explicit_retry_without_a_stuck_action() {
        registry.fail = true
        content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.onNodeWithText("Não foi possível abrir a solicitação de permissão.", substring = true).assertExists()
        compose.onNodeWithText("SYNTHETIC_INTERNAL_REQUEST", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, scans); registry.fail = false }
        compose.onNodeWithTag("scan_ble_button").performScrollTo().performClick()
        compose.runOnIdle { registry.respond(true); assertEquals(1, scans); assertEquals(2, registry.launches) }
    }

    @Test fun unavailable_settings_keeps_the_screen_open_and_allows_retry() {
        content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.runOnIdle { registry.respond(false) }
        compose.onNodeWithText("Abrir permissões do aplicativo").performScrollTo().performClick()
        compose.onNodeWithText("Não foi possível abrir as permissões.", substring = true).assertExists()
        compose.onNodeWithText("SYNTHETIC_INTERNAL_SETTINGS", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, scans); settingsFail = false }
        compose.onNodeWithText("Abrir permissões do aplicativo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, settingsAttempts); assertEquals(0, scans) }
    }

    @Test fun denied_first_request_does_not_run_a_second_action_and_allows_new_connection() {
        content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.onNodeWithTag("connect_watch_${watch.deviceId}").performScrollTo().performClick()
        compose.runOnIdle { registry.respond(false); assertEquals(0, scans); assertEquals(0, connections) }
        compose.onNodeWithTag("connect_watch_${watch.deviceId}").performScrollTo().performClick()
        compose.runOnIdle { registry.respond(true); assertEquals(0, scans); assertEquals(1, connections); assertEquals(2, registry.launches) }
    }

    @Test fun failed_scan_prompt_cannot_leak_its_action_into_a_later_connection() {
        registry.fail = true
        content()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.runOnIdle { registry.fail = false }
        compose.onNodeWithTag("connect_watch_${watch.deviceId}").performScrollTo().performClick()
        compose.runOnIdle { registry.respond(true); assertEquals(0, scans); assertEquals(1, connections); assertEquals(2, registry.launches) }
    }

    @Test fun repeated_scan_while_request_is_pending_starts_only_once() {
        content()
        repeat(2) { compose.onNodeWithTag("scan_ble_button").performClick() }
        compose.runOnIdle { assertEquals(1, registry.launches); registry.respond(true); assertEquals(1, scans) }
    }

    private class Registry : ActivityResultRegistry() {
        var fail = false
        var launches = 0
        private var code = 0
        private var permissions = emptyList<String>()
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches++
            if (fail) throw IllegalStateException("SYNTHETIC_INTERNAL_REQUEST")
            code = requestCode
            permissions = (input as Array<*>).map { it as String }
        }
        fun respond(granted: Boolean) { dispatchResult(code, permissions.associateWith { granted }) }
    }
}
