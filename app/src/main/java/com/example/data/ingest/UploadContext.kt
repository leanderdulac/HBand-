package com.example.data.ingest

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import com.example.HBandHealthSyncApp
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Send-time context for one Core upload. Written as optional JSON keys on each
 * reading (`app_state`, `screen_locked`, `watch_battery_pct`), the same way
 * `device_label` is. The Core schema allows unknown fields (`extra=allow`) and
 * stores them beside the reading; they are not part of `client_reading_id` or
 * the flush idempotency key, and they are not written back into the durable queue.
 *
 * Applied when the request is built, so a reading queued in the foreground and
 * sent later describes the send, not the moment it was saved.
 *
 * `watch_battery_pct` is the last Veepoo SDK battery read already mapped by
 * [com.example.data.hband.VeepooBatteryMapper] (percent, or a coarse 1–4 bar
 * converted to a percent). Missing, simulated, or out of 0..100 is JSON null.
 * No new BLE read is started for the upload.
 *
 * `app_state` uses started activities, not process importance: the BLE
 * foreground service stays running while the screen is off, and that must
 * still be `background`.
 */
data class UploadContext(
    val appState: String,
    val screenLocked: Boolean,
    val watchBatteryPct: Int?,
) {
    init {
        require(appState == FOREGROUND || appState == BACKGROUND) { "app_state" }
        require(watchBatteryPct == null || watchBatteryPct in 0..100) { "watch_battery_pct" }
    }

    fun applyTo(json: JSONObject) {
        json.put(KEY_APP_STATE, appState)
        json.put(KEY_SCREEN_LOCKED, screenLocked)
        if (watchBatteryPct == null) json.put(KEY_WATCH_BATTERY, JSONObject.NULL)
        else json.put(KEY_WATCH_BATTERY, watchBatteryPct)
    }

    companion object {
        const val FOREGROUND = "foreground"
        const val BACKGROUND = "background"
        const val KEY_APP_STATE = "app_state"
        const val KEY_SCREEN_LOCKED = "screen_locked"
        const val KEY_WATCH_BATTERY = "watch_battery_pct"

        fun of(
            inForeground: Boolean,
            screenLocked: Boolean,
            watchBatteryPct: Int?,
            batterySimulated: Boolean = false,
        ): UploadContext = UploadContext(
            appState = if (inForeground) FOREGROUND else BACKGROUND,
            screenLocked = screenLocked,
            watchBatteryPct = watchBatteryPct?.takeIf { !batterySimulated && it in 0..100 },
        )
    }
}

/** Started-activity count. Registered from [HBandHealthSyncApp.onCreate]. */
internal object AppVisibility {
    private val started = AtomicInteger(0)
    private val installed = AtomicBoolean(false)

    fun isForeground(): Boolean = started.get() > 0

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { noteStarted() }
            override fun onActivityStopped(activity: Activity) { noteStopped() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    internal fun noteStarted() { started.incrementAndGet() }

    internal fun noteStopped() { started.updateAndGet { (it - 1).coerceAtLeast(0) } }

    internal fun resetForTests() { started.set(0) }
}

internal object UploadContextReader {
    fun capture(context: Context): UploadContext = UploadContext.of(
        inForeground = AppVisibility.isForeground(),
        screenLocked = isScreenLocked(context),
        watchBatteryPct = lastWatchBatteryPercent(context),
    )

    /** Lock screen showing. No location and no extra permission. */
    fun isScreenLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isKeyguardLocked == true
    }

    /**
     * Last Veepoo SDK battery percent kept on the connected watch.
     * Null when the manager is not up, the watch is disconnected, the value
     * was simulated, or the SDK has not reported a percent in 0..100.
     */
    fun lastWatchBatteryPercent(context: Context): Int? {
        val app = context.applicationContext as? HBandHealthSyncApp ?: return null
        val device = app.readyBleManagerOrNull()?.connectedDevice?.value ?: return null
        if (!device.isConnected || device.batteryIsSimulated) return null
        return device.batteryLevel?.takeIf { it in 0..100 }
    }
}
