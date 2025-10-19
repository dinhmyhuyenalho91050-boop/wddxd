package com.example.proxyserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.proxyserver.service.ForegroundProxyService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ForegroundProxyService.start(context)
        }
    }
}
