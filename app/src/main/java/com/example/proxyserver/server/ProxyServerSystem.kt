package com.example.proxyserver.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.intercept
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.pingPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class ProxyServerSystem(
    private val config: ServerConfig = ServerConfig()
) {
    private val logger = LoggingService("ProxyServer")
    private val connectionRegistry = ConnectionRegistry(logger)
    private val requestHandler = RequestHandler(connectionRegistry, logger)

    private var httpEngine: io.ktor.server.engine.ApplicationEngine? = null
    private var wsEngine: io.ktor.server.engine.ApplicationEngine? = null

    suspend fun start() {
        if (httpEngine != null || wsEngine != null) return
        httpEngine = createHttpServer().also { engine ->
            withContext(Dispatchers.IO) { engine.start(wait = false) }
        }
        wsEngine = createWebSocketServer().also { engine ->
            withContext(Dispatchers.IO) { engine.start(wait = false) }
        }
        logger.info("代理服务器系统启动完成")
    }

    suspend fun stop() {
        withContext(Dispatchers.IO) {
            httpEngine?.stop(1000, 2000)
            wsEngine?.stop(1000, 2000)
        }
        httpEngine = null
        wsEngine = null
        logger.info("代理服务器系统已停止")
    }

    fun isRunning(): Boolean = httpEngine != null && wsEngine != null

    fun serverConfig(): ServerConfig = config

    private fun createHttpServer(): io.ktor.server.engine.ApplicationEngine {
        return io.ktor.server.engine.embeddedServer(CIO, port = config.httpPort, host = config.host) {
            install(CallLogging)

            intercept(io.ktor.server.application.ApplicationCallPipeline.Setup) {
                addCorsHeaders()
                if (call.request.httpMethod == HttpMethod.Options) {
                    call.respond(HttpStatusCode.OK)
                    finish()
                    return@intercept
                }
            }

            routing {
                route("/{...}") {
                    handle {
                        requestHandler.process(call)
                    }
                }
            }
        }
    }

    private fun createWebSocketServer(): io.ktor.server.engine.ApplicationEngine {
        return io.ktor.server.engine.embeddedServer(CIO, port = config.wsPort, host = config.host) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(30)
            }

            routing {
                webSocket("/bridge") {
                    connectionRegistry.register(this)
                }
            }
        }
    }

    private suspend fun PipelineContext<Unit, io.ktor.server.application.ApplicationCall>.addCorsHeaders() {
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.headers.append(HttpHeaders.AccessControlAllowMethods, "GET, POST, PUT, DELETE, OPTIONS, PATCH")
        call.response.headers.append(
            HttpHeaders.AccessControlAllowHeaders,
            "Content-Type, Authorization, X-Requested-With, Accept, Origin"
        )
        call.response.headers.append(HttpHeaders.AccessControlAllowCredentials, "true")
        call.response.headers.append("Access-Control-Max-Age", "86400")
    }

    data class ServerConfig(
        val httpPort: Int = 8889,
        val wsPort: Int = 9998,
        val host: String = "127.0.0.1"
    )
}
