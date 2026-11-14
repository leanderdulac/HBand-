package com.example.data.local

import android.content.SharedPreferences

/**
 * Local-only patient consent for the "Resumo com IA" Gemini summary.
 * Default is not consented. Nothing is sent to Core; this never changes the ingest contract.
 */
class AiInsightConsentStore(
    private val prefs: SharedPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    data class Snapshot(
        val consented: Boolean = false,
        val consentedAtMillis: Long? = null,
        val withdrawnAtMillis: Long? = null,
        val promptShown: Boolean = false,
    )

    fun snapshot(): Snapshot = Snapshot(
        consented = prefs.getBoolean(KEY_CONSENTED, false),
        consentedAtMillis = prefs.getLong(KEY_CONSENTED_AT, 0L).takeIf { it > 0L },
        withdrawnAtMillis = prefs.getLong(KEY_WITHDRAWN_AT, 0L).takeIf { it > 0L },
        promptShown = prefs.getBoolean(KEY_PROMPT_SHOWN, false),
    )

    fun isConsented(): Boolean = snapshot().consented

    fun grant(): Snapshot {
        prefs.edit()
            .putBoolean(KEY_CONSENTED, true)
            .putLong(KEY_CONSENTED_AT, nowMillis())
            .remove(KEY_WITHDRAWN_AT)
            .putBoolean(KEY_PROMPT_SHOWN, true)
            .apply()
        return snapshot()
    }

    fun withdraw(): Snapshot {
        prefs.edit()
            .putBoolean(KEY_CONSENTED, false)
            .putLong(KEY_WITHDRAWN_AT, nowMillis())
            .apply()
        return snapshot()
    }

    fun markPromptShown(): Snapshot {
        prefs.edit().putBoolean(KEY_PROMPT_SHOWN, true).apply()
        return snapshot()
    }

    companion object {
        const val KEY_CONSENTED = "ai_insight_consented"
        const val KEY_CONSENTED_AT = "ai_insight_consented_at_millis"
        const val KEY_WITHDRAWN_AT = "ai_insight_withdrawn_at_millis"
        const val KEY_PROMPT_SHOWN = "ai_insight_consent_prompt_shown"
        const val PRIVACY_POLICY_URL = "https://www.higianext2u.com.br/privacidade"
        const val PRIVACY_DELETION_URL = "https://www.higianext2u.com.br/privacidade#exclusao"
    }
}

/** When the one-time first-connection consent dialog should appear. */
fun shouldPromptAiInsightConsent(
    featureEnabled: Boolean,
    consented: Boolean,
    promptShown: Boolean,
    hasPairedWatch: Boolean,
    anotherPromptVisible: Boolean = false,
): Boolean = featureEnabled && !consented && !promptShown && hasPairedWatch && !anotherPromptVisible

/** Gemini (and the automatic 15-minute review) only run when the build flag and consent are both on. */
fun shouldGenerateAiInsight(featureEnabled: Boolean, consented: Boolean): Boolean =
    featureEnabled && consented
