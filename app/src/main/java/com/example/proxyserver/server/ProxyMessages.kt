package com.example.proxyserver.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProxyRequest(
    val path: String,
    val method: String,
    val headers: Map<String, String>,
    @SerialName("query_params") val queryParams: Map<String, List<String>>,
    val body: String,
    @SerialName("request_id") val requestId: String
)

@Serializable
data class ProxyResponseMessage(
    @SerialName("event_type") val eventType: String,
    val status: Int? = null,
    val headers: Map<String, String>? = null,
    val data: String? = null,
    val message: String? = null,
    @SerialName("request_id") val requestId: String? = null
) {
    enum class Type { ResponseHeaders, Chunk, Error, StreamEnd }

    val type: Type
        get() = when (eventType) {
            "response_headers" -> Type.ResponseHeaders
            "chunk" -> Type.Chunk
            "error" -> Type.Error
            "stream_close" -> Type.StreamEnd
            else -> Type.Chunk
        }
}
