package com.example.proxyserver.server

import org.java_websocket.WebSocket
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry(private val logger: LoggingService) {
    private val connections = Collections.synchronizedSet(mutableSetOf<WebSocket>())
    private val messageQueues = ConcurrentHashMap<String, MessageQueue>()

    fun addConnection(socket: WebSocket) {
        connections.add(socket)
        logger.info("新客户端连接: ${socket.remoteSocketAddress}")
    }

    fun removeConnection(socket: WebSocket) {
        connections.remove(socket)
        logger.info("客户端连接断开: ${socket.remoteSocketAddress}")
        messageQueues.values.forEach { it.close() }
        messageQueues.clear()
    }

    fun hasActiveConnections(): Boolean = connections.isNotEmpty()

    fun getFirstConnection(): WebSocket? = synchronized(connections) {
        connections.firstOrNull()
    }

    fun createMessageQueue(requestId: String): MessageQueue {
        val queue = MessageQueue()
        messageQueues[requestId] = queue
        return queue
    }

    fun removeMessageQueue(requestId: String) {
        messageQueues.remove(requestId)?.close()
    }

    fun forwardRequest(request: ProxyRequest) {
        val connection = getFirstConnection() ?: throw IllegalStateException("没有可用的浏览器连接")
        val payload = JSONObject().apply {
            put("path", request.path)
            put("method", request.method)
            put("headers", JSONObject(request.headers))
            put("query_params", JSONObject(request.queryParams))
            put("body", request.body)
            put("request_id", request.requestId)
        }
        connection.send(payload.toString())
    }

    fun handleIncomingMessage(rawMessage: String) {
        try {
            val message = JSONObject(rawMessage)
            val requestId = message.optString("request_id", null)
            if (requestId.isNullOrEmpty()) {
                logger.warn("收到无效消息：缺少request_id")
                return
            }

            val queue = messageQueues[requestId]
            if (queue == null) {
                logger.warn("收到未知请求ID的消息: $requestId")
                return
            }

            routeMessage(message, queue)
        } catch (exception: JSONException) {
            logger.error("解析WebSocket消息失败: ${exception.message}", exception)
        }
    }

    private fun routeMessage(message: JSONObject, queue: MessageQueue) {
        when (val eventType = message.optString("event_type")) {
            "response_headers", "chunk", "error" -> {
                val headersJson = message.optJSONObject("headers")
                val headers = mutableMapOf<String, String>()
                if (headersJson != null) {
                    headersJson.keys().forEach { key ->
                        headers[key] = headersJson.optString(key)
                    }
                }
                queue.enqueue(
                    QueueMessage.Payload(
                        eventType = eventType,
                        status = message.optInt("status", 200),
                        headers = if (headers.isEmpty()) null else headers,
                        data = message.optString("data", null),
                        message = message.optString("message", null)
                    )
                )
            }

            "stream_close" -> queue.enqueue(QueueMessage.StreamEnd)
            else -> logger.warn("未知的事件类型: $eventType")
        }
    }
}
