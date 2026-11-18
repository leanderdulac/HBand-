package com.example.data.hband

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HBandBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            try {
                HBandBleService.startIfPersistedSession(context)
            } catch (error: Exception) {
                android.util.Log.e("HBandBootReceiver", "BLE session resume failed", error)
            }
            try {
                // WorkManager persists work across reboots; KEEP makes this a no-op when already enqueued.
                com.example.worker.HBandWorkScheduler.schedulePeriodicIngest(context)
            } catch (error: Exception) {
                android.util.Log.e("HBandBootReceiver", "Ingest reschedule failed", error)
            }
        }
    }
}
