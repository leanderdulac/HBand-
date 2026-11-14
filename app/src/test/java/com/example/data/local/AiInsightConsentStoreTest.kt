package com.example.data.local

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AiInsightConsentStoreTest {
    private var now = 1_000L
    private fun store(): AiInsightConsentStore {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("ai_consent", 0)
            .also { it.edit().clear().commit() }
        return AiInsightConsentStore(prefs) { now }
    }

    @Test fun default_is_not_consented_and_has_no_timestamp() {
        val snap = store().snapshot()
        assertFalse(snap.consented)
        assertNull(snap.consentedAtMillis)
        assertNull(snap.withdrawnAtMillis)
        assertFalse(snap.promptShown)
    }

    @Test fun grant_records_timestamp_and_marks_prompt_shown() {
        now = 5_000L
        val snap = store().grant()
        assertTrue(snap.consented)
        assertEquals(5_000L, snap.consentedAtMillis)
        assertNull(snap.withdrawnAtMillis)
        assertTrue(snap.promptShown)
    }

    @Test fun withdraw_clears_consent_and_records_withdrawal_time() {
        val store = store()
        now = 5_000L
        store.grant()
        now = 9_000L
        val snap = store.withdraw()
        assertFalse(snap.consented)
        assertEquals(5_000L, snap.consentedAtMillis)
        assertEquals(9_000L, snap.withdrawnAtMillis)
        assertTrue(snap.promptShown)
    }

    @Test fun state_survives_a_new_store_instance() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("ai_consent_persist", 0)
            .also { it.edit().clear().commit() }
        AiInsightConsentStore(prefs) { 42L }.grant()
        val reloaded = AiInsightConsentStore(prefs) { 99L }.snapshot()
        assertTrue(reloaded.consented)
        assertEquals(42L, reloaded.consentedAtMillis)
    }

    @Test fun should_generate_only_when_flag_and_consent_are_on() {
        assertFalse(shouldGenerateAiInsight(featureEnabled = false, consented = false))
        assertFalse(shouldGenerateAiInsight(featureEnabled = true, consented = false))
        assertFalse(shouldGenerateAiInsight(featureEnabled = false, consented = true))
        assertTrue(shouldGenerateAiInsight(featureEnabled = true, consented = true))
    }

    @Test fun first_connection_prompt_only_once_after_pairing_when_not_consented() {
        assertTrue(shouldPromptAiInsightConsent(true, consented = false, promptShown = false, hasPairedWatch = true))
        assertFalse(shouldPromptAiInsightConsent(true, consented = false, promptShown = false, hasPairedWatch = false))
        assertFalse(shouldPromptAiInsightConsent(true, consented = true, promptShown = false, hasPairedWatch = true))
        assertFalse(shouldPromptAiInsightConsent(true, consented = false, promptShown = true, hasPairedWatch = true))
        assertFalse(shouldPromptAiInsightConsent(false, consented = false, promptShown = false, hasPairedWatch = true))
        assertFalse(shouldPromptAiInsightConsent(
            true, consented = false, promptShown = false, hasPairedWatch = true, anotherPromptVisible = true,
        ))
    }
}
