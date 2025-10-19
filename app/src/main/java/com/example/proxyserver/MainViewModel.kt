package com.example.proxyserver

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxyserver.service.ProxyServerService
import com.example.proxyserver.service.ProxyServerService.LocalBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class MainViewModel : ViewModel() {
    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState

    private var binder: LocalBinder? = null
    private var serviceJob: Job? = null

    fun bindService(localBinder: LocalBinder) {
        binder = localBinder
        serviceJob?.cancel()
        serviceJob = viewModelScope.launch {
            localBinder.serviceState.collect { state ->
                _serviceState.value = state
            }
        }
    }

    fun unbindService() {
        serviceJob?.cancel()
        binder = null
        _serviceState.value = ServiceState()
    }

    fun startService(context: Context) {
        val intent = Intent(context, ProxyServerService::class.java)
        context.startForegroundService(intent)
    }

    fun stopService(context: Context) {
        val intent = Intent(context, ProxyServerService::class.java)
        context.stopService(intent)
    }
}

data class ServiceState(
    val isRunning: Boolean = false,
    val httpHost: String = "127.0.0.1",
    val httpPort: Int = 8889,
    val wsHost: String = "127.0.0.1",
    val wsPort: Int = 9998
)
