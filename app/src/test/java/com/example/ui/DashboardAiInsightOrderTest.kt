package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.repository.ApiHealthState
import com.example.ui.components.SyncDisplayStatus
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Visão geral order: highlighted AI summary right after "Meu relógio" only when the flag is on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xhdpi", sdk = [36], application = android.app.Application::class)
class DashboardAiInsightOrderTest {
    @get:Rule val compose = createComposeRule()

    private fun show(aiInsightEnabled: Boolean) = compose.setContent {
        MyApplicationTheme {
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                DashboardTab(

                    connectedDevice = null, latestTelemetry = null, sensorMetrics = emptyList(),
                    autoIngestLive = true,

                    onToggleAutoIngest = {}, onSpotCheck = {}, onShowNotification = {},
                    onScanClick = {}, onDisconnect = {},
                    geminiInsightText = "Nos últimos 7 dias, sua frequência cardíaca média foi de 72 BPM, dentro da faixa esperada.",
                    aiInsightEnabled = aiInsightEnabled,
                )
            }
        }
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y // unclipped, also below the fold
    private fun topOfText(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y // unclipped, also below the fold

    @Test fun flag_on_places_highlight_after_watch_and_before_readings() {
        show(aiInsightEnabled = true)
        val watch = top("device_control_card")
        val insight = top("ai_insight_highlight_card")
        val latest = top("latest_watch_reading")
        val today = top("daily_health_summary_card")
        assertTrue("watch=$watch insight=$insight", watch < insight)
        assertTrue("insight=$insight latest=$latest", insight < latest)
        assertTrue("latest=$latest today=$today", latest < today)
        // Always expanded, not collapsible: no section header, text visible without a tap.
        compose.onNodeWithText("Resumo com inteligência artificial").assertDoesNotExist()
        compose.onNodeWithTag("gemini_insight_text").assertIsDisplayed()
        compose.onNodeWithText("Resumo automático em validação").assertDoesNotExist()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/dashboard_ai_insight_on.png")
    }

    @Test fun flag_off_keeps_collapsed_section_after_the_other_sections() {
        show(aiInsightEnabled = false)
        compose.onNodeWithTag("ai_insight_highlight_card").assertDoesNotExist()
        val today = top("daily_health_summary_card")
        val share = topOfText("Compartilhar registros")
        val section = topOfText("Resumo com inteligência artificial")
        assertTrue("today=$today section=$section", today < section)
        assertTrue("share=$share section=$section", share < section)
        compose.onNodeWithTag("gemini_health_insight_card").assertDoesNotExist() // collapsed as today
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/dashboard_ai_insight_off.png")
    }
}
