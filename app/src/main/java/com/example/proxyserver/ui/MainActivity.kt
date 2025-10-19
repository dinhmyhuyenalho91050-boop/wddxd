package com.example.proxyserver.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.proxyserver.R
import com.example.proxyserver.databinding.ActivityMainBinding
import com.example.proxyserver.service.ForegroundProxyService
import com.example.proxyserver.service.ProxyServiceStateStore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRunning = false

    override fun onStart() {
        super.onStart()
        isRunning = ProxyServiceStateStore.isRunning(this)
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startButton.setOnClickListener {
            ForegroundProxyService.start(this)
            isRunning = true
            ProxyServiceStateStore.setRunning(this, true)
            updateStatus()
            maybeRequestIgnoreBatteryOptimization()
        }

        binding.stopButton.setOnClickListener {
            ForegroundProxyService.stop(this)
            isRunning = false
            ProxyServiceStateStore.setRunning(this, false)
            updateStatus()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        isRunning = ProxyServiceStateStore.isRunning(this)
        updateStatus()
    }

    private fun updateStatus() {
        binding.statusText.text = if (isRunning) {
            getString(R.string.status_running)
        } else {
            getString(R.string.status_stopped)
        }
    }

    private fun maybeRequestIgnoreBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            val ignoring = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (!ignoring) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
