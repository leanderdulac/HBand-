package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientSyncHistoryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun only_explicit_send_requests_work_and_non_failure_states_remain_distinct() {
        var sends = 0
        val logs = listOf("CANCELLED", "BLOCKED", "UNKNOWN_FUTURE_STATE", "FAILED").map { state ->
            SyncLogEntry(state, "Origem de teste", 0L, state, "Registro de teste")
        }
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SyncHistoryLog(logs, { sends++ }, { error("Unchanged callback must be preserved") })
            }
        } }
        compose.onNodeWithText("CANCELADO").assertExists()
        compose.onNodeWithText("AGUARDANDO OUTRA TAREFA").assertExists()
        compose.onNodeWithText("SITUAÇÃO NÃO RECONHECIDA").assertExists()
        compose.onAllNodesWithText("FALHOU").assertCountEquals(1)
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithText("Tentar enviar registros").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, sends) }
        compose.onNodeWithText("Com falha (1)").performScrollTo().performClick()
        compose.onNodeWithText("CANCELADO").assertDoesNotExist()
        compose.onNodeWithText("AGUARDANDO OUTRA TAREFA").assertDoesNotExist()
        compose.onNodeWithText("SITUAÇÃO NÃO RECONHECIDA").assertDoesNotExist()
        compose.onAllNodesWithText("FALHOU").assertCountEquals(1)
        compose.runOnIdle { assertEquals(1, sends) }
    }
}
