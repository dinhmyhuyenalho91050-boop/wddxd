package com.example.proxyserver

import android.util.Base64
import org.java_websocket.WebSocket
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.text.Charsets

class ConnectionRegistry(private val logger: LoggingService) {

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
                    val headers = mutableMapOf<String, String>()
                    headersJson?.keys()?.forEach { key ->
                        headers[key] = headersJson.optString(key)
                    }
                    val status = parsed.optInt("status", 200)
                    queue.enqueue(ProxyMessage.ResponseHeaders(status, headers))
                }

                "chunk" -> {
                    val chunkBytes = decodeChunkPayload(parsed)
                    queue.enqueue(ProxyMessage.Chunk(chunkBytes))
                }

                "error" -> {
                    val status = if (parsed.has("status")) parsed.optInt("status") else null
                    val message = parsed.optString("message", "Unknown error")
                    queue.enqueue(ProxyMessage.Error(status, message))
                }

                "stream_close" -> queue.enqueue(ProxyMessage.StreamEnd)

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

        val encoding = parsed.optString("encoding")
        val isBase64 = parsed.optBoolean("is_base64", false) ||
            parsed.optBoolean("binary", false) ||
            encoding.equals("base64", ignoreCase = true)

        return when {
            isBase64 -> decodeBase64(dataValue.toString())
            dataValue is String -> dataValue.toByteArray(Charsets.UTF_8)
            else -> dataValue.toString().toByteArray(Charsets.UTF_8)
        }
    }

    private fun decodeBase64(data: String): ByteArray {
        return try {
            Base64.decode(data, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            logger.warn("Base64数据解码失败，返回空字节数组")
            ByteArray(0)
        }
    }
}
