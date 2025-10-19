package com.example.proxyserver.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {
    fun isIgnoringOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
