package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class QueueStatus {
    PENDING,
    SYNCED,
    FAILED
}

@Entity(tableName = "ingest_queue")
data class IngestQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = QueueStatus.PENDING.name,
    val retries: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null
)
