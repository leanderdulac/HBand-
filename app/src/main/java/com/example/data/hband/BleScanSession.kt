package com.example.data.hband

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper

/** Owns one scan and its deadline; expired callbacks must never affect a later scan. */
internal class BleScanSession(
    private val handler: Handler,
    private val canScan: () -> Boolean,
    private val startScan: (ScanCallback) -> Unit,
    private val stopScan: (ScanCallback) -> Unit,
    private val onResults: (List<ScanResult>) -> Unit,
    private val onScanningChanged: (Boolean) -> Unit,
    private val onFailure: (BleScanFailure) -> Unit,
) {
    private var active: ScanCallback? = null
    private var deadline: Runnable? = null

    fun start() = onHandler {
        if (active != null) return@onHandler
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result != null) deliver(this, listOf(result))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                if (results != null) deliver(this, results.toList())
            }

            override fun onScanFailed(errorCode: Int) = onHandler {
                if (active !== this) return@onHandler
                finish()
                onFailure(BleScanFailure.SCAN_FAILED)
            }
        }
        active = callback
        onScanningChanged(true)
        try {
            if (!canScan()) throw SecurityException("BLE permission unavailable")
            startScan(callback)
            // Some implementations can report failure synchronously while starting.
            if (active === callback) {
                val timeout = Runnable { if (active === callback) finish() }
                deadline = timeout
                handler.postDelayed(timeout, 12_000L)
            }
        } catch (error: Exception) {
            if (active === callback) {
                finish()
                onFailure(when (error) {
                    is SecurityException -> BleScanFailure.PERMISSION_REQUIRED
                    is BleScanUnavailableException -> error.reason
                    else -> BleScanFailure.SCAN_FAILED
                })
            }
        }
    }

    fun stop() = onHandler { finish() }

    private fun deliver(callback: ScanCallback, results: List<ScanResult>) = onHandler {
        if (active !== callback) return@onHandler
        try {
            if (!canScan()) throw SecurityException("BLE permission revoked")
            onResults(results)
        } catch (_: SecurityException) {
            finish()
            onFailure(BleScanFailure.PERMISSION_REQUIRED)
        }
    }

    private fun finish() {
        val callback = active ?: return
        // Invalidate first: stopScan can itself cause a late callback.
        active = null
        deadline?.let(handler::removeCallbacks)
        deadline = null
        onScanningChanged(false)
        try {
            stopScan(callback)
        } catch (_: Exception) {
            onFailure(BleScanFailure.STOP_FAILED)
        }
    }

    private fun onHandler(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) action() else handler.post { action() }
    }
}
