package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
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
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "hband_sqlcipher_master"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val storage = DatabaseKeyStorage(object : DatabaseKeyCipher {
        override fun wrap(raw: ByteArray): Pair<String, String> {
            val (ciphertext, iv) = SqlCipherPassphrase.wrap(raw)
            return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
        }
        override fun unwrap(wrapped: String, iv: String): ByteArray = SqlCipherPassphrase.unwrap(wrapped, iv)
    })

    fun getPassphrase(context: Context): ByteArray = try {
        val app = context.applicationContext
        storage.getOrCreate(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            app.getDatabasePath(AppDatabase.DATABASE_NAME))
    } catch (_: Exception) { throw DatabaseKeyUnavailableException() }

    private fun wrap(raw: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return cipher.doFinal(raw) to cipher.iv
    }

    private fun unwrap(wrappedB64: String, ivB64: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        // A missing master key is a recovery condition, never a reason to replace it.
        cipher.init(Cipher.DECRYPT_MODE, requireExistingSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(Base64.decode(wrappedB64, Base64.NO_WRAP))
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        if (keyStore.containsAlias(KEY_ALIAS)) throw DatabaseKeyUnavailableException()

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

    private fun requireExistingSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw DatabaseKeyUnavailableException()
    }
}
