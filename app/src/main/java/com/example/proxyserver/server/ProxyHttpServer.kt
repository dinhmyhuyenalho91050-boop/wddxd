package com.example.proxyserver.server

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

class ProxyHttpServer(
    port: Int,
    private val requestHandler: RequestHandler,
    private val logger: LoggingService
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            runBlocking { requestHandler.processRequest(session) }
        } catch (exception: Exception) {
            logger.error("请求处理失败: ${exception.message}", exception)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "服务器内部错误")
        }
    }
}
