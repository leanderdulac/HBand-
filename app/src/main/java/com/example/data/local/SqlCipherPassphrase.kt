package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the SQLCipher database key. The raw 32-byte passphrase never ships in
 * source; it is generated on first run and wrapped with an Android Keystore AES key.
 */
object SqlCipherPassphrase {
    private const val PREFS = "hband_sqlcipher"
    private const val WRAPPED_KEY = "wrapped_db_key"
    private const val WRAPPED_IV = "wrapped_db_key_iv"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "hband_sqlcipher_master"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun getPassphrase(context: Context): ByteArray {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(WRAPPED_KEY, null)
        val iv = prefs.getString(WRAPPED_IV, null)
        if (!wrapped.isNullOrBlank() && !iv.isNullOrBlank()) {
            return unwrap(wrapped, iv)
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val (cipherText, ivBytes) = wrap(raw)
        prefs.edit()
            .putString(WRAPPED_KEY, Base64.encodeToString(cipherText, Base64.NO_WRAP))
            .putString(WRAPPED_IV, Base64.encodeToString(ivBytes, Base64.NO_WRAP))
            .apply()
        return raw
    }

    private fun wrap(raw: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return cipher.doFinal(raw) to cipher.iv
    }

    private fun unwrap(wrappedB64: String, ivB64: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(Base64.decode(wrappedB64, Base64.NO_WRAP))
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
