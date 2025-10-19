package com.example.proxyserver.server

import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry(private val logger: LoggingService) {
    private val connections = mutableSetOf<DefaultWebSocketServerSession>()
    private val messageQueues = ConcurrentHashMap<String, MessageQueue>()
    private val json = Json { ignoreUnknownKeys = true }

    fun hasActiveConnections(): Boolean = connections.isNotEmpty()

    fun createMessageQueue(requestId: String): MessageQueue {
        val queue = MessageQueue()
        messageQueues[requestId] = queue
        return queue
    }

    fun removeMessageQueue(requestId: String) {
        messageQueues.remove(requestId)?.close()
    }

    fun getFirstConnection(): DefaultWebSocketServerSession? = connections.firstOrNull()

    suspend fun register(session: DefaultWebSocketServerSession) {
        connections += session
        logger.info("新客户端连接: ${session.call.request.origin.remoteHost}")
        try {
            session.incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> handleIncomingMessage(frame.readText())
                    is Frame.Close -> logger.info("客户端主动关闭连接")
                    else -> {}
                }
            }
        } catch (ex: Exception) {
            logger.error("WebSocket连接错误: ${ex.message}", ex)
        } finally {
            unregister(session)
        }
    }

    private fun unregister(session: DefaultWebSocketServerSession) {
        if (connections.remove(session)) {
            logger.info("客户端连接断开")
            messageQueues.values.forEach { it.close() }
            messageQueues.clear()
        }
    }

    suspend fun forwardRequest(request: ProxyRequest) {
        val connection = getFirstConnection()
            ?: throw IllegalStateException("没有可用的浏览器连接")
        val payload = json.encodeToString(request)
        connection.send(Frame.Text(payload))
    }

    private suspend fun handleIncomingMessage(raw: String) {
        try {
            val message = json.decodeFromString<ProxyResponseMessage>(raw)
            val requestId = message.requestId
            if (requestId == null) {
                logger.warn("收到无效消息：缺少request_id")
                return
            }
            val queue = messageQueues[requestId]
            if (queue != null) {
                queue.enqueue(message)
            } else {
                logger.warn("收到未知请求ID的消息: $requestId")
            }
        } catch (ex: Exception) {
            logger.error("解析WebSocket消息失败", ex)
        }
    }
}
