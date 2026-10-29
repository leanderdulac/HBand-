package com.example.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.IngestQueueEntity
import com.example.data.repository.ApiHealthState
import com.example.ui.QueuePresentationState
import com.example.ui.displayStatus
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = Application::class)
class ApiHealthPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unverified_service_is_not_an_observed_failure_in_dashboard() {
        val queue = QueuePresentationState(listOf(IngestQueueEntity(id = 57, payloadJson = "{}", status = "PENDING")))
        assertNotEquals(SyncDisplayStatus.OFFLINE, queue.displayStatus(false, ApiHealthState()))
    }

    @Test fun initial_header_does_not_claim_disconnection() {
        compose.setContent { MyApplicationTheme { ApiHeader(ApiHealthState(), {}) } }
        compose.onNodeWithText("DESCONECTADO").assertDoesNotExist()
    }

    @Test fun health_check_does_not_claim_to_check_ingest_endpoint() {
        compose.setContent { MyApplicationTheme { ApiHeader(ApiHealthState(), {}) } }
        compose.onNodeWithText("Endpoint: /api/v1/wearables/ingest").assertDoesNotExist()
    }

    @Test fun no_response_timestamp_means_unknown_even_with_inconsistent_online_flag() {
        compose.setContent { MyApplicationTheme { ApiHeader(ApiHealthState(isOnline = true), {}) } }
        compose.onNodeWithText("Ainda não verificado").assertExists()
        compose.onNodeWithTag("api_health_checked_at", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun completed_result_is_historical_during_refresh_and_changes_only_on_new_response() {
        val health = mutableStateOf(ApiHealthState())
        var requests = 0
        compose.setContent { MyApplicationTheme { ApiHeader(health.value, { requests++ }) } }
        compose.onNodeWithTag("api_health_result")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Ainda não verificado")
        compose.runOnIdle { health.value = ApiHealthState(isOnline = true, statusCode = 200, lastCheckTime = 1790506800000) }
        compose.onNodeWithText("Serviço respondeu à última verificação").assertExists()
        val before = compose.onNodeWithTag("api_health_checked_at", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithTag("ping_api_button").performClick()
        // The transport has not responded. UI keeps explicitly historical evidence.
        compose.onNodeWithText("Serviço respondeu à última verificação").assertExists()
        assertEquals(before, compose.onNodeWithTag("api_health_checked_at", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.runOnIdle { assertEquals(1, requests); health.value = ApiHealthState(statusCode = 503, lastCheckTime = 1790506860000) }
        compose.onNodeWithText("Última verificação do serviço falhou").assertExists()
        assertNotEquals(before, compose.onNodeWithTag("api_health_checked_at", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text])
        for (code in listOf(401, 403)) {
            compose.runOnIdle { health.value = ApiHealthState(statusCode = code, lastCheckTime = 1790506860000) }
            compose.onNodeWithText("Verificação recusada pelo serviço").assertExists()
        }
        compose.runOnIdle { health.value = ApiHealthState() }
        compose.onNodeWithText("Ainda não verificado").assertExists()
        compose.onNodeWithTag("api_health_checked_at", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun unknown_service_preserves_queue_facts_and_observed_failure_still_displays() {
        val row = IngestQueueEntity(id = 57, payloadJson = "{}", status = "PENDING")
        assertEquals(SyncDisplayStatus.FULLY_SYNCED, QueuePresentationState(emptyList()).displayStatus(false, ApiHealthState()))
        assertEquals(SyncDisplayStatus.PENDING_QUEUE, QueuePresentationState(listOf(row)).displayStatus(false, ApiHealthState()))
        assertEquals(SyncDisplayStatus.FAILED, QueuePresentationState(listOf(row.copy(status = "FAILED"))).displayStatus(false, ApiHealthState()))
        val blocked = QueuePresentationState(listOf(row.copy(status = "FAILED", errorMessage = "Falha de autenticação na API HealthTech (HTTP 401): denied")))
        assertEquals(SyncDisplayStatus.AUTH_REQUIRED, blocked.displayStatus(false, ApiHealthState()))
        assertEquals(SyncDisplayStatus.SYNCING, blocked.displayStatus(true, ApiHealthState()))
        assertEquals(SyncDisplayStatus.LOADING, (null as QueuePresentationState?).displayStatus(false, ApiHealthState()))
        assertEquals(SyncDisplayStatus.OFFLINE, QueuePresentationState(listOf(row)).displayStatus(false, ApiHealthState(lastCheckTime = 1790506800000)))
    }

    @Test fun large_text_keeps_status_and_refresh_readable_on_narrow_screen() {
        var requests = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MyApplicationTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ApiHeader(ApiHealthState(), { requests++ })
                    }
                }
            }
        }
        compose.onNodeWithText("Ainda não verificado").assertIsDisplayed()
        val card = compose.onNodeWithTag("api_header_card").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText("Ainda não verificado", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(text.left >= card.left && text.right <= card.right)
        compose.onNodeWithTag("ping_api_button").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onRoot().captureRoboImage(filePath = "build/health-presentation-large-text.png")
    }
}
