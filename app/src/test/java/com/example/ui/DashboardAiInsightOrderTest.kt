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

/** Visão geral: highlighted AI summary always sits right after "Meu relógio". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xhdpi", sdk = [36], application = android.app.Application::class)
class DashboardAiInsightOrderTest {
    @get:Rule val compose = createComposeRule()

    private fun show(insightText: String = "Nos últimos 7 dias, sua frequência cardíaca média foi de 72 BPM, dentro da faixa esperada.") =
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    DashboardTab(
                        syncDisplayStatus = SyncDisplayStatus.FULLY_SYNCED,
                        pendingCount = 0, syncedCount = 0, failedCount = 0,
                        syncLogs = emptyList(), apiHealth = ApiHealthState(),
                        connectedDevice = null, latestTelemetry = null, sensorMetrics = emptyList(),
                        autoIngestLive = true,
                        onTriggerSync = {}, onRefreshHealth = {}, onRetryQueueRead = {},
                        onToggleAutoIngest = {}, onSpotCheck = {}, onShowNotification = {},
                        onScanClick = {}, onDisconnect = {},
                        geminiInsightText = insightText,
                    )
                }
            }
        }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    @Test fun highlight_is_after_watch_and_before_readings() {
        show()
        val watch = top("device_control_card")
        val insight = top("ai_insight_highlight_card")
        val latest = top("latest_watch_reading")
        val today = top("daily_health_summary_card")
        assertTrue("watch=$watch insight=$insight", watch < insight)
        assertTrue("insight=$insight latest=$latest", insight < latest)
        assertTrue("latest=$latest today=$today", latest < today)
        compose.onNodeWithText("Resumo com inteligência artificial").assertDoesNotExist()
        compose.onNodeWithTag("gemini_insight_text").assertIsDisplayed()
        compose.onNodeWithText("Resumo automático em validação").assertDoesNotExist()
        compose.onNodeWithText("Resumo com IA").assertExists()
        compose.onNodeWithText("Atualizar resumo").assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/dashboard_ai_insight_on.png")
    }

    @Test fun empty_state_keeps_the_card_visible() {
        show(insightText = "")
        compose.onNodeWithTag("ai_insight_highlight_card").assertExists()
        compose.onNodeWithTag("ai_insight_empty").assertExists()
        compose.onNodeWithText("Resumo com IA").assertExists()
        compose.onNodeWithTag("ai_insight_review_label").assertExists()
        compose.onNodeWithTag("refresh_gemini_insight_button").assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/dashboard_ai_insight_empty.png")
    }
}
