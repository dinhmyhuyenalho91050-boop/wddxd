package com.example.proxyserver

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.proxyserver.service.ProxyServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProxyServerApplication : Application() {
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 预热服务器实例，确保应用启动后即可快速启动服务
        preloadScope.launch {
            ProxyServerService.ensureServerInitialized(applicationContext)
        }
    }

    fun startProxyService() {
        val intent = Intent(this, ProxyServerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
