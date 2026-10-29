package com.example.data.hband

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HBandMeasureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != HBandMeasureAlarm.ACTION_MEASURE_DUE) return
        HBandBleService.startMeasure(context)
    }
}
