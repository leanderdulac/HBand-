package com.example.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.data.local.StorageStartupState
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class StorageStartupScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opening_and_failure_do_not_compose_operational_ui() {
        val state = mutableStateOf(StorageStartupState.OPENING)
        var entered = false
        compose.setContent { MyApplicationTheme { StorageStartupScreen(state.value) { entered = true; Text("Operational") } } }
        compose.onNodeWithText("Abrindo seus registros").assertIsDisplayed()
        compose.runOnIdle { assertFalse(entered); state.value = StorageStartupState.UNAVAILABLE }
        compose.onNodeWithText("Não foi possível abrir seus registros").assertIsDisplayed()
        compose.onNodeWithText("Operational").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle { assertFalse(entered) }
    }

    @Test fun readiness_releases_content() {
        compose.setContent { StorageStartupScreen(StorageStartupState.READY) { Text("Operational") } }
        compose.onNodeWithText("Operational").assertIsDisplayed()
        compose.onNodeWithTag("storage_startup_screen").assertDoesNotExist()
    }

    @Test fun rotation_while_opening_does_not_skip_permissions() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(StorageStartupState.OPENING)
        var requests = 0
        restoration.setContent { RequestPermissionsWhenStorageReady(state.value) { requests++ } }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(0, requests); state.value = StorageStartupState.READY }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, requests) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun unavailable_never_requests_permissions() {
        var requests = 0
        compose.setContent { RequestPermissionsWhenStorageReady(StorageStartupState.UNAVAILABLE) { requests++ } }
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun phone_large_type_guidance_is_readable() = layout(2f, "startup_phone_large")

    @Test @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun tablet_guidance_is_readable() = layout(1.6f, "startup_tablet_large")

    private fun layout(scale: Float, screenshot: String) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MyApplicationTheme { StorageStartupScreen(StorageStartupState.UNAVAILABLE) { error("No clinical UI") } }
            }
        }
        for (label in listOf("Não foi possível abrir seus registros",
            "A coleta pelo aplicativo e os envios não foram iniciados nesta abertura.",
            "Não limpe os dados, não desinstale o aplicativo e não tente substituir a chave do banco.",
            "Peça ajuda à equipe responsável pela instalação para verificar o armazenamento e recuperar o acesso.",
            "Esta tela não confirma a integridade dos registros nem recupera dados automaticamente.")) {
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$label: " + layouts.map { "size=${it.size}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}, bottom=${it.getLineBottom(it.lineCount - 1)}" },
                layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/$screenshot.png")
        compose.onNodeWithText("Não foi possível abrir seus registros").performScrollTo()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/${screenshot}_top.png")
    }
}
