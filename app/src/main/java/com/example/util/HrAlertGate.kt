package com.example.util

import android.content.SharedPreferences

/**
 * Aviso de batimentos por cruzamento do limite cadastrado pelo paciente.
 *
 * Um aviso quando a FC sai da faixa; silêncio enquanto continua fora. Rearma quando volta
 * [HYSTERESIS_BPM] para dentro (limite − 5 no alto, limite + 5 no baixo). Se continuar fora,
 * no máximo um lembrete após [REMINDER_AFTER_MS]. O estado fica em SharedPreferences para que
 * reiniciar o app/serviço não dispare de novo. Usa relógio de parede (sobrevive a reboot).
 */
class HrAlertGate(private val prefs: SharedPreferences) {
    enum class Kind(val key: String) { HIGH("high"), LOW("low") }

    /** Retorna o tipo de aviso a disparar agora, ou null. */
    fun evaluate(hr: Int, upper: Int, lower: Int, nowMs: Long = System.currentTimeMillis()): Kind? {
        if (hr <= 0) return null
        val high = step(Kind.HIGH, hr > upper, hr <= upper - HYSTERESIS_BPM, nowMs)
        val low = step(Kind.LOW, hr < lower, hr >= lower + HYSTERESIS_BPM, nowMs)
        return high ?: low
    }

    private fun step(kind: Kind, outOfRange: Boolean, backInside: Boolean, nowMs: Long): Kind? {
        val active = "hr_alert_${kind.key}_active"
        val firedAt = "hr_alert_${kind.key}_fired_at"
        val reminded = "hr_alert_${kind.key}_reminded"
        if (outOfRange) {
            if (!prefs.getBoolean(active, false)) {
                prefs.edit().putBoolean(active, true).putLong(firedAt, nowMs).putBoolean(reminded, false).commit()
                return kind
            }
            if (!prefs.getBoolean(reminded, false) && nowMs - prefs.getLong(firedAt, nowMs) >= REMINDER_AFTER_MS) {
                prefs.edit().putBoolean(reminded, true).commit()
                return kind
            }
        } else if (backInside && prefs.getBoolean(active, false)) {
            prefs.edit().putBoolean(active, false).putBoolean(reminded, false).commit()
        }
        return null
    }

    companion object {
        const val HYSTERESIS_BPM = 5
        const val REMINDER_AFTER_MS = 30L * 60 * 1000
    }
}
