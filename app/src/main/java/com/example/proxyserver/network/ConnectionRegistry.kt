package com.example.proxyserver.network

import com.example.proxyserver.core.LoggingService
import com.example.proxyserver.model.ProxyChunk
import com.example.proxyserver.model.ProxyError
import com.example.proxyserver.model.ProxyEvent
import com.example.proxyserver.model.ProxyResponseHeaders
import com.example.proxyserver.model.ProxyStreamClose
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.origin
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.CoroutineContext

class ConnectionRegistry(
    private val logger: LoggingService,
    coroutineContext: CoroutineContext = Dispatchers.IO
) {
    private val scope = CoroutineScope(coroutineContext)
    private val connections = CopyOnWriteArraySet<WebSocketConnection>()
    private val messageQueues = ConcurrentHashMap<String, MessageQueue>()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun addConnection(session: DefaultWebSocketServerSession, call: ApplicationCall) {
        val connection = WebSocketConnection(session)
        connections.add(connection)
        logger.info("新客户端连接: ${call.request.origin.remoteHost}")

        scope.launch {
            try {
                session.incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        handleIncoming(frame.readText())
                    }
                }
            } catch (ex: Exception) {
                logger.error("WebSocket连接错误: ${ex.message}", ex)
            } finally {
                removeConnection(connection)
            }
        }
    }

    suspend fun removeConnection(connection: WebSocketConnection) {
        mutex.withLock {
            if (connections.remove(connection)) {
                logger.info("客户端连接断开")
                messageQueues.values.forEach { it.close() }
                messageQueues.clear()
            }
        }
        try {
            connection.close()
        } catch (ignored: Exception) {
            logger.debug("连接关闭异常: ${ignored.message}")
        }
    }

    fun hasActiveConnections(): Boolean = connections.isNotEmpty()

    fun getFirstConnection(): WebSocketConnection? = connections.firstOrNull()

    fun createMessageQueue(requestId: String): MessageQueue {
        val queue = MessageQueue()
        messageQueues[requestId] = queue
        return queue
    }

    fun removeMessageQueue(requestId: String) {
        messageQueues.remove(requestId)?.close()
    }

    private suspend fun handleIncoming(message: String) {
        try {
            val jsonElement = json.parseToJsonElement(message)
            val obj = jsonElement as? JsonObject ?: return
            val eventType = (obj["event_type"] as? JsonPrimitive)?.content
            val requestId = (obj["request_id"] as? JsonPrimitive)?.content
            if (requestId.isNullOrBlank()) {
                logger.warn("收到无效消息：缺少request_id")
                return
            }
            val queue = messageQueues[requestId]
            if (queue == null) {
                logger.warn("收到未知请求ID的消息: $requestId")
                return
            }
            val event: ProxyEvent? = when (eventType) {
                "response_headers" -> json.decodeFromJsonElement<ProxyResponseHeaders>(obj)
                "chunk" -> json.decodeFromJsonElement<ProxyChunk>(obj)
                "error" -> json.decodeFromJsonElement<ProxyError>(obj)
                "stream_close" -> json.decodeFromJsonElement<ProxyStreamClose>(obj)
                else -> {
                    logger.warn("未知的事件类型: $eventType")
                    null
                }
            }
            if (event != null) {
                queue.enqueue(event)
            }
        } catch (ex: Exception) {
            logger.error("解析WebSocket消息失败", ex)
        }
    }

    class WebSocketConnection(private val session: DefaultWebSocketServerSession) {
        private val sendMutex = Mutex()

        suspend fun sendText(text: String) {
            sendMutex.withLock {
                session.send(Frame.Text(text))
            }
        }

        suspend fun close() {
            sendMutex.withLock {
                session.close(CloseReason(CloseReason.Codes.NORMAL, "shutdown"))
            }
        }
    }
}
