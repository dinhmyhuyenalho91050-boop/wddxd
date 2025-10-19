package com.example.proxyserver.server

data class ProxyRequest(
    val path: String,
    val method: String,
    val headers: Map<String, String>,
    val queryParams: Map<String, String>,
    val body: String,
    val requestId: String
)
