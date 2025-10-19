package com.example.proxyserver.server

sealed class QueueMessage {
    data class Payload(
        val eventType: String?,
        val status: Int?,
        val headers: Map<String, String>?,
        val data: String?,
        val message: String?
    ) : QueueMessage()

    object StreamEnd : QueueMessage()
}
