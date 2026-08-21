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

    @Query("SELECT * FROM ingest_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingItems(): List<IngestQueueEntity>

    @Query("SELECT * FROM ingest_queue WHERE status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getFailedItems(): List<IngestQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
