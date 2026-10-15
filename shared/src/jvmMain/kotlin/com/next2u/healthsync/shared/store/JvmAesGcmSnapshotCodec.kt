package com.next2u.healthsync.shared.store

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM snapshot for JVM tests and the Linux CI host.
 * Key lives next to the blob; iOS uses CryptoKit + Keychain instead.
 */
class JvmAesGcmSnapshotCodec(
    private val blobFile: File = File(System.getProperty("java.io.tmpdir"), "next2u-healthsync-snapshot.bin"),
    private val keyFile: File = File(System.getProperty("java.io.tmpdir"), "next2u-healthsync-snapshot.key"),
) : SnapshotCodec {
    override fun read(): PlaintextRead {
        if (!blobFile.exists()) return PlaintextRead(missing = true)
        if (!keyFile.exists()) {
            return PlaintextRead(failed = true, detail = "Chave AES ausente; o arquivo cifrado não foi tocado.")
        }
        return try {
            val plain = decrypt(blobFile.readBytes(), keyFile.readBytes())
            PlaintextRead(plaintext = plain.decodeToString())
        } catch (error: Exception) {
            PlaintextRead(failed = true, detail = error.message ?: PatientStore.CORRUPT)
        }
    }

    override fun write(plaintext: String): Boolean = try {
        val key = loadOrCreateKey()
        blobFile.writeBytes(encrypt(plaintext.encodeToByteArray(), key))
        true
    } catch (_: Exception) {
        false
    }

    private fun loadOrCreateKey(): ByteArray {
        if (keyFile.exists()) return keyFile.readBytes()
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        keyFile.writeBytes(key)
        return key
    }

    companion object {
        private val MAGIC = byteArrayOf('N'.code.toByte(), '2'.code.toByte(), 'U'.code.toByte(), '1'.code.toByte())
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        fun isolated(dir: File): JvmAesGcmSnapshotCodec {
            dir.mkdirs()
            return JvmAesGcmSnapshotCodec(
                blobFile = File(dir, "snapshot.bin"),
                keyFile = File(dir, "snapshot.key"),
            )
        }

        internal fun encrypt(plain: ByteArray, key: ByteArray): ByteArray {
            val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            val body = cipher.doFinal(plain)
            return MAGIC + iv + body
        }

        internal fun decrypt(blob: ByteArray, key: ByteArray): ByteArray {
            require(blob.size > MAGIC.size + IV_BYTES) { "blob curto" }
            require(blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "magic inválida" }
            val iv = blob.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
            val body = blob.copyOfRange(MAGIC.size + IV_BYTES, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(body)
        }
    }
}

internal actual fun defaultSnapshotCodec(): SnapshotCodec = JvmAesGcmSnapshotCodec()
