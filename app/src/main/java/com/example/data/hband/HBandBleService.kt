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
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForeground() {
        if (foregroundStarted) return
        createChannel()
        startInForeground(
            buildNotification(
                getString(R.string.ble_session_connecting),
                connected = false
            )
        )
        foregroundStarted = true
        observeBleState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires startForeground() even when we stop immediately.
        ensureForeground()
        if (intent?.action == ACTION_STOP) {
            (application as? HBandHealthSyncApp)?.requestBleSessionStop()
            HBandReconnectWatchdog.cancel(this)
            stopSelfSafely()
            return START_NOT_STICKY
        }
        if (bleManager() == null) { stopSelfSafely(); return START_NOT_STICKY }
        HBandReconnectWatchdog.schedule(this)
        when (intent?.action) {
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
        (application as? HBandHealthSyncApp)?.readyBleManagerOrNull()

    private fun observeBleState() {
        val manager = bleManager() ?: return
        observeJob?.cancel()
        var wasConnected = false
        observeJob = serviceScope.launch {
            combine(manager.connectedDevice, manager.isHardwareConnected, manager.sessionPhase) { device, hardware, phase ->
                Triple(device, hardware, phase)
            }.collect { (device, hardware, phase) ->
                val connected = phase == BleSessionPhase.CONNECTED ||
                    (device?.isConnected == true && hardware)
                if (connected && !wasConnected) {
                    // Link (re)established: push anything queued while the watch was away.
                    (application as? HBandHealthSyncApp)?.requestImmediateUpload()
                }
                wasConnected = connected
                val name = device?.name ?: getString(R.string.ble_session_device_fallback)
                val text = textFor(phase, name)
                val notification = buildNotification(text, connected, phase)
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            android.util.Log.e("HBandBleService", "startForeground failed", error)
            stopSelf()
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

    private fun titleFor(phase: BleSessionPhase, connected: Boolean): String = when {
        connected || phase == BleSessionPhase.CONNECTED -> getString(R.string.app_name)
        phase == BleSessionPhase.RECONNECTING -> getString(R.string.ble_session_reconnecting_title)
        phase == BleSessionPhase.NOT_FOUND -> getString(R.string.ble_session_not_found)
        phase == BleSessionPhase.DISCONNECTED -> getString(R.string.ble_session_disconnected)
        else -> getString(R.string.ble_session_connecting)
    }

    private fun textFor(phase: BleSessionPhase, name: String): String = when (phase) {
        BleSessionPhase.CONNECTED -> getString(R.string.ble_session_connected, name)
        BleSessionPhase.RECONNECTING -> getString(R.string.ble_session_reconnecting, name)
        BleSessionPhase.NOT_FOUND -> getString(R.string.ble_session_not_found_text)
        BleSessionPhase.DISCONNECTED -> getString(R.string.ble_session_disconnected)
        BleSessionPhase.CONNECTING -> getString(R.string.ble_session_connecting)
    }

    private fun buildNotification(
        content: String,
        connected: Boolean,
        phase: BleSessionPhase = if (connected) BleSessionPhase.CONNECTED else BleSessionPhase.CONNECTING,
    ): Notification {
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
            .setContentTitle(titleFor(phase, connected))
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
            if ((context.applicationContext as? HBandHealthSyncApp)?.readyBleManagerOrNull() == null) return
            val intent = Intent(context, HBandBleService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (error: Exception) {
                android.util.Log.e("HBandBleService", "Unable to start BLE foreground service", error)
            }
        }

        fun startIfPersistedSession(context: Context) {
            val app = context.applicationContext as? HBandHealthSyncApp ?: return
            if (app.startupStopRequested) return
            val manager = app.readyBleManagerOrNull() ?: return
            if (manager.hasPersistedSession() && manager.isAutoReconnectEnabled) {
                start(context)
            }
        }

        fun reconnect(context: Context) {
            if ((context.applicationContext as? HBandHealthSyncApp)?.readyBleManagerOrNull() == null) return
            val intent = Intent(context, HBandBleService::class.java).apply { action = ACTION_RECONNECT }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (error: Exception) {
                android.util.Log.e("HBandBleService", "Unable to request BLE reconnect", error)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HBandBleService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
