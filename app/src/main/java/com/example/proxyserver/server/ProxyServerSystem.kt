package com.example.proxyserver.server

import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.net.InetSocketAddress

data class ServerConfig(
    val httpPort: Int = 8889,
    val wsPort: Int = 9998,
    val host: String = "127.0.0.1"
)

class ProxyServerSystem(
    private val config: ServerConfig,
    private val coroutineScope: CoroutineScope,
    private val logger: LoggingService = LoggingService("ProxyServer")
) {
    private val connectionRegistry = ConnectionRegistry(logger)
    private val requestHandler = RequestHandler(connectionRegistry, logger, coroutineScope)
    private val httpServer = ProxyHttpServer(config.httpPort, requestHandler, logger)
    private val wsServer = ProxyWebSocketServer(InetSocketAddress(config.host, config.wsPort), connectionRegistry, logger)

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        try {
            httpServer.start(SOCKET_READ_TIMEOUT, false)
            wsServer.start()
            running = true
            logger.info("代理服务器系统启动完成 HTTP ${config.httpPort} WS ${config.wsPort}")
        } catch (exception: IOException) {
            logger.error("启动失败: ${exception.message}", exception)
            throw exception
        }
    }

    fun stop() {
        if (!running) return
        httpServer.stop()
        try {
            wsServer.stop(1000)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("停止WebSocket服务器被中断", interrupted)
        }
        running = false
        logger.info("代理服务器系统已停止")
    }

    fun isRunning(): Boolean = running

    fun statusDescription(): String = "HTTP ${config.httpPort} / WS ${config.wsPort}"

    companion object {
        private const val SOCKET_READ_TIMEOUT = 10_000
    }
}
