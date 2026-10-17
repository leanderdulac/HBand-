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

    /** What goes on the wire: the saved name, else the phone model (Build.MODEL). */
    fun resolve(saved: String?, phoneModel: String?): String? = normalize(saved) ?: normalize(phoneModel)

    /** What the form shows first: the saved name, else the patient's own name. */
    fun prefill(saved: String?, patientName: String?): String = normalize(saved) ?: patientName?.trim().orEmpty()

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    /** Blank clears the saved name, so sends fall back to the phone model. */
    fun save(context: Context, raw: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val value = normalize(raw)
        if (value == null) editor.remove(KEY) else editor.putString(KEY, value)
        editor.apply()
    }

    fun current(context: Context): String? = resolve(read(context), Build.MODEL)
}
