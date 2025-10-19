package com.example.proxyserver

import org.json.JSONObject

/**
 * Observer interface that exposes the raw payloads flowing through the proxy bridge.
 * Implementations can persist the data or stream it elsewhere for side-by-side
 * comparisons against the reference JavaScript implementation.
 */
interface ProxyTrafficMonitor {

    /**
     * Invoked immediately before the proxy forwards an HTTP request to the WebSocket client.
     * [requestJson] contains the exact payload that will be sent while [rawBody] holds the
     * original body bytes as received from the upstream caller (before any normalization).
     */
    fun onHttpRequestForwarded(requestId: String, requestJson: JSONObject, rawBody: ByteArray) {}

    /**
     * Invoked for each raw message arriving from the WebSocket browser client. [parsed] is
     * provided when the payload can be decoded as JSON; otherwise it is null.
     */
    fun onIncomingWebSocketMessage(requestId: String?, rawMessage: String, parsed: JSONObject?) {}

    /**
     * Invoked when a parsed WebSocket payload has been transformed into a [ProxyMessage] and
     * scheduled for delivery to the waiting HTTP caller.
     */
    fun onProxyMessageQueued(requestId: String, message: ProxyMessage) {}

    companion object {
        fun noOp(): ProxyTrafficMonitor = object : ProxyTrafficMonitor {}
    }
}

