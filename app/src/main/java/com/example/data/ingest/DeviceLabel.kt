package com.example.data.ingest

import android.content.Context
import android.os.Build

/**
 * "Nome da pulseira": a readable name the patient chooses in Meu perfil, sent to the Core
 * as the optional `device_label` (Core: max 64 chars, trimmed, empty becomes null).
 * Several pilot watches share the same advertised name, so this tells the team whose
 * watch sent each reading. Stored only on this phone; never derived from secrets.
 */
object DeviceLabel {
    const val MAX_LENGTH = 64
    const val PREFS = "hband_settings"
    const val KEY = "device_label"

    /** Trim, cap at [MAX_LENGTH] without splitting a surrogate pair, and map blank to null. */
    fun normalize(raw: String?): String? {
        var value = raw?.trim().orEmpty()
        if (value.length > MAX_LENGTH) {
            var end = MAX_LENGTH
            if (Character.isHighSurrogate(value[end - 1])) end -= 1
            value = value.substring(0, end).trim()
        }
        return value.ifEmpty { null }
    }

    /** Suggested name from the profile: "Pulseira de <nome>" (same as iOS build 11). */
    fun suggestion(profileName: String?): String? {
        val name = profileName?.trim().orEmpty()
        return if (name.isEmpty()) null else normalize("Pulseira de $name")
    }

    /** On the wire: saved name > "Pulseira de <nome do perfil>" > phone model (Build.MODEL). */
    fun resolve(saved: String?, profileName: String?, phoneModel: String?): String? =
        normalize(saved) ?: suggestion(profileName) ?: normalize(phoneModel)

    /** What the form shows first: the saved name, else the suggestion from the patient's name. */
    fun prefill(saved: String?, patientName: String?): String = normalize(saved) ?: suggestion(patientName).orEmpty()

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    /**
     * Blank, or equal to the suggestion, clears the saved name so the label keeps
     * following the profile name (like iOS). Anything else is kept as typed.
     */
    fun save(context: Context, raw: String?, profileName: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val value = normalize(raw)
        if (value == null || value == suggestion(profileName)) editor.remove(KEY) else editor.putString(KEY, value)
        editor.apply()
    }

    fun current(context: Context, profileName: String?): String? = resolve(read(context), profileName, Build.MODEL)

    /** Send-time label: reads the saved profile name from the local database. */
    suspend fun current(context: Context, profiles: com.example.data.local.UserProfileDao): String? =
        current(context, runCatching { profiles.getUserProfile()?.fullName }.getOrNull())
}
