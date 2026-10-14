package com.example.data.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Which manufacturer-specific help to show for keeping the app awake. */
enum class BatteryGuidance { SAMSUNG, GENERIC }

/**
 * Pure decisions about the battery-optimization exemption, kept free of
 * Android APIs so they can be unit-tested.
 */
object BatteryOptimizationPolicy {
    /** Prompt once, only after a watch is paired and only while the app is still optimized. */
    fun shouldPromptOnce(isIgnoringOptimizations: Boolean, alreadyPrompted: Boolean, hasPairedWatch: Boolean): Boolean =
        hasPairedWatch && !isIgnoringOptimizations && !alreadyPrompted

    fun guidanceFor(manufacturer: String?): BatteryGuidance =
        if (manufacturer?.trim()?.equals("samsung", ignoreCase = true) == true) BatteryGuidance.SAMSUNG
        else BatteryGuidance.GENERIC

    fun statusText(isIgnoringOptimizations: Boolean): String =
        if (isIgnoringOptimizations) "Liberado: o celular não vai pausar o envio das leituras."
        else "Atenção: o celular pode pausar o app e atrasar o envio das leituras."
}

/** Thin Android wrapper around [PowerManager] and the system exemption request. */
object BatteryOptimization {
    private const val PREFS = "battery_optimization"
    private const val KEY_PROMPTED = "prompted_once"

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun wasPrompted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PROMPTED, false)

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    @SuppressLint("BatteryLife") // Continuous BLE health telemetry is an accepted use case.
    fun requestExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Opens the system request; falls back to the general battery-optimization list. */
    fun openExemptionRequest(context: Context) {
        val intents = listOf(
            requestExemptionIntent(context),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { /* try next */ }
        }
    }

    fun guidance(): BatteryGuidance = BatteryOptimizationPolicy.guidanceFor(Build.MANUFACTURER)
}
