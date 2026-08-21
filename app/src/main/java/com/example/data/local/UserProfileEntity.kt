package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = "CURRENT_USER",
    val patientId: String = "PAT-HBAND-001",
    val fullName: String = "Alex Rivera",
    val age: Int = 32,
    val gender: String = "Masculino",
    val heightCm: Float = 178f,
    val weightKg: Float = 74f,
    val dailyStepGoal: Int = 8000,
    val targetWaterMl: Int = 2500,
    val emergencyContact: String = "+55 11 98765-4321",
    val medicalNotes: String = "Monitoramento cardiovascular preventivo com HBand VE30."
)
