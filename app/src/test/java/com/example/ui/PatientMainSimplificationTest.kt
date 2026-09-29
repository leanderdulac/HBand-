package com.example.ui

import android.Manifest
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.HBandDevice
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi", application = Application::class)
class PatientMainSimplificationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remaining_menu_destinations_are_selectable() {
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            MyApplicationTheme { PatientMainNavigation(selected) { selected = it } }
        }
        compose.onNodeWithTag("tab_recharts").assertDoesNotExist()
        compose.onNodeWithTag("tab_queue").assertDoesNotExist()
        for (tag in listOf("tab_ble", "tab_settings", "tab_dashboard")) {
            compose.onNodeWithTag(tag).performClick().assertIsSelected()
        }
        compose.onRoot().captureRoboImage("build/reports/patient-main/menu.png")
    }

    @Test fun dashboard_omits_removed_sections_and_preserves_vitals() {
        compose.setContent {
            MyApplicationTheme {
                DashboardTab(
                    connectedDevice = null,
                    latestTelemetry = null,
                    sensorMetrics = emptyList(),
                    autoIngestLive = false,
                    onToggleAutoIngest = {},
                    onSpotCheck = {},
                    onScanClick = {},
                    onDisconnect = {},
                )
            }
        }
        for (tag in listOf("advanced_detect_card", "hydration_card", "recharts_sensor_dashboard",
            "csv_export_card", "sync_history_log_card", "api_header_card", "workmanager_sync_status_card")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        compose.onNodeWithText("Análise do Banco de Dados Room").assertDoesNotExist()
        compose.onNodeWithTag("device_control_card").assertExists()
        compose.onNodeWithTag("gemini_health_insight_card").assertExists()
        compose.onRoot().captureRoboImage("build/reports/patient-main/overview.png")
    }

    @Test fun scanner_keeps_search_connect_and_disconnect_without_other_device_sections() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val found = HBandDevice(deviceId = "test-ve30", name = "VE30 de teste",
            macAddress = "AA:BB:CC:DD:EE:FF", isConnected = false, firmwareVersion = "")
        var scans = 0
        var connects = 0
        var disconnects = 0
        compose.setContent {
            var connected by remember { mutableStateOf<HBandDevice?>(null) }
            MyApplicationTheme {
                BleDevicesTab(
                    scannedDevices = listOf(found), connectedDevice = connected, isScanning = false,
                    onStartScan = { scans++ },
                    onConnectDevice = { connects++; connected = it.copy(isConnected = true) },
                    onDisconnectDevice = { disconnects++; connected = null },
                )
            }
        }
        compose.onNodeWithText("Scanner BLE HBand & VE30").assertIsDisplayed()
        compose.onNodeWithTag("scan_ble_button").performClick()
        compose.runOnIdle { assertEquals(1, scans) }
        compose.onNodeWithText("Conectar").performClick()
        compose.runOnIdle { assertEquals(1, connects) }
        compose.onNodeWithText("Desconectar").performClick()
        compose.runOnIdle { assertEquals(1, disconnects) }
        for (tag in listOf("quick_connect_gears3_card", "quick_connect_ve30_card", "custom_mac_input")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        compose.onNodeWithText("Arquitetura do Pipeline HBand SDK").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/reports/patient-main/devices.png")
    }

    @Test fun scanning_disables_duplicate_search_and_empty_state_has_no_mac_instruction() {
        compose.setContent {
            MyApplicationTheme {
                BleDevicesTab(emptyList(), null, true, {}, {}, {})
            }
        }
        compose.onNodeWithTag("scan_ble_button").assertIsNotEnabled()
        compose.onNodeWithText("Buscando...").assertIsDisplayed()
        compose.onNodeWithText("Toque em 'Buscar dispositivos' para encontrar seu VE30.").assertExists()
        compose.onNodeWithText("Conexão Direta por Endereço MAC").assertDoesNotExist()
    }
}
