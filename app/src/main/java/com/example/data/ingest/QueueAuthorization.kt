package com.example.data.ingest

import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus

/** Recognizes the app's persisted HTTP auth errors, including records from older builds. */
object QueueAuthorization {
    const val UNAUTHORIZED_PREFIX = "Falha de autenticação na API HealthTech (HTTP 401)"
    const val FORBIDDEN_PREFIX = "Falha de autenticação na API HealthTech (HTTP 403)"
    const val PAUSED_MESSAGE = "O servidor não autorizou o envio. As tentativas automáticas estão pausadas e os registros continuam salvos neste aparelho. Peça ajuda à equipe responsável. Após corrigir o acesso, tente novamente."

    fun isBlocked(item: IngestQueueEntity): Boolean =
        (item.status == QueueStatus.FAILED.name || item.status == QueueStatus.PENDING.name) &&
            listOf(UNAUTHORIZED_PREFIX, FORBIDDEN_PREFIX).any { prefix ->
                item.errorMessage?.startsWith(prefix) == true
            }
}
