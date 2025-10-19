package com.example.proxyserver.network

import com.example.proxyserver.model.ProxyEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout

class MessageQueue(private val timeoutMs: Long = 600_000L) {
    private val channel = Channel<ProxyEvent>(Channel.UNLIMITED)

    suspend fun enqueue(message: ProxyEvent) {
        channel.send(message)
    }

    suspend fun dequeue(): ProxyEvent {
        return try {
            withTimeout(timeoutMs) {
                channel.receive()
            }
        } catch (closed: ClosedReceiveChannelException) {
            throw IllegalStateException("Queue closed", closed)
        }
    }

    fun close() {
        channel.close()
    }
}
