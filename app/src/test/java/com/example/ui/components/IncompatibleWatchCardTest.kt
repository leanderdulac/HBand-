package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.hband.VeepooDeviceSupport
import com.example.data.model.HBandDevice
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class IncompatibleWatchCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shows_copy_once_and_forget_unbinds() {
        var forgotten = 0
        val watch = HBandDevice(name = "Galaxy Watch8", macAddress = "AA:BB:CC:DD:EE:FF")
        compose.setContent {
            MyApplicationTheme {
                IncompatibleWatchCard(device = watch, onForget = { forgotten++ })
            }
        }
        compose.onNodeWithTag("incompatible_watch_card").assertIsDisplayed()
        compose.onNodeWithText(VeepooDeviceSupport.INCOMPATIBLE_CARD_TEXT).assertIsDisplayed()
        compose.onNodeWithText("Galaxy Watch8").assertIsDisplayed()
        compose.onNodeWithTag("btn_forget_watch").performClick()
        compose.runOnIdle { assertEquals(1, forgotten) }
    }

    @Test
    fun hidden_when_there_is_no_device() {
        compose.setContent {
            MyApplicationTheme { IncompatibleWatchCard(device = null, onForget = {}) }
        }
        compose.onNodeWithTag("incompatible_watch_card").assertDoesNotExist()
    }
}
