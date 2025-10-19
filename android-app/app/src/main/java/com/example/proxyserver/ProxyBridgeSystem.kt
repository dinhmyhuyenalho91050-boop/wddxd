package com.example.proxyserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import kotlin.text.Charsets
import java.util.UUID

class ProxyBridgeSystem(
    private val config: Config = Config(),
    logListener: ((String) -> Unit)? = null
) {

    data class Config(
        val httpPort: Int = 8889,
        val wsPort: Int = 9998,
        val host: String = "0.0.0.0",
        val defaultTimeoutMs: Long = 600_000L
    )

    private val logger = LoggingService("ProxyServer", logListener)
    private val connectionRegistry = ConnectionRegistry(logger)
    private var scope = createScope()

    @Volatile
    private var httpServer: ProxyHttpServer? = null

    @Volatile
    private var webSocketServer: ProxyWebSocketServer? = null

    val isRunning: Boolean
        get() = httpServer != null && webSocketServer != null

    fun start() {
        if (isRunning) return

        val httpServerInstance = ProxyHttpServer(config.host, config.httpPort)
        val webSocketInstance = ProxyWebSocketServer(config.host, config.wsPort)

        try {
            httpServerInstance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            logger.info("HTTP服务器启动: http://${config.host}:${config.httpPort}")
            webSocketInstance.start()
            logger.info("WebSocket服务器启动: ws://${config.host}:${config.wsPort}")

            httpServer = httpServerInstance
            webSocketServer = webSocketInstance
        } catch (ex: Exception) {
            logger.error("启动失败: ${ex.message}", ex)
            httpServerInstance.safeStop()
            webSocketInstance.safeStop()
            throw ex
        }
    }

    fun stop() {
        httpServer?.safeStop()
        webSocketServer?.safeStop()
        httpServer = null
        webSocketServer = null
        connectionRegistry.clearQueues()
        scope.cancel()
        scope = createScope()
    }

    fun hasActiveConnections(): Boolean = connectionRegistry.hasActiveConnections()

    fun status(): String = if (isRunning) {
        "HTTP ${config.httpPort}, WS ${config.wsPort}"
    } else {
        "stopped"
    }

    private fun createScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun NanoHTTPD.safeStop() {
        try {
            stop()
        } catch (ignored: Exception) {
            logger.error("停止HTTP服务器失败", ignored)
        }
    }

    private fun WebSocketServer.safeStop() {
        try {
            stop(2000)
        } catch (ignored: Exception) {
            logger.error("停止WebSocket服务器失败", ignored)
        }
    }

    private inner class ProxyHttpServer(hostname: String, port: Int) : NanoHTTPD(hostname, port) {

        override fun serve(session: IHTTPSession): Response {
            logger.info("处理请求: ${session.method} ${session.uri}")

            if (session.method == Method.OPTIONS) {
                return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "")
                    .applyCors()
            }

            if (!connectionRegistry.hasActiveConnections()) {
                return createErrorResponse(Response.Status.SERVICE_UNAVAILABLE, "没有可用的浏览器连接")
            }

            val requestId = generateRequestId()
            val messageQueue = connectionRegistry.createMessageQueue(requestId)

            return try {
                val proxyRequest = buildProxyRequest(session, requestId)
                forwardRequest(proxyRequest)
                handleResponse(messageQueue)
            } catch (timeout: QueueTimeoutException) {
                createErrorResponse(Response.Status.REQUEST_TIMEOUT, "请求超时")
            } catch (closed: QueueClosedException) {
                createErrorResponse(Response.Status.INTERNAL_ERROR, "连接已关闭")
            } catch (ex: Exception) {
                logger.error("请求处理错误: ${ex.message}", ex)
                createErrorResponse(Response.Status.INTERNAL_ERROR, "代理错误: ${ex.message}")
            } finally {
                connectionRegistry.removeMessageQueue(requestId)
            }
        }

        private fun buildProxyRequest(session: IHTTPSession, requestId: String): JSONObject {
            val files = mutableMapOf<String, String>()
            val body = try {
                session.parseBody(files)
                files["postData"] ?: ""
            } catch (ex: Exception) {
                logger.warn("解析请求体失败: ${ex.message}")
                ""
            }

            val headers = JSONObject()
            session.headers.forEach { (key, value) -> headers.put(key, value) }

            val queryParams = JSONObject()
            session.parameters.forEach { (key, value) ->
                val array = JSONArray()
                value.forEach { array.put(it) }
                queryParams.put(key, array)
            }

            return JSONObject().apply {
                put("path", session.uri)
                put("method", session.method.name)
                put("headers", headers)
                put("query_params", queryParams)
                put("body", body)
                put("request_id", requestId)
            }
        }

        private fun forwardRequest(proxyRequest: JSONObject) {
            val connection = connectionRegistry.getFirstConnection()
                ?: throw IllegalStateException("没有可用的浏览器连接")
            connection.send(proxyRequest.toString())
        }

        private fun handleResponse(messageQueue: MessageQueue): Response {
            when (val headerMessage = messageQueue.dequeue(config.defaultTimeoutMs)) {
                is ProxyMessage.Error -> {
                    val statusCode = headerMessage.status ?: 500
                    val status = lookupStatus(statusCode)
                    return createErrorResponse(status, headerMessage.message)
                }

                is ProxyMessage.ResponseHeaders -> {
                    return streamResponse(messageQueue, headerMessage)
                }

                else -> {
                    return createErrorResponse(Response.Status.INTERNAL_ERROR, "无效的响应类型")
                }
            }
        }

        private fun streamResponse(
            messageQueue: MessageQueue,
            headerMessage: ProxyMessage.ResponseHeaders
        ): Response {
            val outputStream = PipedOutputStream()
            val inputStream: InputStream = PipedInputStream(outputStream)
            val status = lookupStatus(headerMessage.status)
            val contentType = headerMessage.headers["Content-Type"] ?: NanoHTTPD.MIME_PLAINTEXT
            val response = newChunkedResponse(status, contentType, inputStream)

            headerMessage.headers
                .filterKeys { it.lowercase() != "content-type" }
                .forEach { (key, value) -> response.addHeader(key, value) }

            response.applyCors()

            scope.launch {
                val keepAlivePayload = ": keepalive\n\n".toByteArray(Charsets.UTF_8)
                val isEventStream = contentType.contains("text/event-stream", ignoreCase = true)
                try {
                    while (true) {
                        try {
                            when (val message = messageQueue.dequeue(config.defaultTimeoutMs)) {
                                is ProxyMessage.Chunk -> {
                                    outputStream.write(message.data)
                                    outputStream.flush()
                                }

                                ProxyMessage.StreamEnd -> break

                                is ProxyMessage.Error -> {
                                    logger.warn("流式响应收到错误: ${message.message}")
                                    break
                                }

                                else -> {
                                    // ignore
                                }
                            }
                        } catch (timeout: QueueTimeoutException) {
                            if (isEventStream) {
                                outputStream.write(keepAlivePayload)
                                outputStream.flush()
                            } else {
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore streaming exceptions
                } finally {
                    try {
                        outputStream.close()
                    } catch (_: Exception) {
                    }
                }
            }

            return response
        }

        private fun lookupStatus(code: Int): Response.IStatus {
            return Response.Status.lookup(code) ?: object : Response.IStatus {
                override fun getRequestStatus(): Int = code
                override fun getDescription(): String = "HTTP $code"
            }
        }

        private fun createErrorResponse(status: Response.IStatus, message: String): Response {
            return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, message).applyCors()
        }

        private fun generateRequestId(): String = "${System.currentTimeMillis()}_${UUID.randomUUID()}"

        private fun Response.applyCors(): Response {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
            addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin")
            addHeader("Access-Control-Allow-Credentials", "true")
            addHeader("Access-Control-Max-Age", "86400")
            return this
        }
    }

    private inner class ProxyWebSocketServer(host: String, port: Int) : WebSocketServer(InetSocketAddress(host, port)) {
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            connectionRegistry.addConnection(conn, conn.remoteSocketAddress?.address?.hostAddress)
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            connectionRegistry.removeConnection(conn)
        }

        override fun onMessage(conn: WebSocket, message: String) {
            connectionRegistry.handleIncomingMessage(message)
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            logger.error("WebSocket连接错误: ${ex.message}", ex)
        }

        override fun onStart() {
            logger.info("WebSocket服务器监听: ${config.host}:${config.wsPort}")
        }
    }
}
