package com.example.proxyserver.core

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggingService(private val serviceName: String = "ProxyServer") {
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    private fun format(level: String, message: String): String {
        val timestamp = formatter.format(Date())
        return "[$level] $timestamp [$serviceName] - $message"
    }

    fun info(message: String) {
        Log.i(serviceName, format("INFO", message))
    }

    fun warn(message: String) {
        Log.w(serviceName, format("WARN", message))
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(serviceName, format("ERROR", message), throwable)
    }

    fun debug(message: String) {
        Log.d(serviceName, format("DEBUG", message))
    }
}
