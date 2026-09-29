package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.example.data.ingest.IngestReadingIdentity

enum class QueueStatus {
    PENDING,
    SYNCED,
    FAILED
}

@Entity(
    tableName = "ingest_queue",
    indices = [Index(value = ["clientReadingId"], unique = true)]
)
data class IngestQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = QueueStatus.PENDING.name,
    val retries: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
    /**
     * Stable UUID generated once at insert. Never regenerated on retry —
     * this is the HealthTech `client_reading_id`.
     */
    @ColumnInfo(defaultValue = "''")
    val clientReadingId: String = IngestReadingIdentity.forPayload(payloadJson),
)
