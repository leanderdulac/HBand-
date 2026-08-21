package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object HrNotificationHelper {

    private const val CHANNEL_ID = "hr_threshold_alerts"
    private const val CHANNEL_NAME = "Heart Rate Threshold Alerts"
    private const val CHANNEL_DESC = "Triggers local alerts when heart rate exceeds or drops below target limits"
    private const val NOTIFICATION_ID_HIGH = 1001
    private const val NOTIFICATION_ID_LOW = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendHighHrNotification(context: Context, currentHr: Int, threshold: Int) {
        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ High Heart Rate Alert ($currentHr BPM)")
            .setContentText("Your heart rate of $currentHr BPM exceeds your upper threshold limit ($threshold BPM).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_HIGH, builder.build())
    }

    fun sendLowHrNotification(context: Context, currentHr: Int, threshold: Int) {
        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ Low Heart Rate Alert ($currentHr BPM)")
            .setContentText("Your heart rate of $currentHr BPM is below your lower threshold limit ($threshold BPM).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_LOW, builder.build())
    }
}
