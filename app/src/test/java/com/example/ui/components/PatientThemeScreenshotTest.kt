package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.hband.DeviceCapabilities
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import com.example.ui.theme.AppThemeOption
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Visual review of the redesign. Fixture telemetry is labelled as demonstration data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xhdpi", sdk = [36], application = android.app.Application::class)
class PatientThemeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun light_home_vital_cards() = capture("redesign_home_light") {
        MyApplicationTheme(AppThemeOption.LIGHT) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TelemetryGauges(demoTelemetry(), capabilities = probedCaps(), history = demoHistory())
                }
            }
        }
    }

    @Test
    fun light_history_trend() = capture("redesign_history_trend") {
        MyApplicationTheme(AppThemeOption.LIGHT) {
            Surface(Modifier.fillMaxSize()) {
                RechartsSensorDashboard(
                    demoHistory(),
                    isScrollable = true,
                    initialMetric = ChartMetricType.BLOOD_PRESSURE,
                )
            }
        }
    }

    @Test
    fun settings_theme_picker_light() = captureTheme(AppThemeOption.LIGHT, "redesign_settings_claro")

    @Test
    fun settings_theme_picker_dark_blue() = captureTheme(AppThemeOption.DARK_BLUE, "redesign_settings_escuro_azul")

    @Test
    fun settings_theme_picker_dark_indigo() = captureTheme(AppThemeOption.DARK_INDIGO, "redesign_settings_escuro_indigo")

    @Test
    fun settings_theme_picker_dark_graphite() = captureTheme(AppThemeOption.DARK_GRAPHITE, "redesign_settings_escuro_grafite")

    private fun captureTheme(option: AppThemeOption, name: String) = capture(name) {
        MyApplicationTheme(option) {
            Surface(Modifier.fillMaxSize()) {
                ThemeSelectorCard(themeOption = option, onThemeOptionChange = {}, modifier = Modifier.padding(16.dp))
            }
        }
    }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { content() }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    private fun probedCaps() = DeviceCapabilities(
        probed = true,
        isSupportHeart = true,
        isSupportSpo2 = true,
        isSupportBp = true,
        isSupportTemperature = true,
    )

    private fun demoTelemetry() = HBandTelemetry(
        timestamp = "2026-10-12T12:12:00Z",
        heartRate = 72,
        bloodPressure = BloodPressure(138, 88),
        spO2 = 97,
        temperatureCelsius = 36.5f,
        steps = 0,
        calories = 0f,
        distanceMeters = 0f,
        hrvScore = 0,
        sleepSummary = SleepSummary(),
        isRealSensorData = false,
    )

    private fun demoHistory(): List<HBandSensorMetricEntity> {
        val now = System.currentTimeMillis()
        return (0..6).map { daysAgo ->
            HBandSensorMetricEntity(
                deviceId = "fixture",
                timestamp = "fixture",
                timestampMillis = now - daysAgo * 24L * 60 * 60 * 1000,
                heartRate = 70 + daysAgo,
                systolicBp = 130 + daysAgo,
                diastolicBp = 80,
                spO2 = 96,
                temperatureCelsius = 36.4f,
                steps = 0,
                calories = 0f,
                distanceMeters = 0f,
                hrvScore = 0,
                deepSleepMinutes = 0,
                lightSleepMinutes = 0,
                awakeMinutes = 0,
            )
        }
    }
}
