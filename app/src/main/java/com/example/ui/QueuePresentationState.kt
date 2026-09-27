package com.example.ui

import com.example.data.ingest.QueueAuthorization
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.data.repository.ApiHealthState
import com.example.ui.components.SyncDisplayStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One query response supplies the displayed rows and all three counts together. */
data class QueuePresentationState(val items: List<IngestQueueEntity>) {
    val pendingCount = items.count { it.status == QueueStatus.PENDING.name }
    val syncedCount = items.count { it.status == QueueStatus.SYNCED.name }
    val failedCount = items.count { it.status == QueueStatus.FAILED.name }
}

/** null is loading, including after the last screen observer leaves. */
internal fun Flow<List<IngestQueueEntity>>.queuePresentationState(
    scope: CoroutineScope,
): StateFlow<QueuePresentationState?> =
    map<List<IngestQueueEntity>, QueuePresentationState?> { QueuePresentationState(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), null)

internal fun QueuePresentationState?.displayStatus(syncing: Boolean, health: ApiHealthState): SyncDisplayStatus = when {
    this == null -> SyncDisplayStatus.LOADING
    syncing -> SyncDisplayStatus.SYNCING
    items.any(QueueAuthorization::isBlocked) -> SyncDisplayStatus.AUTH_REQUIRED
    !health.isOnline -> SyncDisplayStatus.OFFLINE
    failedCount > 0 -> SyncDisplayStatus.FAILED
    pendingCount > 0 -> SyncDisplayStatus.PENDING_QUEUE
    else -> SyncDisplayStatus.FULLY_SYNCED
}
