package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h900dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientAdaptiveLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resizing_preserves_navigation_and_unsaved_content() {
        val width = mutableStateOf(1000.dp)
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.width(width.value).height(800.dp)) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    PatientAdaptiveScaffold(tab, 125, { tab = it }, header = {}) {
                        var draft by rememberSaveable { mutableStateOf("") }
                        OutlinedTextField(draft, { draft = it }, Modifier.testTag("draft"))
                    }
                }
            }
        }
        compose.onNodeWithTag("patient_navigation_rail").assertIsDisplayed()
        compose.onNodeWithTag("draft").performTextInput("Rascunho de teste")
        compose.onNodeWithTag("tab_queue").performClick().assertIsSelected().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "125 registros pendentes no aplicativo"),
        )
        compose.runOnIdle { width.value = 360.dp }
        compose.onNodeWithTag("patient_navigation_rail").assertDoesNotExist()
        compose.onNodeWithTag("main_tab_row").assertIsDisplayed()
        compose.onNodeWithTag("tab_queue").assertIsSelected()
        compose.onNodeWithTag("draft").assertTextContains("Rascunho de teste")
        compose.runOnIdle { width.value = 1000.dp }
        compose.onNodeWithTag("tab_queue").assertIsSelected()
        compose.onNodeWithTag("draft").assertTextContains("Rascunho de teste")
    }

    @Test fun tablet_portrait_keeps_all_five_destinations_and_connection_visible() = showHome(600, 900, 1f, true, false)

    @Test fun tablet_landscape_uses_two_readable_columns() = showHome(960, 600, 1f, true, true)

    @Test fun phone_portrait_keeps_bottom_navigation_and_single_column() = showHome(360, 740, 1f, false, false)

    @Test fun small_phone_with_extreme_type_keeps_connection_visible() = showHome(320, 640, 2f, false, false)

    @Test fun short_phone_landscape_keeps_connection_above_bottom_navigation() = showHome(720, 360, 1f, false, false)

    @Test fun large_type_reflows_tablet_summary_without_shrinking_text() = showHome(960, 600, 1.6f, true, false)

    @Test fun summary_reflow_preserves_local_field_state() {
        val width = mutableStateOf(900.dp)
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.width(width.value)) {
                    PatientSummaryLayout(first = { Text("Relógio") }, second = {
                        var draft by remember { mutableStateOf("") }
                        OutlinedTextField(draft, { draft = it }, Modifier.testTag("summary_draft"))
                    })
                }
            }
        }
        compose.onNodeWithTag("summary_draft").performTextInput("Preservar")
        compose.runOnIdle { width.value = 360.dp }
        compose.onNodeWithTag("summary_draft").assertTextContains("Preservar")
        compose.runOnIdle { width.value = 900.dp }
        compose.onNodeWithTag("summary_draft").assertTextContains("Preservar")
    }

    private fun showHome(width: Int, height: Int, scale: Float, rail: Boolean, columns: Boolean) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp-mdpi")
        RuntimeEnvironment.setFontScale(scale)
        var selected = -1
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MyApplicationTheme {
                    PatientAdaptiveScaffold(0, 5, { selected = it },
                        modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 24.dp),
                        header = { HomeWelcomeHeader("", "", {}) },
                    ) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                            PatientSummaryLayout(first = { DeviceControlCard(null, true, {}, {}, {}, {}, {}) }, second = {
                                TelemetryGauges(null)
                                DailyHealthSummaryCard(emptyList())
                            })
                        }
                    }
                }
            }
        }
        if (rail) compose.onNodeWithTag("patient_navigation_rail").assertIsDisplayed()
        else compose.onNodeWithTag("main_tab_row").assertIsDisplayed()
        val button = compose.onNodeWithTag("connect_watch_button").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("patient_content").fetchSemanticsNode().boundsInRoot
        assertTrue("Connection action fits fully within the content viewport", button.bottom <= content.bottom)
        // Unclipped coordinates also cover the second group below the scroll viewport.
        val firstNode = compose.onNodeWithTag("patient_summary_first").fetchSemanticsNode()
        val secondNode = compose.onNodeWithTag("patient_summary_second").fetchSemanticsNode()
        val first = firstNode.positionInRoot
        val second = secondNode.positionInRoot
        if (columns) {
            assertEquals(first.y, second.y, 1f)
            assertTrue(second.x >= first.x + firstNode.size.width)
        } else {
            assertEquals(first.x, second.x, 1f)
            assertTrue(second.y >= first.y + firstNode.size.height)
        }
        listOf("tab_dashboard", "tab_recharts", "tab_ble", "tab_queue", "tab_settings").forEachIndexed { index, tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(index, selected) }
        }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/adaptive-${width}x${height}-${scale}.png")
    }
}
