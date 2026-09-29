package com.example.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class BleLabFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lab_remains_identified_while_showing_the_existing_content() {
        compose.setContent { MyApplicationTheme { BleLabFrame(true) { Text("Relógio") } } }
        compose.onNodeWithText("VE30 ENSAIO", substring = true).assertExists()
        compose.onNodeWithText("Relógio").assertExists()
    }

    @Test fun normal_app_keeps_content_without_lab_claim() {
        compose.setContent { MyApplicationTheme { BleLabFrame(false) { Text("Relógio") } } }
        compose.onNodeWithText("VE30 ENSAIO", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Relógio").assertExists()
    }
}
