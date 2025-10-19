package com.example.proxyserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ProxyBridgeService : Service() {

    private val logBuffer = StringBuilder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { ContextCompat.getSystemService(this, NotificationManager::class.java) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var bridgeSystem: ProxyBridgeSystem

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        acquireWifiLock()
        initializeBridge()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_content_running)))
        startBridge()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBridge()
        releaseWakeLock()
        releaseWifiLock()
        serviceScope.cancel()
        broadcastStatus(false, getString(R.string.status_stopped))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val serviceIntent = Intent(applicationContext, ProxyBridgeService::class.java)
        ContextCompat.startForegroundService(applicationContext, serviceIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBridge() {
        if (!::bridgeSystem.isInitialized) {
            initializeBridge()
        }
        val system = bridgeSystem
        serviceScope.launch {
            try {
                system.start()
                broadcastStatus(true, formatStatusMessage())
            } catch (ex: Exception) {
                logMessage("Failed to start bridge: ${ex.message}")
                broadcastStatus(false, "启动失败: ${ex.message}")
                stopSelf()
            }
        }
    }

    private fun stopBridge() {
        if (!::bridgeSystem.isInitialized) return
        try {
            bridgeSystem.stop()
        } catch (ex: Exception) {
            logMessage("Failed to stop bridge: ${ex.message}")
        }
    }

    private fun broadcastStatus(isRunning: Boolean, message: String) {
        val intent = Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_RUNNING, isRunning)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_LOG, logBuffer.toString())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        updateNotification(message)
    }

    private fun logMessage(message: String) {
        synchronized(logBuffer) {
            logBuffer.appendLine(message)
            if (logBuffer.length > MAX_LOG_BUFFER) {
                logBuffer.delete(0, logBuffer.length - MAX_LOG_BUFFER)
            }
        }
        val intent = Intent(ACTION_LOG_UPDATE).apply {
            putExtra(EXTRA_LOG, logBuffer.toString())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ProxyBridgeService::class.java).apply { action = ACTION_STOP }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ProxyBridge::WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun acquireWifiLock() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ProxyBridge::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wifiLock = null
    }

    private fun formatStatusMessage(): String {
        val ports = if (::bridgeSystem.isInitialized) bridgeSystem.status() else "initializing"
        return getString(R.string.notification_content_running) + " ($ports)"
    }

    private fun initializeBridge() {
        if (::bridgeSystem.isInitialized) return
        bridgeSystem = ProxyBridgeSystem(
            logListener = { logMessage(it) }
        )
    }

    companion object {
        const val ACTION_STATUS = "com.example.proxyserver.ACTION_STATUS"
        const val ACTION_LOG_UPDATE = "com.example.proxyserver.ACTION_LOG_UPDATE"
        const val EXTRA_RUNNING = "extra_running"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_LOG = "extra_log"
        const val ACTION_STOP = "com.example.proxyserver.action.STOP"
        private const val CHANNEL_ID = "proxy_bridge_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_LOG_BUFFER = 32_768
    }
}
