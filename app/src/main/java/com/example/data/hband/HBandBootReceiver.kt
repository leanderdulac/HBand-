package com.example.data.hband

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HBandBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            HBandBleService.startIfPersistedSession(context)
            // WorkManager persists work across reboots; KEEP makes this a no-op when already enqueued.
            com.example.worker.HBandWorkScheduler.schedulePeriodicIngest(context)
        }
    }
}
