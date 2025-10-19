package com.example.proxyserver.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.util.flattenEntries
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.text.Charsets

class RequestHandler(
    private val connectionRegistry: ConnectionRegistry,
    private val logger: LoggingService
) {
    suspend fun process(call: ApplicationCall) {
        val method = call.request.httpMethod.value
        val path = call.request.path()
        logger.info("处理请求: $method $path")

        if (!connectionRegistry.hasActiveConnections()) {
            call.respond(HttpStatusCode.ServiceUnavailable, "没有可用的浏览器连接")
            return
        }

        val requestId = generateRequestId()
        val proxyRequest = buildProxyRequest(call, requestId)
        val queue = connectionRegistry.createMessageQueue(requestId)

        try {
            connectionRegistry.forwardRequest(proxyRequest)
            handleResponse(call, queue)
        } catch (ex: Exception) {
            logger.error("请求处理错误: ${ex.message}", ex)
            call.respond(HttpStatusCode.InternalServerError, "代理错误: ${ex.message}")
        } finally {
            connectionRegistry.removeMessageQueue(requestId)
        }
    }

    private suspend fun handleResponse(call: ApplicationCall, queue: MessageQueue) {
        when (val result = queue.dequeue()) {
            QueueResult.StreamEnd -> call.respond(HttpStatusCode.BadGateway, "浏览器连接过早关闭")
            QueueResult.Timeout -> call.respond(HttpStatusCode.GatewayTimeout, "请求超时")
            is QueueResult.Message -> {
                val header = result.payload
                if (header.type == ProxyResponseMessage.Type.Error) {
                    val status = header.status ?: HttpStatusCode.InternalServerError.value
                    call.respond(HttpStatusCode.fromValue(status), header.message ?: "未知错误")
                    return
                }

                val status = header.status ?: HttpStatusCode.OK.value
                call.response.status(HttpStatusCode.fromValue(status))
                header.headers?.forEach { (name, value) ->
                    call.response.headers.append(name, value, false)
                }
                val contentType = header.headers?.get(HttpHeaders.ContentType)

                call.respondBytesWriter(contentType?.let { ContentType.parse(it) }) {
                    streamResponse(queue, this, contentType)
                }
            }
        }
    }

    private suspend fun streamResponse(
        queue: MessageQueue,
        channel: ByteWriteChannel,
        contentType: String?
    ) {
        while (true) {
            when (val result = queue.dequeue()) {
                QueueResult.StreamEnd -> return
                QueueResult.Timeout -> {
                    if (contentType?.contains("text/event-stream", ignoreCase = true) == true) {
                        channel.writeFully(": keepalive\n\n".toByteArray())
                        channel.flush()
                    } else {
                        return
                    }
                }
                is QueueResult.Message -> {
                    val payload = result.payload
                    if (payload.type == ProxyResponseMessage.Type.Error) {
                        return
                    }
                    val data = payload.data ?: continue
                    channel.writeFully(data.toByteArray(Charsets.UTF_8))
                    channel.flush()
                }
            }
        }
    }

    private suspend fun buildProxyRequest(call: ApplicationCall, requestId: String): ProxyRequest {
        val headers = call.request.headers.flattenEntries().associate { (key, value) -> key to value }
        val queryParams = call.request.queryParameters.entries().associate { (key, values) ->
            key to values
        }
        val body = withContext(Dispatchers.IO) {
            call.receiveChannel().readRemaining().readText(Charsets.UTF_8)
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

    private fun generateRequestId(): String = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
}
