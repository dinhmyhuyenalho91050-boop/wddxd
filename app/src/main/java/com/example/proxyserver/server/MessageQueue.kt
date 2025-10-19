package com.example.proxyserver.server

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull

class QueueTimeoutException : Exception("Queue timeout")
class QueueClosedException : Exception("Queue closed")

class MessageQueue(private val defaultTimeoutMs: Long = 600_000L) {
    private val channel = Channel<QueueMessage>(Channel.BUFFERED)

    suspend fun dequeue(timeoutMs: Long = defaultTimeoutMs): QueueMessage {
        return try {
            withTimeoutOrNull(timeoutMs) { channel.receive() } ?: throw QueueTimeoutException()
        } catch (closed: ClosedReceiveChannelException) {
            throw QueueClosedException()
        }
    }

    fun enqueue(message: QueueMessage) {
        if (!channel.isClosedForSend) {
            channel.trySend(message)
        }
    }

    fun close() {
        channel.close()
    }

    suspend fun drainRemaining(): List<QueueMessage> {
        val messages = mutableListOf<QueueMessage>()
        try {
            while (true) {
                messages.add(channel.receive())
            }
        } catch (_: ClosedReceiveChannelException) {
            // ignore
        }
        return messages
    }
}
