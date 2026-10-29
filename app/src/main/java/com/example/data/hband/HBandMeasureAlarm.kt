package com.example.data.hband

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Acorda o processo na hora da próxima medição. O Handler de 30 min morre
 * com o app e o Doze segura ele; este alarme continua valendo.
 */
object HBandMeasureAlarm {
    const val ACTION_MEASURE_DUE = "com.example.data.hband.MEASURE_DUE"
    const val PREF_NEXT_MEASURE_AT = "next_measure_at_ms"
    const val PREF_MEASURE_ARMED = "measure_wake_armed"
    private const val PREFS = "hband_settings"
    private const val REQUEST_CODE = 2401
    private const val TAG = "HBandMeasureAlarm"

    fun arm(context: Context, triggerAtMs: Long) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_NEXT_MEASURE_AT, triggerAtMs)
            .putBoolean(PREF_MEASURE_ARMED, true)
            .apply()
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(app)
        val trigger = triggerAtMs.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
            if (exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarme exato negado, usando janela de idle: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending(app))
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_NEXT_MEASURE_AT)
            .putBoolean(PREF_MEASURE_ARMED, false)
            .apply()
    }

    /** WorkManager: se o alarme se perdeu e a rodada já venceu, sobe o serviço. */
    fun nudgeIfDue(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(HBandBleManager.PREF_AUTO_RECONNECT, true)) return
        if (!prefs.getBoolean(PREF_MEASURE_ARMED, false)) return
        val mac = prefs.getString(HBandBleManager.PREF_LAST_MAC, null)?.trim().orEmpty()
        if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(mac)) return
        val nextAt = prefs.getLong(PREF_NEXT_MEASURE_AT, Long.MAX_VALUE)
        if (nextAt > System.currentTimeMillis()) return
        HBandBleService.startMeasure(context.applicationContext)
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, HBandMeasureReceiver::class.java).apply {
            action = ACTION_MEASURE_DUE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
