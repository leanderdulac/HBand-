package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.hband.AutoMeasureUiState
import com.example.data.hband.DeviceCapabilities
import com.example.data.hband.HistorySyncUiState
import com.example.data.hband.WearDetectUiState
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
class BandSdkSettingsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun band_sdk_settings_probed_light_theme() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BandSdkSettingsCard(
                    capabilities = DeviceCapabilities(
                        historyDays = 7,
                        originProtocolVersion = 3,
                        isSupportAutoMeasure = true,
                        isSupportPreciseSleep = true,
                        isSupportWearDetect = true,
                        isSupportSpo2 = true,
                        isSupportSpo2AutoDetect = true,
                        isSupportHrv = true,
                        isSupportHeart = true,
                        isSupportBp = true,
                        probed = true,
                    ),
                    autoMeasure = AutoMeasureUiState(
                        supported = true,
                        heartRateEnabled = true,
                        spo2NightAutoEnabled = true,
                        spo2AutoSupported = true,
                    ),
                    wearDetect = WearDetectUiState(
                        supported = true,
                        enabled = true,
                        lastWorn = true,
                    ),
                    historySync = HistorySyncUiState(
                        isRunning = false,
                        phase = "done",
                        originSamples = 18,
                        sleepDays = 3,
                        hrvSamples = 6,
                        spo2Samples = 4,
                        lastCompletedAtMs = 1_725_000_000_000L,
                    ),
                    hardwareConnected = true,
                    onAutoMeasureChange = {},
                    onSpo2AutoChange = {},
                    onWearDetectChange = {},
                    onSyncHistory = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/band_sdk_settings_p0.png")
    }
}
