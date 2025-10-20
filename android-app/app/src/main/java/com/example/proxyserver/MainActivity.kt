package com.example.proxyserver

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var toggleButton: Button
    private lateinit var logView: TextView

    private var isRunning = false
    private var startRequested = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startProxyServiceInternal()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_notification_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
            startRequested = false
        }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ProxyBridgeService.ACTION_STATUS) {
                isRunning = intent.getBooleanExtra(ProxyBridgeService.EXTRA_RUNNING, false)
                val message = intent.getStringExtra(ProxyBridgeService.EXTRA_MESSAGE)
                val logs = intent.getStringExtra(ProxyBridgeService.EXTRA_LOG)
                updateStatus(message)
                updateLogs(logs)
            }
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ProxyBridgeService.ACTION_LOG_UPDATE) {
                val logs = intent.getStringExtra(ProxyBridgeService.EXTRA_LOG)
                updateLogs(logs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.serverStatus)
        toggleButton = findViewById(R.id.toggleButton)
        logView = findViewById(R.id.logView)

        toggleButton.setOnClickListener {
            if (isRunning) {
                stopProxyService()
            } else {
                if (ensureNotificationPermission()) {
                    startProxyServiceInternal()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.registerReceiver(statusReceiver, IntentFilter(ProxyBridgeService.ACTION_STATUS))
        lbm.registerReceiver(logReceiver, IntentFilter(ProxyBridgeService.ACTION_LOG_UPDATE))
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        super.onStop()
    }

    private fun ensureNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        return if (granted) {
            true
        } else {
            if (!startRequested) {
                startRequested = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            false
        }
    }

    private fun startProxyServiceInternal() {
        val intent = Intent(this, ProxyBridgeService::class.java)
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (ex: ForegroundServiceStartNotAllowedException) {
            updateStatus(getString(R.string.status_stopped))
            Toast.makeText(
                this,
                getString(R.string.error_start_foreground_service_not_allowed),
                Toast.LENGTH_LONG
            ).show()
        } catch (securityException: SecurityException) {
            updateStatus(getString(R.string.status_stopped))
            Toast.makeText(
                this,
                getString(R.string.error_start_service, securityException.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        } catch (illegalState: IllegalStateException) {
            updateStatus(getString(R.string.status_stopped))
            Toast.makeText(
                this,
                getString(R.string.error_start_service, illegalState.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopProxyService() {
        val intent = Intent(this, ProxyBridgeService::class.java)
        stopService(intent)
    }

    private fun updateStatus(message: String?) {
        statusView.text = message ?: getString(R.string.status_stopped)
        toggleButton.text = if (isRunning) getString(R.string.button_stop) else getString(R.string.button_start)
    }

    private fun updateLogs(logs: String?) {
        logView.text = logs ?: ""
        logView.post {
            val layout = logView.layout
            if (layout != null) {
                val scrollAmount = layout.getLineTop(logView.lineCount) - logView.height
                if (scrollAmount > 0) {
                    logView.scrollTo(0, scrollAmount)
                } else {
                    logView.scrollTo(0, 0)
                }
            }
        }
    }

}
