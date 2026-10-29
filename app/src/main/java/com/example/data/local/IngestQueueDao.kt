package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IngestQueueDao {

    @Query("SELECT * FROM ingest_queue ORDER BY id DESC")
    fun getAllItems(): Flow<List<IngestQueueEntity>>

    @Query("SELECT * FROM ingest_queue ORDER BY id DESC")
    suspend fun getAllItemsSync(): List<IngestQueueEntity>

    // Check persisted auth evidence without materializing every clinical payload.
    // Binary prefix equality preserves Kotlin startsWith (including letter case).
    @Query("""
        SELECT EXISTS(SELECT 1 FROM ingest_queue
        WHERE status IN ('PENDING', 'FAILED') AND (
            substr(errorMessage, 1, length(:unauthorizedPrefix)) = :unauthorizedPrefix COLLATE BINARY
            OR substr(errorMessage, 1, length(:forbiddenPrefix)) = :forbiddenPrefix COLLATE BINARY
        ))
    """)
    suspend fun hasAuthorizationBlock(unauthorizedPrefix: String, forbiddenPrefix: String): Boolean

    @Query("SELECT * FROM ingest_queue WHERE status = 'PENDING' ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingItems(): List<IngestQueueEntity>

    @Query("SELECT * FROM ingest_queue WHERE status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getFailedItems(): List<IngestQueueEntity>

    // A repeated identity must not replace a previously captured row or its receipt.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: IngestQueueEntity): Long

    @Update
    suspend fun updateItem(item: IngestQueueEntity)

    @Query("DELETE FROM ingest_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ingest_queue WHERE status = 'SYNCED'")
    suspend fun clearSyncedItems()

    @Query("DELETE FROM ingest_queue")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM ingest_queue WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>
}
