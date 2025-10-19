package com.example.proxyserver

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed class ProxyMessage {
    data class ResponseHeaders(
        val status: Int,
        val headers: Map<String, String>
    ) : ProxyMessage()

    data class Chunk(val data: String) : ProxyMessage()

    data class Error(val status: Int?, val message: String) : ProxyMessage()

    data object StreamEnd : ProxyMessage()

    data object QueueClosed : ProxyMessage()
}

class QueueTimeoutException : Exception("Queue timeout")
class QueueClosedException : Exception("Queue closed")

class MessageQueue(private val timeoutMs: Long = 600_000L) {

    private val queue = LinkedBlockingQueue<ProxyMessage>()
    private val closed = AtomicBoolean(false)

    fun enqueue(message: ProxyMessage) {
        if (closed.get()) return
        queue.offer(message)
    }

    @Throws(QueueTimeoutException::class, QueueClosedException::class)
    fun dequeue(timeout: Long = timeoutMs): ProxyMessage {
        if (closed.get() && queue.isEmpty()) {
            throw QueueClosedException()
        }

        val message = queue.poll(timeout, TimeUnit.MILLISECONDS)
            ?: throw QueueTimeoutException()

        if (message == ProxyMessage.QueueClosed) {
            throw QueueClosedException()
        }

        return message
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            queue.clear()
            queue.offer(ProxyMessage.QueueClosed)
        }
    }
}
