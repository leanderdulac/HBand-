package com.example.ui.components

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w720dp-h360dp-mdpi")
    fun short_screen_keeps_the_connection_action_and_profile_visible() {
        RuntimeEnvironment.setFontScale(1f)
        var profileOpened = false
        compose.setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 24.dp),
                    bottomBar = { PatientNavigationBar(0, {}) },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        HomeWelcomeHeader("Nome de teste", "patient-fixture", { profileOpened = true })
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            DeviceControlCard(null, true, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        val button = compose.onNodeWithTag("connect_watch_button").fetchSemanticsNode()
        val navigation = compose.onNodeWithTag("main_tab_row").fetchSemanticsNode()
        assertTrue("The connection action must fit in the initial short-screen viewport",
            button.positionInRoot.y + button.size.height <= navigation.positionInRoot.y)
        compose.onNodeWithTag("header_edit_profile_button").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(profileOpened) }
        compose.onNodeWithTag("next2u_brand_logo").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun extreme_text_navigation_leaves_the_main_connection_action_fully_visible() {
        RuntimeEnvironment.setFontScale(2f)
        var route = -1
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MyApplicationTheme {
                    // Reserve the system edges observed on the isolated Pixel 7
                    // emulator; Robolectric does not provide its physical cutout.
                    Scaffold(
                        modifier = Modifier.fillMaxSize().padding(top = 36.dp, bottom = 24.dp),
                        bottomBar = { PatientNavigationBar(0) { route = it } },
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            HomeWelcomeHeader("", "patient-fixture", {})
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                                DeviceControlCard(null, true, {}, {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/extreme_navigation.png")
        val button = compose.onNodeWithTag("connect_watch_button").fetchSemanticsNode()
        val navigation = compose.onNodeWithTag("main_tab_row").fetchSemanticsNode()
        assertTrue("The complete connection button must fit above navigation",
            button.positionInRoot.y + button.size.height <= navigation.positionInRoot.y)
        listOf(0 to "tab_dashboard", 2 to "tab_ble", 4 to "tab_settings").forEach { (index, tag) ->
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(index, route) }
        }
    }

    @Test fun large_text_keeps_all_destinations_visible_and_preserves_routes() {
        RuntimeEnvironment.setFontScale(1.6f)
        var route = -1
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                MyApplicationTheme { PatientNavigationBar(0) { route = it } }
            }
        }
        compose.onNodeWithTag("tab_dashboard").assertIsSelected()
        listOf(0 to "tab_dashboard", 2 to "tab_ble", 4 to "tab_settings").forEach { (index, tag) ->
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(index, route) }
        }
    }


}
