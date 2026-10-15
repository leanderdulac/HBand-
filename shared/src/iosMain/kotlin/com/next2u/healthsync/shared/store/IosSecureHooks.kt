package com.next2u.healthsync.shared.store

/**
 * Swift installs CryptoKit + Keychain load/save before [PatientStore.open].
 * Opening without hooks fails and never writes.
 */
abstract class PlaintextLoader {
    abstract fun read(): PlaintextRead
}

abstract class PlaintextSaver {
    abstract fun write(plaintext: String): Boolean
}

object IosSecureHooks {
    var loader: PlaintextLoader? = null
        private set
    var saver: PlaintextSaver? = null
        private set

    fun isInstalled(): Boolean = loader != null && saver != null

    fun install(loader: PlaintextLoader, saver: PlaintextSaver) {
        this.loader = loader
        this.saver = saver
    }

    fun clearForTests() {
        loader = null
        saver = null
    }
}

class IosHookSnapshotCodec : SnapshotCodec {
    override fun read(): PlaintextRead {
        val loader = IosSecureHooks.loader
            ?: return PlaintextRead(failed = true, detail = PatientStore.HOOKS_MISSING)
        return try {
            loader.read()
        } catch (error: Exception) {
            PlaintextRead(failed = true, detail = error.message ?: PatientStore.CORRUPT)
        }
    }

    override fun write(plaintext: String): Boolean {
        val saver = IosSecureHooks.saver ?: return false
        return try {
            saver.write(plaintext)
        } catch (_: Exception) {
            false
        }
    }
}

internal actual fun defaultSnapshotCodec(): SnapshotCodec = IosHookSnapshotCodec()
