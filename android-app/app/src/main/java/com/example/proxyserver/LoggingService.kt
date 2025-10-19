package com.example.proxyserver

import android.util.Log

class LoggingService(
    private val serviceName: String = "ProxyServer",
    private val onLog: ((String) -> Unit)? = null
) {

    private fun formatMessage(level: String, message: String): String {
        val timestamp = System.currentTimeMillis()
        return "[$level] $timestamp [$serviceName] - $message"
    }

    fun info(message: String) {
        val formatted = formatMessage("INFO", message)
        Log.i(serviceName, formatted)
        onLog?.invoke(formatted)
    }

    fun warn(message: String) {
        val formatted = formatMessage("WARN", message)
        Log.w(serviceName, formatted)
        onLog?.invoke(formatted)
    }

    fun error(message: String, throwable: Throwable? = null) {
        val formatted = formatMessage("ERROR", message)
        Log.e(serviceName, formatted, throwable)
        onLog?.invoke(formatted)
    }

    fun debug(message: String) {
        val formatted = formatMessage("DEBUG", message)
        Log.d(serviceName, formatted)
        onLog?.invoke(formatted)
    }
}
