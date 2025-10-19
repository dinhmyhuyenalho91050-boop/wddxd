package com.example.proxyserver

import android.util.Base64
import org.java_websocket.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.text.Charsets

class ConnectionRegistry(
    private val logger: LoggingService,
    private val trafficMonitor: ProxyTrafficMonitor = ProxyTrafficMonitor.noOp()
) {

    private val connections = Collections.synchronizedSet(mutableSetOf<WebSocket>())
    private val messageQueues = ConcurrentHashMap<String, MessageQueue>()

    fun addConnection(webSocket: WebSocket, clientAddress: String?) {
        connections.add(webSocket)
        logger.info("新客户端连接: ${clientAddress ?: "unknown"}")
    }

    fun removeConnection(webSocket: WebSocket) {
        connections.remove(webSocket)
        logger.info("客户端连接断开")
        clearQueues()
    }

    fun hasActiveConnections(): Boolean = synchronized(connections) { connections.isNotEmpty() }

    fun getFirstConnection(): WebSocket? = synchronized(connections) { connections.firstOrNull() }

    fun createMessageQueue(requestId: String): MessageQueue {
        val queue = MessageQueue()
        messageQueues[requestId] = queue
        return queue
    }

    fun removeMessageQueue(requestId: String) {
        messageQueues.remove(requestId)?.close()
    }

    fun clearQueues() {
        messageQueues.values.forEach { it.close() }
        messageQueues.clear()
    }

    fun handleIncomingMessage(messageData: String) {
        try {
            val parsed = JSONObject(messageData)
            val requestId = parsed.optString("request_id")
            trafficMonitor.onIncomingWebSocketMessage(requestId.takeIf { it.isNotEmpty() }, messageData, parsed)
            if (requestId.isEmpty()) {
                logger.warn("收到无效消息：缺少request_id")
                return
            }

            val queue = messageQueues[requestId]
            if (queue == null) {
                logger.warn("收到未知请求ID的消息: $requestId")
                return
            }

            when (parsed.optString("event_type")) {
                "response_headers" -> {
                    val headersJson = parsed.optJSONObject("headers")
                    val headers = mutableMapOf<String, MutableList<String>>()
                    headersJson?.keys()?.forEach { key ->
                        val value = headersJson.get(key)
                        when (value) {
                            is org.json.JSONArray -> {
                                val list = headers.getOrPut(key) { mutableListOf() }
                                for (i in 0 until value.length()) {
                                    list.add(value.optString(i))
                                }
                            }
                            null, JSONObject.NULL -> {
                                headers.getOrPut(key) { mutableListOf() }.add("")
                            }
                            else -> {
                                headers.getOrPut(key) { mutableListOf() }.add(value.toString())
                            }
                        }
                    }
                    val finalizedHeaders = headers.mapValues { it.value.toList() }
                    val status = parsed.optInt("status", 200)
                    val message = ProxyMessage.ResponseHeaders(status, finalizedHeaders)
                    trafficMonitor.onProxyMessageQueued(requestId, message)
                    queue.enqueue(message)
                }

                "chunk" -> {
                    val chunkBytes = decodeChunkPayload(parsed)
                    val message = ProxyMessage.Chunk(chunkBytes)
                    trafficMonitor.onProxyMessageQueued(requestId, message)
                    queue.enqueue(message)
                }

                "error" -> {
                    val status = if (parsed.has("status")) parsed.optInt("status") else null
                    val message = parsed.optString("message", "Unknown error")
                    val proxyMessage = ProxyMessage.Error(status, message)
                    trafficMonitor.onProxyMessageQueued(requestId, proxyMessage)
                    queue.enqueue(proxyMessage)
                }

                "stream_close" -> {
                    trafficMonitor.onProxyMessageQueued(requestId, ProxyMessage.StreamEnd)
                    queue.enqueue(ProxyMessage.StreamEnd)
                }

                else -> logger.warn("未知的事件类型: ${parsed.optString("event_type")}")
            }
        } catch (ex: Exception) {
            logger.error("解析WebSocket消息失败", ex)
        }
    }

    private fun decodeChunkPayload(parsed: JSONObject): ByteArray {
        // 优先使用二进制安全的字段
        val base64Data = parsed.optString("data_base64", null)
        if (!base64Data.isNullOrEmpty()) {
            return decodeBase64(base64Data)
        }

        val dataValue = parsed.opt("data")
        if (dataValue == null || dataValue == JSONObject.NULL) {
            return ByteArray(0)
        }

        if (dataValue is JSONObject && dataValue.optString("type") == "Buffer") {
            val dataArray = dataValue.optJSONArray("data")
            if (dataArray != null) {
                val buffer = ByteArray(dataArray.length())
                for (i in 0 until dataArray.length()) {
                    buffer[i] = (dataArray.optInt(i) and 0xFF).toByte()
                }
                return buffer
            }
        }

        if (dataValue is JSONArray) {
            val buffer = ByteArray(dataValue.length())
            for (i in 0 until dataValue.length()) {
                buffer[i] = (dataValue.optInt(i) and 0xFF).toByte()
            }
            return buffer
        }

        val encoding = parsed.optString("encoding")
        val isBase64 = parsed.optBoolean("is_base64", false) ||
            parsed.optBoolean("binary", false) ||
            encoding.equals("base64", ignoreCase = true)

        return when {
            isBase64 -> decodeBase64(dataValue.toString(), dataValue)
            dataValue is String -> dataValue.toByteArray(Charsets.UTF_8)
            else -> dataValue.toString().toByteArray(Charsets.UTF_8)
        }
    }

    private fun decodeBase64(data: String, fallbackValue: Any? = null): ByteArray {
        return try {
            Base64.decode(data, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            logger.warn("Base64数据解码失败，回退到原始数据")
            when (fallbackValue) {
                is String -> fallbackValue.toByteArray(Charsets.UTF_8)
                else -> ByteArray(0)
            }
        }
    }
}
