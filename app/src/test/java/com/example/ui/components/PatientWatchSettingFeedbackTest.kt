package com.example.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.hband.*
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

/** Presentation receipts are synthetic; no SDK, storage, permission or radio action. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w320dp-h740dp-mdpi")
class PatientWatchSettingFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private val status = mutableStateOf(WatchSettingConfirmation.UNKNOWN)
    private var requests = 0
    @After fun reset() { RuntimeEnvironment.setFontScale(1f) }
    private fun content(font: Float = 1f) {
        RuntimeEnvironment.setFontScale(font)
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { BandSdkSettingsCard(
                capabilities = DeviceCapabilities(probed = true),
                autoMeasure = AutoMeasureUiState(supported = true, spo2AutoSupported = true,
                    heartRateConfirmation = status.value, spo2Confirmation = status.value),
                wearDetect = WearDetectUiState(supported = true, confirmation = status.value),
                historySync = HistorySyncUiState(), hardwareConnected = true,
                onAutoMeasureChange = { requests++ }, onSpo2AutoChange = { requests++ },
                onWearDetectChange = { requests++ }, onSyncHistory = {},
            ) }
        } }
    }
    @Test fun unconfirmed_initial_values_are_not_presented_as_received_results() {
        content()
        compose.onNodeWithTag("auto_measure_result").assertTextContains("ainda não confirmada", substring = true)
        compose.onAllNodesWithText("Último retorno recebido do relógio.").assertCountEquals(0)
    }
    @Test fun pending_values_disable_repetition_then_failure_allows_explicit_retry() {
        status.value = WatchSettingConfirmation.PENDING; content()
        for (tag in listOf("auto_measure_switch", "spo2_auto_switch", "wear_detect_switch")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled().assertIsOff().performClick()
        }
        compose.runOnIdle { assertEquals(0, requests); status.value = WatchSettingConfirmation.UNCONFIRMED }
        compose.onNodeWithTag("auto_measure_result").assertTextContains("parte da solicitação pode ter sido aplicada", substring = true)
        compose.onNodeWithTag("auto_measure_switch").performScrollTo().assertIsEnabled().assertIsOff().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }
    @Test fun sdk_confirmation_is_described_as_a_received_result() {
        status.value = WatchSettingConfirmation.CONFIRMED; content()
        compose.onNodeWithTag("auto_measure_result").assertTextEquals("Último retorno recebido do relógio.")
        compose.runOnIdle { assertEquals(0, requests) }
    }
    @Test @Config(qualifiers = "w640dp-h320dp-mdpi")
    fun uncertainty_and_retry_remain_accessible_on_a_short_screen_with_large_type() {
        status.value = WatchSettingConfirmation.UNCONFIRMED; content(2f)
        for ((tag, name) in listOf("auto_measure_result" to "warning", "auto_measure_switch" to "retry")) {
            val node = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            assertTrue(node.boundsInRoot.height >= node.size.height - 1f)
            compose.onRoot().captureRoboImage(filePath = "build/watch-settings-$name.png")
        }
        compose.runOnIdle { assertEquals(0, requests) }
    }
}
