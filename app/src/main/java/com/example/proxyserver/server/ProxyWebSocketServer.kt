package com.example.proxyserver.server

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class ProxyWebSocketServer(
    private val bindAddress: InetSocketAddress,
    private val connectionRegistry: ConnectionRegistry,
    private val logger: LoggingService
) : WebSocketServer(bindAddress) {

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connectionRegistry.addConnection(conn)
        logger.info("WebSocket连接建立: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connectionRegistry.removeConnection(conn)
        logger.info("WebSocket关闭: ${conn.remoteSocketAddress} code=$code reason=$reason")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        connectionRegistry.handleIncomingMessage(message)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        logger.error("WebSocket错误: ${ex.message}", ex)
    }

    override fun onStart() {
        logger.info("WebSocket服务器启动: ${bindAddress.hostString}:${bindAddress.port}")
    }
}
