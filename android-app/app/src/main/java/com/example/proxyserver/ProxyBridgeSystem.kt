package com.example.proxyserver

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Method
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.InetSocketAddress
import kotlin.text.Charsets
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.DEFAULT_BUFFER_SIZE

class ProxyBridgeSystem(
    private val config: Config = Config(),
    logListener: ((String) -> Unit)? = null,
    private val trafficMonitor: ProxyTrafficMonitor = ProxyTrafficMonitor.noOp()
) {

    data class Config(
        val httpPort: Int = 8889,
        val wsPort: Int = 9998,
        val host: String = "127.0.0.1",
        val defaultTimeoutMs: Long = 600_000L
    )

    private val logger = LoggingService("ProxyServer", logListener)
    private val connectionRegistry = ConnectionRegistry(logger, trafficMonitor)
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
    }

    fun hasActiveConnections(): Boolean = connectionRegistry.hasActiveConnections()

    fun status(): String = if (isRunning) {
        "HTTP ${config.httpPort}, WS ${config.wsPort}"
    } else {
        "stopped"
    }

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

    private data class ProxyRequestBundle(
        val payload: JSONObject,
        val rawBody: ByteArray
    )

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
            val queueReleased = AtomicBoolean(false)
            val releaseQueue = {
                if (queueReleased.compareAndSet(false, true)) {
                    connectionRegistry.removeMessageQueue(requestId)
                }
            }

            return try {
                val proxyRequest = buildProxyRequest(session, requestId)
                forwardRequest(proxyRequest)
                handleResponse(messageQueue, releaseQueue)
            } catch (timeout: QueueTimeoutException) {
                releaseQueue()
                createErrorResponse(Response.Status.REQUEST_TIMEOUT, "请求超时")
            } catch (closed: QueueClosedException) {
                releaseQueue()
                createErrorResponse(Response.Status.INTERNAL_ERROR, "连接已关闭")
            } catch (ex: Exception) {
                releaseQueue()
                logger.error("请求处理错误: ${ex.message}", ex)
                createErrorResponse(Response.Status.INTERNAL_ERROR, "代理错误: ${ex.message}")
            }
        }

        private fun buildProxyRequest(session: IHTTPSession, requestId: String): ProxyRequestBundle {
            val bodyBytes = captureRequestBody(session)

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

            val payload = JSONObject().apply {
                put("path", session.uri)
                put("method", session.method.name)
                put("headers", headers)
                put("query_params", queryParams)
                put("body", bodyString)
                put("request_id", requestId)
            }
            return ProxyRequestBundle(payload, bodyBytes)
        }

        private fun normalizeRequestBody(bodyBytes: ByteArray, contentTypeHeader: String?): String {
            if (bodyBytes.isEmpty()) {
                return ""
            }

            val resolvedContentType = contentTypeHeader
                ?.lowercase(Locale.ROOT)
                ?.substringBefore(';')
                ?.trim()
                ?: ""
            val charset = resolveCharset(contentTypeHeader)

            if (isJsonContentType(resolvedContentType)) {
                decodeText(bodyBytes, charset)?.let { return it }
                if (charset != StandardCharsets.UTF_8) {
                    decodeText(bodyBytes, StandardCharsets.UTF_8)?.let { return it }
                }
                return bufferString(bodyBytes)
            }

            if (isFormUrlEncoded(resolvedContentType)) {
                decodeText(bodyBytes, charset)?.let { decoded ->
                    stringifyUrlEncodedBody(decoded, charset)?.let { return it }
                    return decoded
                }
                if (charset != StandardCharsets.UTF_8) {
                    decodeText(bodyBytes, StandardCharsets.UTF_8)?.let { decoded ->
                        stringifyUrlEncodedBody(decoded, StandardCharsets.UTF_8)?.let { return it }
                        return decoded
                    }
                }
                return bufferString(bodyBytes)
            }

            if (resolvedContentType.startsWith("text/") ||
                resolvedContentType.contains("javascript") ||
                resolvedContentType.contains("xml")
            ) {
                decodeText(bodyBytes, charset)?.let { return it }
                if (charset != StandardCharsets.UTF_8) {
                    decodeText(bodyBytes, StandardCharsets.UTF_8)?.let { return it }
                }
                return bufferString(bodyBytes)
            }

            return bufferString(bodyBytes)
        }

        private fun captureRequestBody(session: IHTTPSession): ByteArray {
            val inputStream = session.inputStream ?: return ByteArray(0)
            val buffer = java.io.ByteArrayOutputStream()
            val tempBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val declaredLength = session.headers["content-length"]?.toLongOrNull()

            try {
                if (declaredLength != null) {
                    var remaining = declaredLength
                    while (remaining > 0) {
                        val bytesToRead = minOf(tempBuffer.size.toLong(), remaining).toInt()
                        val read = inputStream.read(tempBuffer, 0, bytesToRead)
                        if (read == -1) break
                        buffer.write(tempBuffer, 0, read)
                        remaining -= read
                    }
                } else {
                    while (true) {
                        val read = inputStream.read(tempBuffer)
                        if (read == -1) break
                        buffer.write(tempBuffer, 0, read)
                    }
                }
            } catch (ex: Exception) {
                logger.warn("读取请求体失败: ${ex.message}")
            }

            val bodyBytes = buffer.toByteArray()

            if (declaredLength != null && bodyBytes.isNotEmpty() && bodyBytes.size.toLong() != declaredLength) {
                logger.warn("请求体长度与Content-Length不匹配: 预期=${declaredLength}, 实际=${bodyBytes.size}")
            }

            return bodyBytes
        }

        private fun bufferString(bodyBytes: ByteArray): String {
            val dataArray = JSONArray()
            bodyBytes.forEach { byte -> dataArray.put(byte.toInt() and 0xFF) }
            return JSONObject().apply {
                put("type", "Buffer")
                put("data", dataArray)
            }.toString()
        }

        private fun resolveCharset(contentTypeHeader: String?): Charset {
            if (contentTypeHeader.isNullOrBlank()) {
                return StandardCharsets.UTF_8
            }

            val charsetToken = contentTypeHeader.split(';')
                .drop(1)
                .map { it.trim() }
                .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
                ?.trim('"', '\'', ' ')
                ?.takeIf { it.isNotEmpty() }

            if (charsetToken.isNullOrEmpty()) {
                return StandardCharsets.UTF_8
            }

            return try {
                Charset.forName(charsetToken)
            } catch (_: UnsupportedCharsetException) {
                StandardCharsets.UTF_8
            } catch (_: IllegalArgumentException) {
                StandardCharsets.UTF_8
            }
        }

        private fun decodeText(bodyBytes: ByteArray, charset: Charset): String? {
            return try {
                val decoded = String(bodyBytes, charset)
                val roundTripped = decoded.toByteArray(charset)
                if (roundTripped.contentEquals(bodyBytes)) {
                    decoded
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun isJsonContentType(contentType: String): Boolean {
            if (contentType.isEmpty()) {
                return false
            }

            return contentType == "application/json" ||
                contentType.endsWith("+json") ||
                contentType == "text/json"
        }

        private fun isFormUrlEncoded(contentType: String): Boolean {
            return contentType == "application/x-www-form-urlencoded"
        }

        private fun stringifyUrlEncodedBody(body: String, charset: Charset): String? {
            val pairs = body.split('&').filter { it.isNotEmpty() }
            if (pairs.isEmpty()) {
                return "{}"
            }

            val result = JSONObject()

            for (pair in pairs) {
                val idx = pair.indexOf('=')
                val rawKey = if (idx >= 0) pair.substring(0, idx) else pair
                val rawValue = if (idx >= 0) pair.substring(idx + 1) else ""

                val key = try {
                    java.net.URLDecoder.decode(rawKey, charset.name())
                } catch (_: Exception) {
                    return null
                }

                val value = try {
                    java.net.URLDecoder.decode(rawValue, charset.name())
                } catch (_: Exception) {
                    return null
                }

                addFormField(result, key, value)
            }

            return result.toString()
        }

        private fun addFormField(container: JSONObject, key: String, value: String) {
            if (key.endsWith("[]")) {
                val normalizedKey = key.dropLast(2)
                val existing = container.opt(normalizedKey)
                val array = when (existing) {
                    is JSONArray -> existing
                    JSONObject.NULL, null -> JSONArray()
                    else -> JSONArray().apply { put(existing) }
                }
                array.put(value)
                container.put(normalizedKey, array)
                return
            }

            val existing = container.opt(key)
            when (existing) {
                null, JSONObject.NULL -> container.put(key, value)
                is JSONArray -> existing.put(value)
                else -> container.put(key, JSONArray().apply {
                    put(existing)
                    put(value)
                })
            }
        }

        private fun forwardRequest(proxyRequest: ProxyRequestBundle) {
            val connection = connectionRegistry.getFirstConnection()
                ?: throw IllegalStateException("没有可用的浏览器连接")
            val requestId = proxyRequest.payload.optString("request_id")
            trafficMonitor.onHttpRequestForwarded(requestId, proxyRequest.payload, proxyRequest.rawBody)
            connection.send(proxyRequest.payload.toString())
        }

        private fun handleResponse(
            messageQueue: MessageQueue,
            releaseQueue: () -> Unit
        ): Response {
            when (val headerMessage = messageQueue.dequeue(config.defaultTimeoutMs)) {
                is ProxyMessage.Error -> {
                    val statusCode = headerMessage.status ?: 500
                    val status = lookupStatus(statusCode)
                    releaseQueue()
                    return createErrorResponse(status, headerMessage.message)
                }

                is ProxyMessage.ResponseHeaders -> {
                    return streamResponse(messageQueue, headerMessage, releaseQueue)
                }

                else -> {
                    releaseQueue()
                    return createErrorResponse(Response.Status.INTERNAL_ERROR, "无效的响应类型")
                }
            }
        }

        private fun streamResponse(
            messageQueue: MessageQueue,
            headerMessage: ProxyMessage.ResponseHeaders,
            releaseQueue: () -> Unit
        ): Response {
            val status = lookupStatus(headerMessage.status)
            val contentTypeValues = headerMessage.headers.entries
                .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                ?.value
            val contentType = normalizeContentType(contentTypeValues?.firstOrNull())
            val isEventStream = contentType.contains("text/event-stream", ignoreCase = true)

            val responseStream = QueueBackedInputStream(
                messageQueue = messageQueue,
                timeoutMs = config.defaultTimeoutMs,
                keepAliveEnabled = isEventStream,
                onStreamComplete = releaseQueue
            )

            val response = newChunkedResponse(status, contentType, responseStream)

            val accumulatedHeaders = mutableMapOf<String, String>()
            headerMessage.headers.forEach { (rawKey, values) ->
                if (rawKey.equals("content-type", ignoreCase = true)) return@forEach
                if (rawKey.equals("content-length", ignoreCase = true)) return@forEach
                if (rawKey.equals("transfer-encoding", ignoreCase = true)) return@forEach

                val key = rawKey
                values.forEach { rawValue ->
                    val value = sanitizeHeaderValue(rawValue)
                    val existing = accumulatedHeaders[key]
                    accumulatedHeaders[key] = when {
                        existing.isNullOrEmpty() -> value
                        value.isEmpty() -> existing
                        else -> "$existing\r\n$key: $value"
                    }
                }
            }

            accumulatedHeaders.forEach { (key, value) ->
                response.addHeader(key, value)
            }

            response.applyCors()

            return response
        }

        private inner class QueueBackedInputStream(
            private val messageQueue: MessageQueue,
            private val timeoutMs: Long,
            private val keepAliveEnabled: Boolean,
            private val onStreamComplete: () -> Unit
        ) : InputStream() {
            private var buffer = ByteArray(0)
            private var position = 0
            private var closed = false
            private val keepAlivePayload = ": keepalive\n\n".toByteArray(Charsets.UTF_8)
            private var completionNotified = false

            override fun read(): Int {
                val data = ensureBuffer() ?: return -1
                return data[position++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val data = ensureBuffer() ?: return -1
                val bytesAvailable = data.size - position
                val bytesToCopy = minOf(len, bytesAvailable)
                System.arraycopy(data, position, b, off, bytesToCopy)
                position += bytesToCopy
                return bytesToCopy
            }

            private fun ensureBuffer(): ByteArray? {
                if (closed) {
                    return null
                }

                if (position < buffer.size) {
                    return buffer
                }

                while (true) {
                    val message = try {
                        messageQueue.dequeue(timeoutMs)
                    } catch (_: QueueClosedException) {
                        closeStream()
                        return null
                    } catch (_: QueueTimeoutException) {
                        if (keepAliveEnabled) {
                            buffer = keepAlivePayload
                            position = 0
                            return buffer
                        }
                        closeStream()
                        return null
                    }

                    when (message) {
                        is ProxyMessage.Chunk -> {
                            if (message.data.isEmpty()) {
                                continue
                            }
                            buffer = message.data
                            position = 0
                            return buffer
                        }

                        ProxyMessage.StreamEnd -> {
                            closeStream()
                            return null
                        }

                        is ProxyMessage.Error -> {
                            logger.warn("流式响应收到错误: ${message.message}")
                            closeStream()
                            return null
                        }

                        else -> {
                            // Ignore unexpected events
                        }
                    }
                }
            }

            private fun closeStream() {
                closed = true
                buffer = ByteArray(0)
                position = 0
                if (!completionNotified) {
                    completionNotified = true
                    onStreamComplete()
                }
            }

            override fun close() {
                closeStream()
                super.close()
            }
        }

        private fun lookupStatus(code: Int): Response.IStatus {
            return Response.Status.lookup(code) ?: object : Response.IStatus {
                override fun getRequestStatus(): Int = code
                override fun getDescription(): String = "HTTP $code"
            }
        }

        private fun normalizeContentType(contentType: String?): String {
            val resolved = contentType?.takeIf { it.isNotBlank() } ?: NanoHTTPD.MIME_PLAINTEXT
            val lower = resolved.lowercase(Locale.ROOT)
            if (lower.contains("charset=")) {
                return resolved
            }

            val needsUtf8 = lower.startsWith("text/") ||
                lower.contains("json") ||
                lower.contains("javascript") ||
                lower.contains("xml") ||
                lower.contains("form-urlencoded")

            return if (needsUtf8) {
                "$resolved; charset=utf-8"
            } else {
                resolved
            }
        }

        private fun createErrorResponse(status: Response.IStatus, message: String): Response {
            return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, message).applyCors()
        }

        private fun sanitizeHeaderValue(value: String?): String {
            if (value == null) return ""
            return value.replace("\r", "").replace("\n", "")
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
