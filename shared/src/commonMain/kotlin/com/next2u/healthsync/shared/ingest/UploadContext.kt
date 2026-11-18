package com.next2u.healthsync.shared.ingest

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Send-time context for one Core upload. Optional JSON keys on each reading,
 * same as `device_label`: the Core allows unknown fields (`extra=allow`) and
 * stores them beside the reading. They are not part of `client_reading_id`
 * or the flush idempotency key, and they are not written back into the queue.
 *
 * Stamped when the request is built, so a reading saved in the foreground and
 * sent later describes the send.
 *
 * `watch_battery_pct` is the last Veepoo SDK percent (`veepooSDKReadDeviceBatteryInfo`
 * when `isPercent` is true). Missing or outside 0..100 is JSON null. No location.
 */
data class UploadContext(
    val appState: String,
    val screenLocked: Boolean,
    val watchBatteryPct: Int?,
) {
    init {
        require(appState == FOREGROUND || appState == BACKGROUND)
        require(watchBatteryPct == null || watchBatteryPct in 0..100)
    }

    fun jsonFields(): Map<String, JsonElement> = mapOf(
        KEY_APP_STATE to JsonPrimitive(appState),
        KEY_SCREEN_LOCKED to JsonPrimitive(screenLocked),
        KEY_WATCH_BATTERY to (watchBatteryPct?.let { JsonPrimitive(it) } ?: JsonNull),
    )

    companion object {
        const val FOREGROUND = "foreground"
        const val BACKGROUND = "background"
        const val KEY_APP_STATE = "app_state"
        const val KEY_SCREEN_LOCKED = "screen_locked"
        const val KEY_WATCH_BATTERY = "watch_battery_pct"

        fun of(appState: String, screenLocked: Boolean, watchBatteryPct: Int?): UploadContext? {
            val state = when (appState.trim()) {
                FOREGROUND -> FOREGROUND
                BACKGROUND -> BACKGROUND
                else -> return null
            }
            return UploadContext(
                appState = state,
                screenLocked = screenLocked,
                watchBatteryPct = watchBatteryPct?.takeIf { it in 0..100 },
            )
        }
    }
}
