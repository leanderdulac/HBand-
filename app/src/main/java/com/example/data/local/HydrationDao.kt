package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HydrationDao {
    @Query("SELECT * FROM hydration_logs WHERE dateString = :dateString ORDER BY timestampMillis DESC")
    fun getHydrationLogsForDate(dateString: String): Flow<List<HydrationLogEntity>>

    @Query("SELECT SUM(amountMl) FROM hydration_logs WHERE dateString = :dateString")
    fun getTodayTotalMlFlow(dateString: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HydrationLogEntity)

    @Query("DELETE FROM hydration_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM hydration_logs WHERE dateString = :dateString")
    suspend fun resetTodayLogs(dateString: String)

    @Query("DELETE FROM hydration_logs")
    suspend fun clearAll()
}
