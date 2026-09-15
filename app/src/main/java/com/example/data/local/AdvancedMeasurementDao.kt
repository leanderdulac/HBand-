package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvancedMeasurementDao {
    @Query("SELECT * FROM advanced_measurements ORDER BY timestampMillis DESC")
    fun getAll(): Flow<List<AdvancedMeasurementEntity>>

    @Query("SELECT * FROM advanced_measurements WHERE kind = :kind ORDER BY timestampMillis DESC LIMIT 1")
    fun getLatestByKind(kind: String): Flow<AdvancedMeasurementEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AdvancedMeasurementEntity): Long

    @Query("DELETE FROM advanced_measurements")
    suspend fun clearAll()
}
