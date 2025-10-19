package com.example.proxyserver.server

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggingService(private val serviceName: String = "ProxyServer") {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    private fun formatMessage(level: String, message: String): String {
        val timestamp = dateFormat.format(Date())
        return "[$level] $timestamp [$serviceName] - $message"
    }

    fun info(message: String) {
        Log.i(serviceName, formatMessage("INFO", message))
    }

    fun warn(message: String) {
        Log.w(serviceName, formatMessage("WARN", message))
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(serviceName, formatMessage("ERROR", message), throwable)
    }

    fun debug(message: String) {
        Log.d(serviceName, formatMessage("DEBUG", message))
    }
}
