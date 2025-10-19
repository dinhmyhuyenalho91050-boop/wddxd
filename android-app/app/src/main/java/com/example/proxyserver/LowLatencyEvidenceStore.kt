package com.example.proxyserver

import android.content.Context
import android.content.SharedPreferences

class LowLatencyEvidenceStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasRecentEvidence(): Boolean {
        val expiry = preferences.getLong(KEY_EVIDENCE_EXPIRY, 0L)
        if (expiry == 0L) {
            return false
        }

        val now = System.currentTimeMillis()
        return if (now <= expiry) {
            true
        } else {
            preferences.edit().remove(KEY_EVIDENCE_EXPIRY).apply()
            false
        }
    }

    fun recordEvidence(durationMs: Long = DEFAULT_EVIDENCE_DURATION_MS) {
        val expiry = System.currentTimeMillis() + durationMs
        preferences.edit().putLong(KEY_EVIDENCE_EXPIRY, expiry).apply()
    }

    fun clearEvidence() {
        preferences.edit().remove(KEY_EVIDENCE_EXPIRY).apply()
    }

    companion object {
        private const val PREFS_NAME = "proxy_bridge_network_tuning"
        private const val KEY_EVIDENCE_EXPIRY = "low_latency_evidence_expiry"
        private const val DEFAULT_EVIDENCE_DURATION_MS = 6 * 60 * 60 * 1000L
    }
}
