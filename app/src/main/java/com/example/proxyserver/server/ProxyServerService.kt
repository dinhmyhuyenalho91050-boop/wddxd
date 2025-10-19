package com.example.proxyserver.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.proxyserver.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ProxyServerService : LifecycleService() {
    private val logger = LoggingService("ProxyServerService")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverConfig = ServerConfig()
    private val serverSystem = ProxyServerSystem(serverConfig, serviceScope, logger)

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_stopped)))
        acquireWakeLock()
        startServers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            serverSystem.stop()
        } catch (exception: Exception) {
            logger.error("停止代理服务器失败: ${exception.message}", exception)
        }
        serviceScope.cancel()
        releaseWakeLock()
        sendStatusBroadcast(false)
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startServers() {
        serviceScope.launch {
            try {
                serverSystem.start()
                sendStatusBroadcast(true)
                updateNotification(getString(R.string.status_running, serverConfig.httpPort, serverConfig.wsPort))
            } catch (exception: Exception) {
                logger.error("启动代理服务器失败: ${exception.message}", exception)
                sendStatusBroadcast(false)
                stopSelf()
            }
        }
    }

    private fun sendStatusBroadcast(isRunning: Boolean) {
        val intent = Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_STATUS_DESCRIPTION, serverSystem.statusDescription())
            putExtra(EXTRA_HTTP_PORT, serverConfig.httpPort)
            putExtra(EXTRA_WS_PORT, serverConfig.wsPort)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ProxyServer::WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } finally {
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val stopIntent = Intent(this, ProxyServerService::class.java).apply { action = ACTION_STOP }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_service), pendingStopIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = buildNotification(content)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_STATUS = "com.example.proxyserver.ACTION_STATUS"
        const val ACTION_STOP = "com.example.proxyserver.ACTION_STOP"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_STATUS_DESCRIPTION = "extra_status_description"
        const val EXTRA_HTTP_PORT = "extra_http_port"
        const val EXTRA_WS_PORT = "extra_ws_port"
        private const val CHANNEL_ID = "proxy_server_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
