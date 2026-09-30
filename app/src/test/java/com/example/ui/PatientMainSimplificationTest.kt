package com.example.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientMainSimplificationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun main_navigation_has_no_history_or_queue_destination() {
        compose.setContent { MyApplicationTheme { PatientNavigationBar(0, {}) } }
        compose.onNodeWithTag("tab_recharts").assertDoesNotExist()
        compose.onNodeWithTag("tab_queue").assertDoesNotExist()
        listOf("tab_dashboard", "tab_ble", "tab_settings").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
    }

    @Test fun restored_removed_destinations_return_home_and_existing_routes_keep_their_identity() {
        listOf(-1, 1, 3, 5).forEach { org.junit.Assert.assertEquals(0, patientMainTab(it)) }
        listOf(0, 2, 4).forEach { org.junit.Assert.assertEquals(it, patientMainTab(it)) }
    }

    @Test fun dashboard_removes_requested_sections() {
        compose.setContent { MyApplicationTheme {
            DashboardTab(

                connectedDevice = null, latestTelemetry = null, sensorMetrics = emptyList(),
                autoIngestLive = true,

                onToggleAutoIngest = {}, onSpotCheck = {}, onShowNotification = {},
                onScanClick = {}, onDisconnect = {},

            )
        } }
        listOf("Medições do relógio", "Água no dia a dia", "Informações para suporte").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        listOf("home_history_button", "workmanager_sync_status_card", "recharts_sensor_dashboard",
            "csv_export_card", "sync_history_log_card", "api_header_card").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("device_control_card").assertExists()
        compose.onNodeWithText("Sono").assertExists()
        compose.onNodeWithText("Compartilhar registros").assertExists()
    }

    @Test fun watch_screen_exposes_scanner_without_manual_address_entry() {
        compose.setContent { MyApplicationTheme {
            PatientWatchContent(emptyList(), null, false, null, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("scan_ble_button").assertIsDisplayed()
        compose.onNodeWithTag("watch_results_heading").assertExists()
        compose.onNodeWithTag("watch_support_button").assertDoesNotExist()
        compose.onNodeWithTag("custom_mac_input").assertDoesNotExist()
    }
}
