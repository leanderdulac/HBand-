package com.example.ui.components

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.IngestQueueEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.queuePresentationState
import org.junit.Assert.assertEquals
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = Application::class)
class QueueLoadingRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pending_query_does_not_claim_no_pending_records() = pendingQuery {
        compose.onNodeWithText("A fila exibida não tem envios pendentes.", substring = true).assertDoesNotExist()
    }

    @Test fun pending_query_does_not_claim_empty_list() = pendingQuery {
        compose.onNodeWithText("Ver registros da fila").performScrollTo().performClick()
        compose.onNodeWithText("A lista exibida está vazia.").assertDoesNotExist()
    }

    private fun pendingQuery(check: () -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // Production screen sharing policy; no operational ViewModel startup.
        val state = flow<List<IngestQueueEntity>> { awaitCancellation() }
            .queuePresentationState(scope)
        try {
            compose.setContent { MyApplicationTheme {
                QueueInspector(
                    pendingCount = 0, syncedCount = 0, failedCount = 0,
                    queueItems = state.collectAsState().value?.items, syncLogs = emptyList(), isSyncing = false,
                    onSyncNow = { error("Unexpected send") }, onRetryFailedItem = {}, onRetryAllFailed = {},
                    onDeleteItem = {}, onClearSynced = {}, onClearAll = {}, onInspectItem = {}, onRefreshWorkManager = {},
                )
            } }
            check()
        } finally { scope.cancel() }
    }

    @Test fun loading_hides_actions_and_discards_an_open_removal_confirmation() {
        val items = mutableStateOf<List<IngestQueueEntity>?>(listOf(IngestQueueEntity(id = 57, payloadJson = "{}", status = "FAILED")))
        var actions = 0
        compose.setContent { MyApplicationTheme {
            QueueInspector(0, 0, 1, items.value, emptyList(), false,
                { actions++ }, { actions++ }, { actions++ }, { actions++ },
                { actions++ }, { actions++ }, { actions++ }, { actions++ })
        } }
        compose.onNodeWithText("Detalhes para suporte").performScrollTo().performClick()
        compose.onNodeWithTag("delete_queue_57").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_queue_removal").assertExists()
        compose.runOnIdle { items.value = null }
        compose.onNodeWithTag("confirm_queue_removal").assertDoesNotExist()
        compose.onNodeWithTag("trigger_workmanager_sync_button").assertDoesNotExist()
        compose.onNodeWithText("Registros com falha no envio: 1").assertDoesNotExist()
        compose.onNodeWithText("Detalhes para suporte").assertDoesNotExist()
        compose.onNodeWithTag("sync_status_message").assertTextContains("Carregando fila…")
        compose.runOnIdle { items.value = emptyList() }
        compose.onNodeWithTag("confirm_queue_removal").assertDoesNotExist()
        compose.onNodeWithText("Ver registros da fila").performScrollTo().performClick()
        compose.onNodeWithText("A lista exibida está vazia.").assertExists()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test fun navigation_announces_loading_then_confirmed_zero_then_exact_count() {
        val count = mutableStateOf<Int?>(null)
        compose.setContent { MyApplicationTheme { PatientNavigationBar(3, count.value, {}) } }
        fun description(text: String) = compose.onNodeWithTag("tab_queue")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text))
        description("Carregando fila do aplicativo")
        compose.runOnIdle { count.value = 0 }
        description("Sem registros pendentes no aplicativo")
        compose.runOnIdle { count.value = 125 }
        description("125 registros pendentes no aplicativo")
        compose.runOnIdle { count.value = null }
        description("Carregando fila do aplicativo")
    }

    @Test fun dashboard_loading_hides_stale_counts_and_send_actions() {
        compose.setContent { MyApplicationTheme {
            SyncStatusIndicator(SyncDisplayStatus.LOADING, 9, 2, 4, { error("Unexpected send") })
        } }
        compose.onNodeWithTag("sync_status_message").assertTextContains("Carregando fila…")
        compose.onNodeWithText("Aguardando envio: 9").assertDoesNotExist()
        compose.onNodeWithText("Registros com falha no envio: 4").assertDoesNotExist()
        compose.onNodeWithTag("trigger_workmanager_sync_button").assertDoesNotExist()
    }
}
