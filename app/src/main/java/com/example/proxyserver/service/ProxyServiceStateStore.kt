package com.example.proxyserver.service

import android.content.Context

object ProxyServiceStateStore {
    private const val PREFS_NAME = "proxy_service_state"
    private const val KEY_RUNNING = "running"

    fun setRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, running)
            .apply()
    }

    fun isRunning(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RUNNING, false)
    }
}
