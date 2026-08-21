package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BreathingDao {
    @Query("SELECT * FROM breathing_sessions ORDER BY timestampMillis DESC")
    fun getAllSessionsFlow(): Flow<List<BreathingSessionEntity>>

    @Query("SELECT SUM(durationSeconds) FROM breathing_sessions")
    fun getTotalBreathingSecondsFlow(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BreathingSessionEntity)

    @Query("DELETE FROM breathing_sessions")
    suspend fun clearAll()
}
