package com.example.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientQueueTest {
    @get:Rule val compose = createComposeRule()
    private val failed = IngestQueueEntity(id = 57, payloadJson = "{}", status = QueueStatus.FAILED.name)

    @Test fun persisted_auth_error_shows_pause_instead_of_internet_advice() {
        var retries = 0
        compose.setContent {
            MyApplicationTheme {
                QueueInspector(
                    pendingCount = 2, syncedCount = 0, failedCount = 1,
                    queueItems = listOf(failed.copy(errorMessage = "Falha de autenticação na API HealthTech (HTTP 401): denied")),
                    syncLogs = emptyList(), isSyncing = false,
                    onSyncNow = { error("Unexpected generic sync") }, onRetryFailedItem = {},
                    onRetryAllFailed = { retries++ }, onDeleteItem = {}, onClearSynced = {},
                    onClearAll = {}, onInspectItem = {}, onRefreshWorkManager = {},
                )
            }
        }
        compose.onNodeWithTag("sync_status_message").assertTextContains("Envios pausados: acesso não autorizado")
        compose.onNodeWithText("Registros com falha no envio: 1").assertExists()
        compose.onNodeWithText("Tentar após corrigir o acesso").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Confira a conexão do celular", substring = true).assertDoesNotExist()
    }

    @Test fun retry_preserves_the_queue_item_id() {
        var retried: Long? = null
        content(retry = { retried = it })
        compose.onNodeWithText("Ver registros da fila").performScrollTo().performClick()
        compose.onNodeWithTag("retry_queue_57").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(57L, retried) }
    }

    @Test fun delete_stays_hidden_and_requires_confirmation() {
        var deleted: Long? = null
        content(delete = { deleted = it })
        compose.onNodeWithTag("delete_queue_57").assertDoesNotExist()
        compose.onNodeWithText("Detalhes para suporte").performScrollTo().performClick()
        compose.onNodeWithTag("delete_queue_57").performScrollTo().performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithTag("confirm_queue_removal").performClick()
        compose.runOnIdle { assertEquals(57L, deleted) }
    }

    @Test fun cancelling_clear_all_preserves_every_record() {
        var clears = 0
        content(clear = { clears++ })
        compose.onNodeWithText("Detalhes para suporte").performScrollTo().performClick()
        compose.onNodeWithTag("clear_queue_button").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
    }

    @Test fun unknown_status_is_not_success() {
        assertEquals("Situação do envio indisponível", queueStatusLabel("UNKNOWN"))
        assertEquals("Concluído no aplicativo", queueStatusLabel("SYNCED"))
    }

    @Test fun changing_sync_state_updates_the_accessible_message_and_retry_action() {
        val status = mutableStateOf(SyncDisplayStatus.SYNCING)
        var retries = 0
        compose.setContent {
            MyApplicationTheme {
                SyncStatusIndicator(
                    syncStatus = status.value, pendingCount = 0, syncedCount = 0,
                    failedCount = if (status.value == SyncDisplayStatus.FAILED) 1 else 0,
                    onTriggerSync = { error("Unexpected generic sync") }, onRetryAll = { retries++ },
                )
            }
        }
        compose.onNodeWithTag("sync_status_message")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Enviando registros")
        compose.onNodeWithTag("trigger_workmanager_sync_button").assertIsNotEnabled()
        compose.runOnIdle { status.value = SyncDisplayStatus.FAILED }
        compose.onNodeWithTag("sync_status_message").assertTextContains("Não foi possível enviar alguns registros")
        compose.onNodeWithTag("trigger_workmanager_sync_button").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, retries); status.value = SyncDisplayStatus.FULLY_SYNCED }
        compose.onNodeWithTag("sync_status_message")
            .assertTextContains("A fila exibida não tem envios pendentes. Isso não confirma o recebimento pela equipe de saúde.")
        compose.onNodeWithTag("trigger_workmanager_sync_button").assertDoesNotExist()
    }

    private fun content(retry: (Long) -> Unit = {}, delete: (Long) -> Unit = {}, clear: () -> Unit = {}) {
        compose.setContent {
            MyApplicationTheme {
                QueueInspector(
                    pendingCount = 0, syncedCount = 0, failedCount = 1,
                    queueItems = listOf(failed), syncLogs = emptyList(), isSyncing = false,
                    onSyncNow = {}, onRetryFailedItem = retry, onRetryAllFailed = {},
                    onDeleteItem = delete, onClearSynced = {}, onClearAll = clear,
                    onInspectItem = {}, onRefreshWorkManager = {},
                )
            }
        }
    }
}
