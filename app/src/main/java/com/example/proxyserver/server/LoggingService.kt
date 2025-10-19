package com.example.proxyserver.server

import android.util.Log
import java.time.Instant
import java.time.format.DateTimeFormatter

class LoggingService(private val serviceName: String = "ProxyServer") {
    private fun formatMessage(level: String, message: String): String {
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        return "[$level] $timestamp [$serviceName] - $message"
    }

    fun info(message: String) {
        Log.i(serviceName, formatMessage("INFO", message))
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(serviceName, formatMessage("ERROR", message), throwable)
    }

    fun warn(message: String) {
        Log.w(serviceName, formatMessage("WARN", message))
    }

    fun debug(message: String) {
        Log.d(serviceName, formatMessage("DEBUG", message))
    }
}
