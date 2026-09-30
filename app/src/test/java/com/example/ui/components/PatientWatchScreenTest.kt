package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.model.HBandDevice
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientWatchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val device = HBandDevice(deviceId = "watch-fixture", name = "VE30 de teste", macAddress = "00:11:22:33:44:55", isConnected = false)

    @Test
    @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun tablet_places_results_beside_search_and_keeps_exact_device_selection() {
        var selected: HBandDevice? = null
        compose.setContent {
            MyApplicationTheme {
                PatientAdaptiveScaffold(2, {}, header = { HomeWelcomeHeader("", "", {}, showGreeting = false) }) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp)) {
                        PatientWatchContent(listOf(device), null, false, null, {}, { selected = it }, {}, {}, {})
                    }
                }
            }
        }
        val first = compose.onNodeWithTag("patient_summary_first").fetchSemanticsNode()
        val second = compose.onNodeWithTag("patient_summary_second").fetchSemanticsNode()
        assertEquals(first.positionInRoot.y, second.positionInRoot.y, 1f)
        assertTrue(second.positionInRoot.x >= first.positionInRoot.x + first.size.width)
        compose.onNodeWithTag("scan_ble_button").assertIsDisplayed()
        compose.onNodeWithTag("connect_watch_${device.deviceId}").assertIsDisplayed().performClick()
        compose.runOnIdle { assertSame(device, selected) }
    }

    @Test fun phone_with_large_text_keeps_results_below_search() {
        content(devices = listOf(device), fontScale = 1.6f)
        val first = compose.onNodeWithTag("patient_summary_first").fetchSemanticsNode()
        val second = compose.onNodeWithTag("patient_summary_second").fetchSemanticsNode()
        assertEquals(first.positionInRoot.x, second.positionInRoot.x, 1f)
        assertTrue(second.positionInRoot.y >= first.positionInRoot.y + first.size.height)
        compose.onNodeWithTag("scan_ble_button").assertIsDisplayed()
        compose.onNodeWithTag("connect_watch_${device.deviceId}").performScrollTo().assertIsDisplayed()
    }

    @Test fun results_announce_list_count_changes_without_claiming_a_connection() {
        val devices = mutableStateOf(emptyList<HBandDevice>())
        compose.setContent {
            MyApplicationTheme {
                PatientWatchContent(devices.value, null, false, null, {}, {}, {}, {}, {})
            }
        }
        val heading = compose.onNodeWithTag("watch_results_heading")
        heading.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Nenhum relógio na lista"))
        compose.runOnIdle { devices.value = listOf(device) }
        heading.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 relógio na lista"))
        compose.onNodeWithText("Relógio conectado").assertDoesNotExist()
        compose.runOnIdle { devices.value = listOf(device, device.copy(deviceId = "second-watch", macAddress = "00:11:22:33:44:66")) }
        heading.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 relógios na lista"))
    }

    @Test fun connecting_from_results_keeps_the_exact_device_identity() {
        var chosen: HBandDevice? = null
        content(devices = listOf(device), connect = { chosen = it })
        compose.onNodeWithTag("connect_watch_${device.deviceId}").performScrollTo().performClick()
        compose.runOnIdle { assertSame(device, chosen) }
    }

    @Test fun permission_denial_and_large_text_leave_help_reachable() {
        content(message = "A permissão não foi concedida.", fontScale = 1.6f)
        compose.onNodeWithText("A permissão não foi concedida.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Abrir permissões do aplicativo").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/watch_permission_large_text.png")
    }

    private fun content(
        devices: List<HBandDevice> = emptyList(),
        connect: (HBandDevice) -> Unit = {},
        byMac: (String) -> Unit = {},
        message: String? = null,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme {
                    Surface(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) {
                            PatientWatchContent(devices, null, false, message, {}, connect, byMac, {}, {})
                        }
                    }
                }
            }
        }
    }
}
