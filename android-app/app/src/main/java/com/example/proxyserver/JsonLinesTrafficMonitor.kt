package com.example.proxyserver

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Persists proxy traffic as JSON lines so users can diff the Android bridge with the
 * reference Node implementation. Each event is written to a rolling log file under the
 * supplied directory. Files are rotated after reaching [maxFileSizeBytes] and the oldest
 * files are deleted once [maxFiles] is exceeded.
 */
class JsonLinesTrafficMonitor(
    outputDirectory: File,
    private val maxFileSizeBytes: Long = 2L * 1024 * 1024,
    private val maxFiles: Int = 5
) : ProxyTrafficMonitor {

    private val directory: File = outputDirectory.apply { mkdirs() }
    private val lock = Any()
    private val fileCounter = AtomicInteger(0)
    @Volatile
    private var currentFile: File = newLogFile()
    private val maxChunkRecordBytes = 64 * 1024

    override fun onHttpRequestForwarded(requestId: String, requestJson: JSONObject, rawBody: ByteArray) {
        runSilently {
            val payload = JSONObject().apply {
                put("request", JSONObject(requestJson.toString()))
                put("raw_body_length", rawBody.size)
                if (rawBody.isNotEmpty()) {
                    val (bytes, truncated) = clipBytes(rawBody)
                    put("raw_body_base64", bytes.toBase64())
                    if (truncated) put("raw_body_truncated", true)
                }
            }
            logEvent("http_forward", requestId, payload)
        }
    }

    override fun onIncomingWebSocketMessage(requestId: String?, rawMessage: String, parsed: JSONObject?) {
        runSilently {
            val payload = JSONObject().apply {
                put("raw", rawMessage)
                parsed?.let { put("parsed", JSONObject(it.toString())) }
            }
            logEvent("ws_inbound", requestId, payload)
        }
    }

    override fun onProxyMessageQueued(requestId: String, message: ProxyMessage) {
        runSilently {
            val payload = JSONObject()
            when (message) {
                is ProxyMessage.ResponseHeaders -> {
                    payload.put("status", message.status)
                    payload.put("headers", JSONObject().apply {
                        message.headers.forEach { (key, values) ->
                            put(key, JSONArray(values))
                        }
                    })
                }

                is ProxyMessage.Chunk -> {
                    payload.put("data_length", message.data.size)
                    val (bytes, truncated) = clipBytes(message.data, maxChunkRecordBytes)
                    payload.put("data_base64", bytes.toBase64())
                    if (truncated) payload.put("truncated", true)
                }

                is ProxyMessage.Error -> {
                    message.status?.let { payload.put("status", it) }
                    payload.put("message", message.message)
                }

                ProxyMessage.StreamEnd -> payload.put("event", "stream_end")

                ProxyMessage.QueueClosed -> payload.put("event", "queue_closed")
            }

            logEvent("proxy_message", requestId, payload)
        }
    }

    private fun logEvent(type: String, requestId: String?, payload: JSONObject) {
        val entry = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("event_type", type)
            requestId?.let { put("request_id", it) }
            put("payload", payload)
        }
        appendLine(entry.toString())
    }

    private fun appendLine(line: String) {
        synchronized(lock) {
            try {
                if (!currentFile.exists()) {
                    currentFile = newLogFile()
                }

                if (currentFile.length() + line.length > maxFileSizeBytes) {
                    rotateFile()
                }

                currentFile.appendText(line + "\n")
            } catch (ex: IOException) {
                // Swallow errors – monitoring must never break the proxy flow.
            }
        }
    }

    private fun rotateFile() {
        currentFile = newLogFile()
        trimOldFiles()
    }

    private fun newLogFile(): File {
        val index = fileCounter.incrementAndGet()
        val name = "traffic-${System.currentTimeMillis()}-$index.log"
        return File(directory, name)
    }

    private fun trimOldFiles() {
        val logFiles = directory.listFiles { file -> file.isFile && file.name.startsWith("traffic-") }
            ?.sortedBy { it.lastModified() }
            ?: return

        val excess = logFiles.size - maxFiles
        if (excess <= 0) return

        logFiles.take(excess).forEach { file ->
            try {
                file.delete()
            } catch (_: Exception) {
                // Ignore – best effort cleanup only.
            }
        }
    }

    private fun clipBytes(data: ByteArray, limit: Int = maxChunkRecordBytes): Pair<ByteArray, Boolean> {
        if (data.size <= limit) {
            return data to false
        }
        val clipped = ByteArray(limit)
        System.arraycopy(data, 0, clipped, 0, limit)
        return clipped to true
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private inline fun runSilently(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // Monitoring should never interfere with live traffic.
        }
    }
}

