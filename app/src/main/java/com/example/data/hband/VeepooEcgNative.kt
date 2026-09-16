package com.example.data.hband

import android.os.Build
import android.util.Log

/**
 * ECG in vpprotocol loads [com.vp.cso.hrvreport.JNIChange], whose `<clinit>`
 * calls `System.loadLibrary("native-lib")`. That `.so` lives in the official
 * SDK `jniLibs` drop, not inside `vpprotocol-*.aar`.
 */
object VeepooEcgNative {
    private const val TAG = "VeepooEcgNative"
    const val LIBRARY = "native-lib"

    const val MISSING_LIB_HINT =
        "ECG nativo indisponível (libnative-lib.so). Atualize o app e tente de novo — a sessão BLE não foi encerrada."

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun loadOnce(): Boolean {
        if (isLoaded) return true
        synchronized(this) {
            if (isLoaded) return true
            return try {
                System.loadLibrary(LIBRARY)
                isLoaded = true
                runCatching { Log.i(TAG, "Loaded $LIBRARY") }
                true
            } catch (error: UnsatisfiedLinkError) {
                isLoaded = false
                runCatching {
                    if (isRobolectricRuntime()) {
                        Log.w(TAG, "native-lib not available in unit tests")
                    } else {
                        Log.e(TAG, "Failed to preload $LIBRARY: ${error.message}")
                    }
                }
                false
            }
        }
    }

    fun ensureLoaded(): Boolean = isLoaded || loadOnce()

    fun uiError(error: Throwable? = null): DetectSessionUiState {
        val native = error is UnsatisfiedLinkError ||
            error?.message?.contains("native-lib", ignoreCase = true) == true
        return DetectSessionUiState(
            supported = true,
            running = false,
            lastError = if (native || error == null) MISSING_LIB_HINT else {
                error.message?.takeIf { it.isNotBlank() } ?: MISSING_LIB_HINT
            },
        )
    }

    private fun isRobolectricRuntime(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        return fingerprint.contains("robolectric", ignoreCase = true)
    }
}
