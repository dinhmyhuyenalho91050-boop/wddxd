package com.example.proxyserver.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.proxyserver.R
import com.example.proxyserver.databinding.ActivityMainBinding
import com.example.proxyserver.server.ProxyServerService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isRunning = intent.getBooleanExtra(ProxyServerService.EXTRA_IS_RUNNING, false)
            val httpPort = intent.getIntExtra(ProxyServerService.EXTRA_HTTP_PORT, DEFAULT_HTTP_PORT)
            val wsPort = intent.getIntExtra(ProxyServerService.EXTRA_WS_PORT, DEFAULT_WS_PORT)
            updateUiState(isRunning, httpPort, wsPort)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startButton.setOnClickListener { startProxyService() }
        binding.stopButton.setOnClickListener { stopProxyService() }
        binding.openSettingsButton.setOnClickListener { openBatteryOptimizationSettings() }

        updateUiState(false, DEFAULT_HTTP_PORT, DEFAULT_WS_PORT)
        refreshBatteryOptimizationHint()
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryOptimizationHint()
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(statusReceiver, IntentFilter(ProxyServerService.ACTION_STATUS))
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun startProxyService() {
        val intent = Intent(this, ProxyServerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopProxyService() {
        stopService(Intent(this, ProxyServerService::class.java))
    }

    private fun updateUiState(isRunning: Boolean, httpPort: Int, wsPort: Int) {
        binding.statusText.text = if (isRunning) {
            getString(R.string.status_running, httpPort, wsPort)
        } else {
            getString(R.string.status_stopped)
        }
        binding.startButton.isEnabled = !isRunning
        binding.stopButton.isEnabled = isRunning
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun refreshBatteryOptimizationHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val ignoring = powerManager.isIgnoringBatteryOptimizations(packageName)
            binding.batteryHint.isVisible = !ignoring
            binding.openSettingsButton.isVisible = !ignoring
        } else {
            binding.batteryHint.isVisible = false
            binding.openSettingsButton.isVisible = false
        }
    }

    companion object {
        private const val DEFAULT_HTTP_PORT = 8889
        private const val DEFAULT_WS_PORT = 9998
    }
}
