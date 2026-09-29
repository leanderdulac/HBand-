package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36], application = android.app.Application::class)
class HomeWelcomeHeaderScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun visao_geral_header_shows_next2u_logo() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeWelcomeHeader(
                    fullName = "Alex Rivera",
                    patientId = "PAT-HBAND-001",
                    onEditProfile = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White),
                )
            }
        }
        composeTestRule.onNodeWithTag("next2u_brand_logo").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/home_welcome_next2u_logo.png",
        )
    }
}
