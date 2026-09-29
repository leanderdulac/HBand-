package com.example.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.hband.BleScanFailure
import com.example.data.model.HBandDevice
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientScanFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun large_text_stop_keeps_found_devices_and_does_not_connect_or_disconnect() {
        val scanning = mutableStateOf(true)
        val device = HBandDevice(deviceId = "scan-fixture", name = "Relógio de teste",
            macAddress = "00:11:22:33:44:55", isConnected = false)
        var stops = 0
        var otherActions = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MyApplicationTheme {
                    PatientWatchContent(listOf(device), null, scanning.value, null,
                        { otherActions++ }, { otherActions++ }, { otherActions++ }, { otherActions++ }, {},
                        onStopScan = { stops++; scanning.value = false })
                }
            }
        }
        compose.onNodeWithTag("stop_ble_scan_button").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithTag("stop_ble_scan_button").assertDoesNotExist()
        compose.onNodeWithTag("scan_ble_button").assertIsEnabled()
        compose.onNodeWithText("Relógio de teste").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("connect_watch_scan-fixture").assertExists()
        compose.runOnIdle { assertEquals(1, stops); assertEquals(0, otherActions) }
    }

    @Test fun bluetooth_off_explains_the_problem_and_explicit_retry_hides_the_old_failure() {
        val scanning = mutableStateOf(false)
        val failure = mutableStateOf<BleScanFailure?>(BleScanFailure.BLUETOOTH_OFF)
        var starts = 0
        compose.setContent {
            MyApplicationTheme {
                PatientWatchContent(emptyList(), null, scanning.value, null,
                    { starts++; scanning.value = true }, {}, {}, {}, {}, scanFailure = failure.value)
            }
        }
        compose.onNodeWithTag("watch_connection_feedback").performScrollTo()
            .assertTextContains("o Bluetooth estava desligado.", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Abrir permissões do aplicativo").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithTag("scan_ble_button").performScrollTo().performClick()
        compose.onNodeWithTag("watch_connection_feedback").assertDoesNotExist()
        compose.onNodeWithTag("scan_ble_button").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, starts); failure.value = null; scanning.value = false }
        compose.onNodeWithTag("watch_connection_feedback").assertDoesNotExist()
        compose.onNodeWithTag("scan_ble_button").assertIsEnabled()
    }

    @Test fun backend_permission_loss_offers_settings_without_starting_scan_or_opening_them_automatically() {
        var settings = 0
        var starts = 0
        compose.setContent {
            MyApplicationTheme {
                PatientWatchContent(emptyList(), null, false, null, { starts++ }, {}, {}, {},
                    { settings++ }, scanFailure = BleScanFailure.PERMISSION_REQUIRED)
            }
        }
        compose.onNodeWithTag("watch_connection_feedback").performScrollTo()
            .assertTextContains("falta de permissão", substring = true)
        compose.runOnIdle { assertEquals(0, settings); assertEquals(0, starts) }
        compose.onNodeWithText("Abrir permissões do aplicativo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, settings); assertEquals(0, starts) }
    }

    @Test fun current_permission_result_takes_precedence_over_an_old_scan_error() {
        compose.setContent {
            MyApplicationTheme {
                PatientWatchContent(emptyList(), null, false, "A permissão não foi concedida.",
                    {}, {}, {}, {}, {}, scanFailure = BleScanFailure.BLUETOOTH_OFF)
            }
        }
        compose.onNodeWithTag("watch_connection_feedback").assertTextEquals("A permissão não foi concedida.")
        compose.onNodeWithText("o Bluetooth estava desligado.", substring = true).assertDoesNotExist()
    }
}
