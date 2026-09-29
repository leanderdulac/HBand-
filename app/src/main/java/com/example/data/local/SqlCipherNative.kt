package com.example.data.local

import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SQLCipher for Android (net.zetetic:sqlcipher-android) ships `libsqlcipher.so`
 * inside the AAR but does **not** load it automatically. Opening Room with
 * [net.zetetic.database.sqlcipher.SupportOpenHelperFactory] before
 * `System.loadLibrary("sqlcipher")` crashes with:
 *
 * `UnsatisfiedLinkError: No implementation found for long
 * net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen(...)`
 */
object SqlCipherNative {
    private const val TAG = "SqlCipherNative"
    private const val LIBRARY = "sqlcipher"

    private val loaded = AtomicBoolean(false)

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun loadOnce() {
        if (loaded.get()) return
        synchronized(this) {
            if (loaded.get()) return
            try {
                System.loadLibrary(LIBRARY)
                loaded.set(true)
                isLoaded = true
                Log.i(TAG, "Loaded native library $LIBRARY")
            } catch (error: UnsatisfiedLinkError) {
                if (isRobolectricRuntime()) {
                    Log.w(TAG, "SQLCipher native library not available in unit tests; skipping load")
                    loaded.set(true)
                    isLoaded = false
                    return
                }
                loaded.set(false)
                isLoaded = false
                throw UnsatisfiedLinkError(
                    "Falha ao carregar libsqlcipher.so ($LIBRARY). " +
                        "O APK precisa incluir jni/arm64-v8a/libsqlcipher.so. Causa: ${error.message}"
                ).apply { initCause(error) }
            }
        }
    }

    private fun isRobolectricRuntime(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        return fingerprint.contains("robolectric", ignoreCase = true)
    }
}
