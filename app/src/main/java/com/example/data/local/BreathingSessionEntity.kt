package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "breathing_sessions")
data class BreathingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val durationSeconds: Int,
    val patternName: String = "4-7-8 Relaxing Breath",
    val timestampMillis: Long = System.currentTimeMillis(),
    val dateString: String
)
