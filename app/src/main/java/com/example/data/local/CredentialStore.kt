package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Core ingest key override. The value is AES-GCM wrapped; the wrapping key
 * lives in the Android Keystore (a process-local AES key under Robolectric,
 * which has no Keystore). Gemini stays in BuildConfig and is not stored here.
 *
 * A missing override stays missing. An explicit empty override stays empty so
 * it does not silently fall back to the key compiled into the build.
 * Plaintext left by older builds is wrapped and removed only after commit.
 */
internal class CredentialStore(
    private val cipher: DatabaseKeyCipher,
    private val secure: SharedPreferences,
    private val legacy: SharedPreferences,
) {
    fun readCoreKeyOverride(): String? = synchronized(lock) {
        if (secure.contains(STATE)) {
            return when (val stored = decodeStored()) {
                Stored.Absent -> {
                    legacy.edit().remove(LEGACY_KEY).commit()
                    null
                }
                is Stored.Value -> {
                    legacy.edit().remove(LEGACY_KEY).commit()
                    stored.text
                }
                Stored.Undecryptable -> {
                    // Keep any leftover plaintext until the wrapped copy opens.
                    // Empty string blocks the build key without erasing the queue.
                    if (legacy.contains(LEGACY_KEY)) legacy.getString(LEGACY_KEY, "") ?: "" else ""
                }
            }
        }
        if (!legacy.contains(LEGACY_KEY)) return null
        val value = legacy.getString(LEGACY_KEY, null) ?: ""
        if (persist(value)) legacy.edit().remove(LEGACY_KEY).commit()
        value
    }

    fun writeCoreKeyOverride(value: String): Boolean = synchronized(lock) {
        if (!persist(value)) return false
        legacy.edit().remove(LEGACY_KEY).commit()
        true
    }

    fun clearOverride(): Boolean = synchronized(lock) {
        val ok = secure.edit().putString(STATE, ABSENT).remove(WRAPPED).remove(IV).commit()
        if (ok) legacy.edit().remove(LEGACY_KEY).commit()
        ok
    }

    private fun decodeStored(): Stored {
        val state = secure.getString(STATE, null)
        if (state == ABSENT) return Stored.Absent
        if (state != PRESENT) return Stored.Undecryptable
        val wrapped = secure.getString(WRAPPED, null)
        val iv = secure.getString(IV, null)
        if (wrapped.isNullOrBlank() || iv.isNullOrBlank()) return Stored.Undecryptable
        return try {
            Stored.Value(cipher.unwrap(wrapped, iv).decodeToString())
        } catch (_: Exception) {
            Stored.Undecryptable
        }
    }

    private sealed interface Stored {
        data object Absent : Stored
        data object Undecryptable : Stored
        data class Value(val text: String) : Stored
    }

    private fun persist(value: String): Boolean = try {
        val (ciphertext, nonce) = cipher.wrap(value.encodeToByteArray())
        if (ciphertext.isBlank() || nonce.isBlank()) false
        else secure.edit()
            .putString(STATE, PRESENT)
            .putString(WRAPPED, ciphertext)
            .putString(IV, nonce)
            .commit()
    } catch (_: Exception) {
        false
    }

    companion object {
        const val PREFS = "hband_credentials"
        const val LEGACY_PREFS = "hband_settings"
        const val LEGACY_KEY = "custom_api_key"
        private const val STATE = "core_key_state"
        private const val PRESENT = "present"
        private const val ABSENT = "absent"
        private const val WRAPPED = "core_key_wrapped"
        private const val IV = "core_key_iv"
        private val lock = Any()

        fun forContext(context: Context): CredentialStore {
            val app = context.applicationContext
            return CredentialStore(
                cipher = CredentialCiphers.forProcess(),
                secure = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE),
            )
        }
    }
}

internal object CredentialCiphers {
    private val robolectricKey: DatabaseKeyCipher by lazy {
        LocalAesGcmCipher(SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES"))
    }

    fun forProcess(): DatabaseKeyCipher =
        if (Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)) robolectricKey
        else KeystoreCredentialCipher
}

/** AES-256-GCM. Used in unit tests and as the Robolectric stand-in for Keystore. */
internal class LocalAesGcmCipher(private val key: SecretKey) : DatabaseKeyCipher {
    override fun wrap(raw: ByteArray): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return b64(cipher.doFinal(raw)) to b64(cipher.iv)
    }

    override fun unwrap(wrapped: String, iv: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64d(iv)))
        return cipher.doFinal(b64d(wrapped))
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun b64d(text: String): ByteArray = Base64.getDecoder().decode(text)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private object KeystoreCredentialCipher : DatabaseKeyCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "hband_credential_master"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    override fun wrap(raw: ByteArray): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(create = true))
        return b64(cipher.doFinal(raw)) to b64(cipher.iv)
    }

    override fun unwrap(wrapped: String, iv: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(create = false), GCMParameterSpec(128, b64d(iv)))
        return cipher.doFinal(b64d(wrapped))
    }

    private fun secretKey(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        if (!create || keyStore.containsAlias(ALIAS)) error("Chave de credencial indisponível.")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun b64d(text: String): ByteArray = Base64.getDecoder().decode(text)
}
