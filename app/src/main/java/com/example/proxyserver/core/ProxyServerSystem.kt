package com.example.proxyserver.core

import com.example.proxyserver.model.KeepAlive
import com.example.proxyserver.network.ConnectionRegistry
import com.example.proxyserver.network.RequestHandler
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.handle
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class ProxyServerSystem(
    private val config: ServerConfig = ServerConfig()
) {
    private val logger = LoggingService("ProxyServer")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val connectionRegistry = ConnectionRegistry(logger)
    private val requestHandler = RequestHandler(connectionRegistry, logger, json)

    private var httpServer: ApplicationEngine? = null
    private var wsServer: ApplicationEngine? = null
    private var keepAliveJob: Job? = null

    suspend fun start() {
        if (httpServer != null || wsServer != null) return
        httpServer = embeddedServer(CIO, port = config.httpPort, host = config.host) {
            configureHttp()
        }.also { engine ->
            engine.start(false)
            logger.info("HTTP服务器启动: http://${config.host}:${config.httpPort}")
        }

        wsServer = embeddedServer(CIO, port = config.wsPort, host = config.host) {
            configureWebsocket()
        }.also { engine ->
            engine.start(false)
            logger.info("WebSocket服务器启动: ws://${config.host}:${config.wsPort}")
        }

        keepAliveJob = scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (connectionRegistry.hasActiveConnections()) {
                    sendKeepAlive()
                }
            }
        }
    }

    suspend fun stop() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        httpServer?.stop(1000, 2000)
        wsServer?.stop(1000, 2000)
        httpServer = null
        wsServer = null
    }

    private fun Application.configureHttp() {
        install(ContentNegotiation) { json() }
        install(CallLogging)
        install(CORS) {
            anyHost()
            allowNonSimpleContentTypes = true
            allowHeader("*")
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
            allowMethod(io.ktor.http.HttpMethod.Put)
            allowMethod(io.ktor.http.HttpMethod.Delete)
            allowMethod(io.ktor.http.HttpMethod.Patch)
            allowMethod(io.ktor.http.HttpMethod.Options)
            exposeHeader("*")
        }

        routing {
            route("/{...}") {
                handle {
                    requestHandler.process(call)
                }
            }
        }
    }

    private fun Application.configureWebsocket() {
        install(WebSockets)
        routing {
            webSocket("/proxy") {
                connectionRegistry.addConnection(this, call)
            }
        }
    }

    private suspend fun sendKeepAlive() {
        val payload = json.encodeToString(KeepAlive())
        connectionRegistry.getFirstConnection()?.sendText(payload)
    }

    data class ServerConfig(
        val httpPort: Int = 8889,
        val wsPort: Int = 9998,
        val host: String = "127.0.0.1"
    )
}
