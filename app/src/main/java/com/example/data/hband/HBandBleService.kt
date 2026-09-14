package com.example.data.hband

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.HBandHealthSyncApp
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the VE30/HBand BLE session alive through Doze
 * and process backgrounding. The actual GATT/Veepoo session lives on
 * [HBandBleManager] (Application-scoped); this service only holds a
 * connected-device FGS notification and asks the manager to reconnect.
 */
class HBandBleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(
            buildNotification(
                getString(R.string.ble_session_connecting),
                connected = false
            )
        )
        observeBleState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                bleManager()?.disconnectDevice()
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> bleManager()?.reconnectLastDevice()
            else -> {
                if (bleManager()?.connectedDevice?.value?.isConnected != true) {
                    bleManager()?.reconnectLastDevice()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun bleManager(): HBandBleManager? =
        (application as? HBandHealthSyncApp)?.bleManager

    private fun observeBleState() {
        val manager = bleManager() ?: return
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            combine(manager.connectedDevice, manager.isHardwareConnected) { device, hardware ->
                device to hardware
            }.collect { (device, hardware) ->
                val connected = device?.isConnected == true && hardware
                val text = when {
                    connected -> getString(
                        R.string.ble_session_connected,
                        device?.name ?: getString(R.string.ble_session_device_fallback)
                    )
                    manager.isAutoReconnectEnabled && !device?.macAddress.isNullOrBlank() ->
                        getString(
                            R.string.ble_session_reconnecting,
                            device?.name ?: getString(R.string.ble_session_device_fallback)
                        )
                    else -> getString(R.string.ble_session_disconnected)
                }
                val notification = buildNotification(text, connected)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)

                val userStopped = !manager.isAutoReconnectEnabled && !connected
                if (userStopped && device?.isConnected != true) {
                    // Stay up while auto-reconnect is still hunting; otherwise drop the FGS.
                    if (device == null || !manager.hasPersistedSession()) {
                        stopSelfSafely()
                    }
                }
            }
        }
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ble_session_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.ble_session_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String, connected: Boolean): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, HBandBleService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(
                if (connected) getString(R.string.app_name)
                else getString(R.string.ble_session_connecting)
            )
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.ble_session_stop),
                stopPending
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "hband_ble_session"
        const val NOTIFICATION_ID = 2101
        const val ACTION_STOP = "com.example.data.hband.STOP_BLE_SESSION"
        const val ACTION_RECONNECT = "com.example.data.hband.RECONNECT_BLE_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, HBandBleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startIfPersistedSession(context: Context) {
            val app = context.applicationContext as? HBandHealthSyncApp ?: return
            if (app.bleManager.hasPersistedSession() && app.bleManager.isAutoReconnectEnabled) {
                start(context)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HBandBleService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
