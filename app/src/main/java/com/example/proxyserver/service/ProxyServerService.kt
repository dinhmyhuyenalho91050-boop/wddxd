package com.example.proxyserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.proxyserver.MainActivity
import com.example.proxyserver.R
import com.example.proxyserver.ServiceState
import com.example.proxyserver.server.ProxyServerSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel

class ProxyServerService : LifecycleService() {
    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(getString(R.string.service_status_starting)))
        scope.launch {
            startServer()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { startServer() }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        runBlocking {
            serverSystem.stop()
            _serviceState.value = ServiceState(isRunning = false)
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private suspend fun startServer() {
        if (serverSystem.isRunning()) return
        try {
            serverSystem.start()
            val config = serverSystem.serverConfig()
            val state = ServiceState(
                isRunning = true,
                httpHost = config.host,
                httpPort = config.httpPort,
                wsHost = config.host,
                wsPort = config.wsPort
            )
            _serviceState.value = state
            updateNotification(state)
        } catch (ex: Exception) {
            _serviceState.value = ServiceState(isRunning = false)
            updateNotification(_serviceState.value)
            throw ex
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(state: ServiceState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val content = if (state.isRunning) {
            "HTTP ${state.httpPort} / WS ${state.wsPort}"
        } else {
            getString(R.string.service_status_stopped)
        }
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    inner class LocalBinder : Binder() {
        val serviceState: StateFlow<ServiceState>
            get() = _serviceState
    }

    companion object {
        private const val CHANNEL_ID = "proxy_server_channel"
        private const val NOTIFICATION_ID = 101

        private val serverSystem = ProxyServerSystem()
        private val _serviceState = MutableStateFlow(ServiceState())

        @Suppress("UNUSED_PARAMETER")
        suspend fun ensureServerInitialized(context: Context) {
            if (!serverSystem.isRunning()) {
                // no-op placeholder to keep config warm
            }
        }
    }
}
