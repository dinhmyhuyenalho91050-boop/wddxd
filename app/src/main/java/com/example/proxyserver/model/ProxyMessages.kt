package com.example.proxyserver.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ProxyEvent {
    val requestId: String
}

@Serializable
@SerialName("response_headers")
data class ProxyResponseHeaders(
    @SerialName("request_id") override val requestId: String,
    @SerialName("status") val status: Int = 200,
    @SerialName("headers") val headers: Map<String, String> = emptyMap()
) : ProxyEvent

@Serializable
@SerialName("chunk")
data class ProxyChunk(
    @SerialName("request_id") override val requestId: String,
    @SerialName("data") val data: String? = null
) : ProxyEvent

@Serializable
@SerialName("error")
data class ProxyError(
    @SerialName("request_id") override val requestId: String,
    @SerialName("status") val status: Int = 500,
    @SerialName("message") val message: String = ""
) : ProxyEvent

@Serializable
@SerialName("stream_close")
data class ProxyStreamClose(
    @SerialName("request_id") override val requestId: String
) : ProxyEvent

@Serializable
sealed interface ProxyMessage

@Serializable
@SerialName("proxy_request")
data class ProxyRequest(
    @SerialName("path") val path: String,
    @SerialName("method") val method: String,
    @SerialName("headers") val headers: Map<String, String>,
    @SerialName("query_params") val queryParams: Map<String, List<String>>,
    @SerialName("body") val body: String,
    @SerialName("request_id") val requestId: String
) : ProxyMessage

@Serializable
@SerialName("keepalive")
data class KeepAlive(
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis()
) : ProxyMessage
