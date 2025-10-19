package com.example.proxyserver.service

import android.util.Log
import com.example.proxyserver.core.ProxyServerSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProxyServerManager {
    private var serverSystem: ProxyServerSystem? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val system = ProxyServerSystem()
        serverSystem = system
        job = scope.launch(Dispatchers.IO) {
            try {
                system.start()
            } catch (ex: Exception) {
                Log.e("ProxyServerManager", "Failed to start server", ex)
            }
        }
    }

    fun stop() {
        val system = serverSystem ?: return
        job?.cancel()
        job = null
        serverSystem = null
        CoroutineScope(Dispatchers.IO).launch {
            system.stop()
        }
    }

    fun isRunning(): Boolean = job?.isActive == true
}
