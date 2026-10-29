package com.example.data.hband

import com.veepoo.protocol.model.datas.SportData

/** Current device counters observed at the SDK callback, not a new clinical measurement. */
data class VeepooSportReading(
    val observedAtMillis: Long,
    val steps: Int?,
    val calories: Float?,
    val distanceMeters: Float?,
) {
    init {
        require(observedAtMillis > 0)
        require(steps == null || steps >= 0)
        require(calories == null || (calories.isFinite() && calories >= 0f))
        require(distanceMeters == null || (distanceMeters.isFinite() && distanceMeters >= 0f))
        require(steps != null || calories != null || distanceMeters != null)
    }

    companion object {
        fun fromSdk(data: SportData?, observedAtMillis: Long): VeepooSportReading? {
            if (data == null || observedAtMillis <= 0) return null
            val steps = data.step.takeIf { it >= 0 }
            val calories = nonNegativeFloat(data.kcal)
            val distance = kilometersToMeters(data.dis)
            if (steps == null && calories == null && distance == null) return null
            return VeepooSportReading(observedAtMillis, steps, calories, distance)
        }

        /** SportData.dis and OriginData.disValue are kilometres in the official SDK. */
        fun kilometersToMeters(value: Double): Float? = nonNegativeFloat(value * 1000.0)

        private fun nonNegativeFloat(value: Double): Float? = value.toFloat()
            .takeIf { value.isFinite() && value >= 0.0 && it.isFinite() }
    }
}
