package com.example.proxyserver.server

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

private const val DEFAULT_TIMEOUT = 600_000L

sealed interface QueueResult {
    data class Message(val payload: ProxyResponseMessage) : QueueResult
    data object StreamEnd : QueueResult
    data object Timeout : QueueResult
}

class MessageQueue(private val timeoutMillis: Long = DEFAULT_TIMEOUT) {
    private val channel = Channel<ProxyResponseMessage>(Channel.BUFFERED)
    private var closed = false

    suspend fun enqueue(message: ProxyResponseMessage) {
        if (closed) return
        channel.send(message)
    }

    suspend fun dequeue(): QueueResult {
        if (closed) return QueueResult.StreamEnd
        val message = withTimeoutOrNull(timeoutMillis) { channel.receive() }
        return when {
            message == null -> QueueResult.Timeout
            message.type == ProxyResponseMessage.Type.StreamEnd -> QueueResult.StreamEnd
            else -> QueueResult.Message(message)
        }
    }

    fun close() {
        closed = true
        channel.close()
    }
}
