package com.example.data.hband

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.HBandHealthSyncApp

/** Pure decisions for the BLE watchdog, unit-tested without Android. */
object ReconnectWatchdogPolicy {
    const val INTERVAL_MS = 7 * 60_000L

    fun shouldReconnect(autoReconnect: Boolean, hasPersistedSession: Boolean, hardwareConnected: Boolean, userStopped: Boolean): Boolean =
        autoReconnect && hasPersistedSession && !hardwareConnected && !userStopped

    fun shouldKeepArmed(autoReconnect: Boolean, hasPersistedSession: Boolean, userStopped: Boolean): Boolean =
        autoReconnect && hasPersistedSession && !userStopped

    /** Exact alarms need SCHEDULE_EXACT_ALARM on 31+; fall back to an inexact Doze-allowed alarm. */
    fun useExactAlarm(sdkInt: Int, canScheduleExact: Boolean): Boolean = sdkInt < 31 || canScheduleExact
}

/**
 * Wakes every ~7 minutes (even in Doze) to reconnect the VE30 if the link dropped and
 * flush pending readings. Mirrors the H Band app's reconnector, without extra permissions.
 */
object HBandReconnectWatchdog {
    private const val REQUEST_CODE = 2102
    const val ACTION_TICK = "com.example.data.hband.WATCHDOG_TICK"

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = SystemClock.elapsedRealtime() + ReconnectWatchdogPolicy.INTERVAL_MS
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        try {
            if (ReconnectWatchdogPolicy.useExactAlarm(Build.VERSION.SDK_INT, canExact)) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent(context))
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent(context))
            }
        } catch (error: SecurityException) {
            // Permission revoked between the check and the call: degrade, never crash.
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent(context))
        }
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, HBandWatchdogReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class HBandWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != HBandReconnectWatchdog.ACTION_TICK) return
        val app = context.applicationContext as? HBandHealthSyncApp ?: return
        val manager = app.readyBleManagerOrNull() ?: return
        val persisted = manager.hasPersistedSession()
        val userStopped = app.startupStopRequested
        if (!ReconnectWatchdogPolicy.shouldKeepArmed(manager.isAutoReconnectEnabled, persisted, userStopped)) return
        try {
            if (ReconnectWatchdogPolicy.shouldReconnect(
                    manager.isAutoReconnectEnabled, persisted, manager.isHardwareConnected.value, userStopped)
            ) {
                // Starting the FGS from an exact alarm is an allowed background-start exemption.
                HBandBleService.reconnect(context)
            }
            app.requestImmediateUpload()
        } catch (error: Exception) {
            android.util.Log.e("HBandWatchdog", "Watchdog tick failed", error)
        }
        HBandReconnectWatchdog.schedule(context)
    }
}
