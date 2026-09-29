package com.example.data.local

import android.content.SharedPreferences
import java.io.File
import java.security.SecureRandom

internal interface DatabaseKeyCipher {
    fun wrap(raw: ByteArray): Pair<String, String>
    fun unwrap(wrapped: String, iv: String): ByteArray
}

/** No key material, file path or platform exception is included in the message. */
internal class DatabaseKeyUnavailableException : IllegalStateException(
    "Não foi possível acessar a chave do banco com segurança. Os arquivos não foram apagados. " +
        "Não limpe os dados nem reinstale o aplicativo; solicite recuperação à equipe responsável."
)

/** Preserves the existing wrapped-key format. Does not recover, replace or migrate keys. */
internal class DatabaseKeyStorage(
    private val cipher: DatabaseKeyCipher,
    private val generate: () -> ByteArray = { ByteArray(32).also { SecureRandom().nextBytes(it) } },
) {
    // SharedPreferences can update its memory even when commit fails. No retry in
    // this process may mistake that volatile value for a durably persisted key.
    private var persistenceFailed = false

    @Synchronized
    fun getOrCreate(prefs: SharedPreferences, database: File): ByteArray =
        try { loadOrCreate(prefs, database) } catch (_: Exception) { throw DatabaseKeyUnavailableException() }

    private fun loadOrCreate(prefs: SharedPreferences, database: File): ByteArray {
        if (persistenceFailed) throw DatabaseKeyUnavailableException()
        val wrapped = read(prefs, WRAPPED_KEY)
        val iv = read(prefs, WRAPPED_IV)
        if (prefs.contains(WRAPPED_KEY) || prefs.contains(WRAPPED_IV)) {
            if (wrapped.isNullOrBlank() || iv.isNullOrBlank()) throw DatabaseKeyUnavailableException()
            val raw = try { cipher.unwrap(wrapped, iv) } catch (_: Exception) { throw DatabaseKeyUnavailableException() }
            if (raw.size != 32) { raw.fill(0); throw DatabaseKeyUnavailableException() }
            return raw
        }
        // Sidecars, even without the main file, are evidence requiring recovery.
        if (listOf("", "-wal", "-shm", "-journal").any { File(database.path + it).exists() }) {
            throw DatabaseKeyUnavailableException()
        }
        val raw = generate()
        var returned = false
        try {
            if (raw.size != 32) throw DatabaseKeyUnavailableException()
            val (ciphertext, nonce) = cipher.wrap(raw)
            if (ciphertext.isBlank() || nonce.isBlank()) throw DatabaseKeyUnavailableException()
            persistenceFailed = true
            if (!prefs.edit().putString(WRAPPED_KEY, ciphertext).putString(WRAPPED_IV, nonce).commit()) {
                throw DatabaseKeyUnavailableException()
            }
            persistenceFailed = false
            returned = true
            return raw
        } catch (_: Exception) {
            throw DatabaseKeyUnavailableException()
        } finally {
            if (!returned) raw.fill(0)
        }
    }

    private fun read(prefs: SharedPreferences, key: String): String? =
        try { prefs.getString(key, null) } catch (_: Exception) { throw DatabaseKeyUnavailableException() }

    internal companion object {
        const val WRAPPED_KEY = "wrapped_db_key"
        const val WRAPPED_IV = "wrapped_db_key_iv"
    }
}
