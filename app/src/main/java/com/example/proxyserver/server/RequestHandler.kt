package com.example.proxyserver.server

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.util.HashMap
import java.util.UUID

class RequestHandler(
    private val connectionRegistry: ConnectionRegistry,
    private val logger: LoggingService,
    private val streamScope: CoroutineScope
) {
    companion object {
        private const val MAX_BODY_SIZE = 100 * 1024 * 1024 // 100MB
    }

    suspend fun processRequest(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        logger.info("处理请求: ${session.method} ${session.uri}")

        if (!connectionRegistry.hasActiveConnections()) {
            return errorResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "没有可用的浏览器连接")
        }

        val requestId = generateRequestId()
        val proxyRequest = try {
            buildProxyRequest(session, requestId)
        } catch (ex: IllegalArgumentException) {
            return errorResponse(NanoHTTPD.Response.Status.BAD_REQUEST, ex.message ?: "请求错误")
        }

        val messageQueue = connectionRegistry.createMessageQueue(requestId)

        return try {
            connectionRegistry.forwardRequest(proxyRequest)
            handleResponse(messageQueue)
        } catch (timeout: QueueTimeoutException) {
            errorResponse(NanoHTTPD.Response.Status.GATEWAY_TIMEOUT, "请求超时")
        } catch (closed: QueueClosedException) {
            errorResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "连接已断开")
        } catch (ex: IllegalStateException) {
            errorResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, ex.message ?: "代理错误")
        } catch (ex: Exception) {
            logger.error("请求处理错误: ${ex.message}", ex)
            errorResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "代理错误: ${ex.message}")
        } finally {
            connectionRegistry.removeMessageQueue(requestId)
        }
    }

    private fun generateRequestId(): String = "${System.currentTimeMillis()}_${UUID.randomUUID()}"

    private fun buildProxyRequest(session: NanoHTTPD.IHTTPSession, requestId: String): ProxyRequest {
        val headers = session.headers.mapValues { it.value }
        val queryParams = session.parameters.mapValues { entry -> entry.value.firstOrNull() ?: "" }
        val body = readRequestBody(session)
        return ProxyRequest(
            path = session.uri,
            method = session.method.name,
            headers = headers,
            queryParams = queryParams,
            body = body,
            requestId = requestId
        )
    }

    private fun readRequestBody(session: NanoHTTPD.IHTTPSession): String {
        if (session.method == NanoHTTPD.Method.GET || session.method == NanoHTTPD.Method.HEAD) {
            return ""
        }

        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val contentLength = session.headers["content-length"]?.toLongOrNull() ?: files["postData"]?.length?.toLong()
            if (contentLength != null && contentLength > MAX_BODY_SIZE) {
                throw IllegalArgumentException("请求体过大")
            }
            val postData = files["postData"] ?: return ""
            val postFile = File(postData)
            if (postFile.exists()) {
                postFile.length().takeIf { it <= MAX_BODY_SIZE } ?: throw IllegalArgumentException("请求体过大")
                postFile.readText()
            } else {
                postData
            }
        } catch (ex: NanoHTTPD.ResponseException) {
            throw IllegalArgumentException(ex.message ?: "解析请求失败")
        } catch (ex: Exception) {
            logger.error("读取请求体失败: ${ex.message}", ex)
            ""
        }
    }

    private suspend fun handleResponse(queue: MessageQueue): NanoHTTPD.Response {
        val headerMessage = queue.dequeue()
        if (headerMessage !is QueueMessage.Payload) {
            throw IllegalStateException("无效的响应头")
        }

        if (headerMessage.eventType == "error") {
            val status = statusFromCode(headerMessage.status ?: 500)
            val message = headerMessage.message ?: "代理错误"
            return errorResponse(status, message)
        }

        val statusCode = headerMessage.status ?: 200
        val contentType = headerMessage.headers?.get("Content-Type") ?: NanoHTTPD.MIME_PLAINTEXT
        val responseStatus = statusFromCode(statusCode)

        val pipedStream = java.io.PipedOutputStream()
        val inputStream = java.io.PipedInputStream(pipedStream)

        val response = NanoHTTPD.newChunkedResponse(responseStatus, contentType, inputStream)
        headerMessage.headers?.forEach { (key, value) ->
            if (!key.equals("content-length", ignoreCase = true)) {
                response.addHeader(key, value)
            }
        }

        streamScope.launch {
            streamResponseData(queue, pipedStream, contentType)
        }

        return response
    }

    private suspend fun streamResponseData(queue: MessageQueue, outputStream: OutputStream, contentType: String?) {
        try {
            while (true) {
                val message = try {
                    queue.dequeue()
                } catch (timeout: QueueTimeoutException) {
                    if (contentType?.contains("text/event-stream", ignoreCase = true) == true) {
                        outputStream.write(": keepalive\n\n".toByteArray())
                        outputStream.flush()
                        continue
                    } else {
                        break
                    }
                } catch (_: QueueClosedException) {
                    break
                }

                when (message) {
                    is QueueMessage.StreamEnd -> break
                    is QueueMessage.Payload -> {
                        val data = message.data ?: continue
                        outputStream.write(data.toByteArray())
                        outputStream.flush()
                    }
                }
            }
        } finally {
            outputStream.close()
        }
    }

    private fun statusFromCode(code: Int): NanoHTTPD.Response.Status {
        return NanoHTTPD.Response.Status.values().firstOrNull { it.requestStatus == code }
            ?: when (code) {
                in 200..299 -> NanoHTTPD.Response.Status.OK
                in 300..399 -> NanoHTTPD.Response.Status.REDIRECT
                in 400..499 -> NanoHTTPD.Response.Status.BAD_REQUEST
                in 500..599 -> NanoHTTPD.Response.Status.INTERNAL_ERROR
                else -> NanoHTTPD.Response.Status.OK
            }
    }

    private fun errorResponse(status: NanoHTTPD.Response.Status, message: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, message)
    }
}
