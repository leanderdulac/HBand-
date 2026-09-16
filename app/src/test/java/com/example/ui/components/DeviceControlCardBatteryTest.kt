package com.example.ui.components

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.example.data.model.HBandDevice
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36], application = android.app.Application::class)
class DeviceControlCardBatteryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun disconnected_unknown_battery_shows_dash_not_placeholder_percent() {
        setCard(
            HBandDevice(
                name = "VE30",
                macAddress = "84:03:A6:48:DD:ED",
                isConnected = false,
                batteryLevel = null,
                batteryIsSimulated = false,
                firmwareVersion = "VE30 Direct MAC",
            )
        )
        composeTestRule.onNodeWithTag("device_battery_level").assertTextEquals("--")
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/device_battery_unknown.png",
        )
    }

    @Test
    fun sdk_percent_is_shown_without_sim_label() {
        setCard(
            HBandDevice(
                name = "VE30",
                macAddress = "84:03:A6:48:DD:ED",
                isConnected = true,
                batteryLevel = 18,
                batteryIsSimulated = false,
            )
        )
        composeTestRule.onNodeWithTag("device_battery_level").assertTextEquals("18%")
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/device_battery_sdk_18.png",
        )
    }

    @Test
    fun simulated_low_battery_is_labeled_as_simulation() {
        setCard(
            HBandDevice(
                name = "VE30",
                macAddress = "84:03:A6:48:DD:ED",
                isConnected = true,
                batteryLevel = 14,
                batteryIsSimulated = true,
            )
        )
        composeTestRule.onNodeWithTag("device_battery_level").assertTextEquals("14% sim.")
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/device_battery_simulated.png",
        )
    }

    private fun setCard(device: HBandDevice) {
        composeTestRule.setContent {
            MyApplicationTheme {
                DeviceControlCard(
                    device = device,
                    autoIngestLive = false,
                    onToggleAutoIngest = {},
                    onSpotCheck = {},
                    onSimulateBatch = {},
                    onScanClick = {},
                    onDisconnect = {},
                )
            }
        }
    }
}
