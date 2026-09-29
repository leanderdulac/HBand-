package com.example.ui

import android.app.Application
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h740dp-mdpi", sdk = [36], application = Application::class)
class PatientNotificationEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replaced_snackbar_does_not_acknowledge_the_cancelled_message() {
        checkReplacement("Tentativa solicitada", "Não foi possível atualizar a fila")
    }

    @Test fun identical_replacement_acknowledges_the_new_event() {
        checkReplacement("Falha no envio", "Falha no envio")
    }

    private fun checkReplacement(firstText: String, nextText: String) {
        val store = PatientNotificationState()
        val host = SnackbarHostState()
        val acknowledgements = mutableListOf<UiNotification>()
        store.post(firstText, true)
        val first = store.notification.value!!
        compose.setContent { MyApplicationTheme {
            val current = store.notification.collectAsState().value
            PatientNotificationEffect(current, host) {
                acknowledgements += it
                store.dismiss(it)
            }
            SnackbarHost(host)
        } }
        compose.onNodeWithText(firstText).assertIsDisplayed()
        compose.runOnIdle { store.post(nextText, true) }
        val next = store.notification.value!!
        compose.onNodeWithText(nextText).assertIsDisplayed()
        compose.runOnIdle { assertTrue(acknowledgements.isEmpty()); assertNotEquals(first.id, next.id) }
        compose.onNodeWithText("Fechar").performClick()
        compose.runOnIdle {
            assertEquals(listOf(next), acknowledgements)
            assertNull(store.notification.value)
        }
    }
}
