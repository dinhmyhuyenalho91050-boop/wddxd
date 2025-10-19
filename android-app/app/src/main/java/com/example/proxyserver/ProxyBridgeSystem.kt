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
        val host: String = "127.0.0.1",
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
            var queueManagedByResponse = false

            return try {
                val proxyRequest = buildProxyRequest(session, requestId)
                forwardRequest(proxyRequest)
                handleResponse(requestId, messageQueue).also {
                    queueManagedByResponse = true
                }
            } catch (timeout: QueueTimeoutException) {
                createErrorResponse(Response.Status.REQUEST_TIMEOUT, "请求超时")
            } catch (closed: QueueClosedException) {
                createErrorResponse(Response.Status.INTERNAL_ERROR, "连接已关闭")
            } catch (ex: Exception) {
                logger.error("请求处理错误: ${ex.message}", ex)
                createErrorResponse(Response.Status.INTERNAL_ERROR, "代理错误: ${ex.message}")
            } finally {
                if (!queueManagedByResponse) {
                    connectionRegistry.removeMessageQueue(requestId)
                }
            }
        }

        private fun buildProxyRequest(session: IHTTPSession, requestId: String): JSONObject {
            val files = mutableMapOf<String, String>()
            val bodyBytes = try {
                session.parseBody(files)
                val rawBody = files["postData"] ?: ""
                readBodyBytes(rawBody)
            } catch (ex: Exception) {
                logger.warn("解析请求体失败: ${ex.message}")
                ByteArray(0)
            }

            val headers = JSONObject()
            session.headers.forEach { (key, value) -> headers.put(key.lowercase(), value) }

            val bodyString = normalizeRequestBody(bodyBytes, session.headers["content-type"])

            val queryParams = JSONObject()
            session.parameters.forEach { (rawKey, values) ->
                val key = if (rawKey.endsWith("[]")) rawKey.dropLast(2) else rawKey
                when {
                    values.isEmpty() -> queryParams.put(key, "")
                    values.size == 1 -> {
                        val existing = queryParams.opt(key)
                        val value = values.first()
                        when (existing) {
                            null, JSONObject.NULL -> queryParams.put(key, value)
                            is JSONArray -> existing.put(value)
                            else -> {
                                val array = JSONArray()
                                array.put(existing)
                                array.put(value)
                                queryParams.put(key, array)
                            }
                        }
                    }
                    else -> {
                        val array = when (val existing = queryParams.opt(key)) {
                            is JSONArray -> existing
                            null, JSONObject.NULL -> JSONArray()
                            else -> JSONArray().apply { put(existing) }
                        }
                        values.forEach { array.put(it) }
                        queryParams.put(key, array)
                    }
                }
            }

            return JSONObject().apply {
                put("path", session.uri)
                put("method", session.method.name)
                put("headers", headers)
                put("query_params", queryParams)
                put("body", bodyString)
                put("request_id", requestId)
            }
        }

        private fun readBodyBytes(rawBody: String): ByteArray {
            if (rawBody.isEmpty()) {
                return ByteArray(0)
            }

            val file = java.io.File(rawBody)
            return if (file.exists()) {
                val bytes = file.readBytes()
                if (!file.delete()) {
                    logger.debug("临时请求体文件删除失败: ${file.absolutePath}")
                }
                bytes
            } else {
                rawBody.toByteArray(Charsets.UTF_8)
            }
        }

        private fun normalizeRequestBody(bodyBytes: ByteArray, contentTypeHeader: String?): String {
            if (bodyBytes.isEmpty()) {
                return ""
            }

            val contentType = contentTypeHeader?.lowercase()?.substringBefore(';')?.trim() ?: ""

            return when {
                contentType.contains("application/json") -> normalizeJsonBody(bodyBytes)
                contentType.contains("application/x-www-form-urlencoded") -> normalizeFormBody(bodyBytes)
                contentType.startsWith("text/") || contentType.contains("javascript") ->
                    bodyBytes.toString(Charsets.UTF_8)
                else -> bufferString(bodyBytes)
            }
        }

        private fun normalizeJsonBody(bodyBytes: ByteArray): String {
            val bodyText = bodyBytes.toString(Charsets.UTF_8)
            return try {
                val trimmed = bodyText.trim()
                when {
                    trimmed.startsWith("[") -> JSONArray(trimmed).toString()
                    trimmed.startsWith("{") -> JSONObject(trimmed).toString()
                    else -> bodyText
                }
            } catch (_: Exception) {
                bodyText
            }
        }

        private fun normalizeFormBody(bodyBytes: ByteArray): String {
            val bodyText = bodyBytes.toString(Charsets.UTF_8)
            if (bodyText.isEmpty()) {
                return ""
            }

            val formObject = JSONObject()
            bodyText.split("&").forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val parts = pair.split("=", limit = 2)
                val decodedKey = java.net.URLDecoder.decode(parts[0], "UTF-8")
                val key = if (decodedKey.endsWith("[]")) decodedKey.dropLast(2) else decodedKey
                val value = if (parts.size > 1) java.net.URLDecoder.decode(parts[1], "UTF-8") else ""
                if (formObject.has(key)) {
                    val existing = formObject.get(key)
                    when (existing) {
                        is JSONArray -> existing.put(value)
                        else -> {
                            val array = JSONArray()
                            array.put(existing)
                            array.put(value)
                            formObject.put(key, array)
                        }
                    }
                } else {
                    formObject.put(key, value)
                }
            }
            return formObject.toString()
        }

        private fun bufferString(bodyBytes: ByteArray): String {
            val dataArray = JSONArray()
            bodyBytes.forEach { byte -> dataArray.put(byte.toInt() and 0xFF) }
            return JSONObject().apply {
                put("type", "Buffer")
                put("data", dataArray)
            }.toString()
        }

        private fun forwardRequest(proxyRequest: JSONObject) {
            val connection = connectionRegistry.getFirstConnection()
                ?: throw IllegalStateException("没有可用的浏览器连接")
            connection.send(proxyRequest.toString())
        }

        private fun handleResponse(requestId: String, messageQueue: MessageQueue): Response {
            when (val headerMessage = messageQueue.dequeue(config.defaultTimeoutMs)) {
                is ProxyMessage.Error -> {
                    val statusCode = headerMessage.status ?: 500
                    val status = lookupStatus(statusCode)
                    connectionRegistry.removeMessageQueue(requestId)
                    return createErrorResponse(status, headerMessage.message)
                }

                is ProxyMessage.ResponseHeaders -> {
                    return streamResponse(requestId, messageQueue, headerMessage)
                }

                else -> {
                    connectionRegistry.removeMessageQueue(requestId)
                    return createErrorResponse(Response.Status.INTERNAL_ERROR, "无效的响应类型")
                }
            }
        }

        private fun streamResponse(
            requestId: String,
            messageQueue: MessageQueue,
            headerMessage: ProxyMessage.ResponseHeaders
        ): Response {
            val outputStream = PipedOutputStream()
            val inputStream: InputStream = PipedInputStream(outputStream)
            val status = lookupStatus(headerMessage.status)
            val contentTypeValues = headerMessage.headers.entries
                .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                ?.value
            val contentType = contentTypeValues?.firstOrNull() ?: NanoHTTPD.MIME_PLAINTEXT
            val response = newChunkedResponse(status, contentType, inputStream)

            headerMessage.headers.forEach { (key, values) ->
                if (key.equals("content-type", ignoreCase = true)) return@forEach
                values.forEach { value -> response.addHeader(key, value) }
            }

            response.applyCors()

            val streamJob = scope.launch {
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

            streamJob.invokeOnCompletion {
                connectionRegistry.removeMessageQueue(requestId)
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
