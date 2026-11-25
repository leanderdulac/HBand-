package com.example.data.local

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CredentialStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val key: SecretKey = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")

    @Before fun clear() {
        context.getSharedPreferences(CredentialStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(CredentialStore.LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store(cipher: DatabaseKeyCipher = LocalAesGcmCipher(key)) = CredentialStore(
        cipher,
        context.getSharedPreferences(CredentialStore.PREFS, Context.MODE_PRIVATE),
        context.getSharedPreferences(CredentialStore.LEGACY_PREFS, Context.MODE_PRIVATE),
    )

    @Test fun legacy_plaintext_core_key_is_wrapped_and_removed() {
        val legacy = context.getSharedPreferences(CredentialStore.LEGACY_PREFS, Context.MODE_PRIVATE)
        legacy.edit().putString(CredentialStore.LEGACY_KEY, "core-key-pilot").commit()
        val secure = store()
        assertEquals("core-key-pilot", secure.readCoreKeyOverride())
        assertFalse(legacy.contains(CredentialStore.LEGACY_KEY))
        val dumped = context.getSharedPreferences(CredentialStore.PREFS, Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(dumped.contains("core-key-pilot"))
        assertEquals("core-key-pilot", store().readCoreKeyOverride())
    }

    @Test fun empty_override_stays_empty_and_absent_stays_absent() {
        val secure = store()
        assertNull(secure.readCoreKeyOverride())
        assertTrue(secure.writeCoreKeyOverride(""))
        assertEquals("", store().readCoreKeyOverride())
        assertTrue(secure.clearOverride())
        assertNull(store().readCoreKeyOverride())
    }

    @Test fun failed_wrap_keeps_the_legacy_plaintext_key() {
        val legacy = context.getSharedPreferences(CredentialStore.LEGACY_PREFS, Context.MODE_PRIVATE)
        legacy.edit().putString(CredentialStore.LEGACY_KEY, "core-key-pilot").commit()
        val broken = object : DatabaseKeyCipher {
            override fun wrap(raw: ByteArray) = error("keystore unavailable")
            override fun unwrap(wrapped: String, iv: String) = error("unused")
        }
        assertEquals("core-key-pilot", store(broken).readCoreKeyOverride())
        assertEquals("core-key-pilot", legacy.getString(CredentialStore.LEGACY_KEY, null))
    }

    @Test fun undecryptable_wrapped_key_does_not_drop_a_leftover_plaintext_copy() {
        val legacy = context.getSharedPreferences(CredentialStore.LEGACY_PREFS, Context.MODE_PRIVATE)
        legacy.edit().putString(CredentialStore.LEGACY_KEY, "core-key-pilot").commit()
        val securePrefs = context.getSharedPreferences(CredentialStore.PREFS, Context.MODE_PRIVATE)
        securePrefs.edit()
            .putString("core_key_state", "present")
            .putString("core_key_wrapped", "not-valid")
            .putString("core_key_iv", "not-valid")
            .commit()
        assertEquals("core-key-pilot", store().readCoreKeyOverride())
        assertEquals("core-key-pilot", legacy.getString(CredentialStore.LEGACY_KEY, null))
    }

    @Test fun aes_gcm_roundtrip_hides_the_core_key() {
        val cipher = LocalAesGcmCipher(key)
        val (wrapped, iv) = cipher.wrap("core-key-pilot".encodeToByteArray())
        assertFalse(wrapped.contains("core-key-pilot"))
        assertEquals("core-key-pilot", cipher.unwrap(wrapped, iv).decodeToString())
    }
}
