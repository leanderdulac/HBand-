package com.example.data.ingest

import com.example.data.local.IngestQueueEntity
import com.example.data.remote.IngestConfigurationStatus

/** Display-only projection. Never retain payloads, raw errors, full URLs or credentials. */
data class IngestDiagnostics(
    val loaded: Boolean = false,
    val configuration: IngestConfigurationStatus = IngestConfigurationStatus(),
    val pending: Int = 0, val failed: Int = 0, val synced: Int = 0, val unknown: Int = 0,
    val authorizationPaused: Boolean = false,
    val readFailed: Boolean = false,
) {
    companion object {
        fun from(items: List<IngestQueueEntity>, configuration: IngestConfigurationStatus) = IngestDiagnostics(
            loaded = true, configuration = configuration,
            pending = items.count { it.status == "PENDING" }, failed = items.count { it.status == "FAILED" },
            synced = items.count { it.status == "SYNCED" }, unknown = items.count { it.status !in setOf("PENDING", "FAILED", "SYNCED") },
            authorizationPaused = items.any(QueueAuthorization::isBlocked),
        )
    }
}
