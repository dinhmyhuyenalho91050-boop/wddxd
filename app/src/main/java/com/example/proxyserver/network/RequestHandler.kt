package com.example.proxyserver.network

import com.example.proxyserver.core.LoggingService
import com.example.proxyserver.model.ProxyChunk
import com.example.proxyserver.model.ProxyError
import com.example.proxyserver.model.ProxyRequest
import com.example.proxyserver.model.ProxyResponseHeaders
import com.example.proxyserver.model.ProxyStreamClose
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryParameters
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.util.toMap
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readByte
import io.ktor.utils.io.core.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.Charset
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlin.random.Random

class RequestHandler(
    private val connectionRegistry: ConnectionRegistry,
    private val logger: LoggingService,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun process(call: ApplicationCall) {
        logger.info("处理请求: ${call.request.httpMethod.value} ${call.request.uri}")

        if (!connectionRegistry.hasActiveConnections()) {
            respondError(call, HttpStatusCode.ServiceUnavailable, "没有可用的浏览器连接")
            return
        }

        val requestId = generateRequestId()
        val proxyRequest = buildProxyRequest(call, requestId)
        val queue = connectionRegistry.createMessageQueue(requestId)

        try {
            forwardRequest(proxyRequest)
            handleResponse(call, queue)
        } catch (ex: Exception) {
            logger.error("请求处理错误: ${ex.message}", ex)
            respondError(call, HttpStatusCode.InternalServerError, "代理错误: ${ex.message}")
        } finally {
            connectionRegistry.removeMessageQueue(requestId)
        }
    }

    private fun generateRequestId(): String {
        return "${System.currentTimeMillis()}_${Random.nextInt()}"
    }

    private suspend fun buildProxyRequest(call: ApplicationCall, requestId: String): ProxyRequest {
        val headers = call.request.headers.toMap().mapValues { it.value.firstOrNull() ?: "" }
        val queryParams = call.request.queryParameters.toMap()
        val body = withContext(Dispatchers.IO) {
            val channel = call.receiveChannel()
            channel.toByteArray().toString(Charset.defaultCharset())
        }
        return ProxyRequest(
            path = call.request.path(),
            method = call.request.httpMethod.value,
            headers = headers,
            queryParams = queryParams,
            body = body,
            requestId = requestId
        )
    }

    private suspend fun forwardRequest(proxyRequest: ProxyRequest) {
        val connection = connectionRegistry.getFirstConnection()
            ?: throw IllegalStateException("缺少可用连接")
        val payload = json.encodeToString(proxyRequest)
        connection.sendText(payload)
    }

    private suspend fun handleResponse(call: ApplicationCall, queue: MessageQueue) {
        when (val headerEvent = queue.dequeue()) {
            is ProxyError -> {
                respondError(call, HttpStatusCode.fromValue(headerEvent.status), headerEvent.message)
                return
            }
            is ProxyResponseHeaders -> {
                val status = HttpStatusCode.fromValue(headerEvent.status)
                val headers = headerEvent.headers
                headers.forEach { (name, value) ->
                    call.response.headers.append(name, value, safeOnly = false)
                }
                call.response.status(status)
                respondStream(call, queue, headers["Content-Type"])
            }
            else -> {
                respondError(call, HttpStatusCode.InternalServerError, "未收到响应头")
            }
        }
    }

    private suspend fun respondStream(
        call: ApplicationCall,
        queue: MessageQueue,
        contentTypeValue: String?
    ) {
        val contentType = contentTypeValue?.let { ContentType.parse(it) }
        call.respondBytesWriter(contentType = contentType) {
            while (true) {
                val event = try {
                    queue.dequeue()
                } catch (timeout: TimeoutCancellationException) {
                    if (contentTypeValue?.contains("text/event-stream", ignoreCase = true) == true) {
                        write(": keepalive\n\n".toByteArray())
                        flush()
                        continue
                    } else {
                        break
                    }
                } catch (closed: IllegalStateException) {
                    break
                }

                when (event) {
                    is ProxyChunk -> {
                        val data = event.data ?: ""
                        write(data.toByteArray())
                        flush()
                    }
                    is ProxyStreamClose -> break
                    is ProxyError -> {
                        logger.warn("流式传输中收到错误: ${event.message}")
                        break
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun respondError(
        call: ApplicationCall,
        status: HttpStatusCode,
        message: String
    ) {
        call.respondBytesWriter(status = status) {
            write(message.toByteArray())
        }
    }
}

private suspend fun ByteReadChannel.toByteArray(): ByteArray {
    val bytes = mutableListOf<Byte>()
    while (!isClosedForRead) {
        val packet = readRemaining(DEFAULT_BUFFER_SIZE.toLong())
        while (!packet.isEmpty) {
            bytes.add(packet.readByte())
        }
    }
    return bytes.toByteArray()
}
