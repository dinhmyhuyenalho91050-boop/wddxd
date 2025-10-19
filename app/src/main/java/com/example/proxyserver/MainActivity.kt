package com.example.proxyserver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.proxyserver.databinding.ActivityMainBinding
import com.example.proxyserver.service.BatteryOptimizationHelper
import com.example.proxyserver.service.ProxyServerService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var serviceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ProxyServerService.LocalBinder ?: return
            serviceBound = true
            viewModel.bindService(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            viewModel.unbindService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            viewModel.serviceState.collectLatest { state ->
                binding.statusText.text = if (state.isRunning) {
                    getString(R.string.service_status_running)
                } else {
                    getString(R.string.service_status_stopped)
                }
                binding.toggleServiceButton.text = if (state.isRunning) {
                    getString(R.string.stop_service)
                } else {
                    getString(R.string.start_service)
                }

                val addressText = if (state.isRunning) {
                    getString(
                        R.string.server_running_on,
                        "http://${state.httpHost}:${state.httpPort}",
                        "ws://${state.wsHost}:${state.wsPort}"
                    )
                } else {
                    ""
                }
                binding.addressText.text = addressText
            }
        }

        binding.toggleServiceButton.setOnClickListener {
            if (viewModel.serviceState.value.isRunning) {
                viewModel.stopService(this)
            } else {
                viewModel.startService(this)
            }
        }

        binding.batteryOptimizationButton.setOnClickListener {
            if (!BatteryOptimizationHelper.isIgnoringOptimizations(this)) {
                BatteryOptimizationHelper.requestIgnoreOptimizations(this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, ProxyServerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        super.onStop()
    }
}
