package com.example.aichat.network

import com.example.aichat.model.MessageRole
import com.example.aichat.model.ModelPreset
import com.example.aichat.model.ModelType
import com.example.aichat.model.PromptPreset
import com.example.aichat.model.RegexRule
import com.example.aichat.model.apply
import com.example.aichat.model.SessionWithMessages
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {

    sealed interface StreamEvent {
        data class Delta(val content: String, val thinking: String) : StreamEvent
        data class Completed(val content: String, val thinking: String) : StreamEvent
    }

    suspend fun streamCompletion(
        session: SessionWithMessages,
        preset: ModelPreset,
        promptPreset: PromptPreset
    ): Flow<StreamEvent> = callbackFlow {
        var call: okhttp3.Call? = null
        val job = launch {
            try {
                val request = when (preset.type) {
                    ModelType.OPEN_AI -> buildOpenAiRequest(session, preset, promptPreset)
                    ModelType.GEMINI_DIRECT -> buildGeminiRequest(session, preset, promptPreset, false)
                    ModelType.GEMINI_PROXY -> buildGeminiRequest(session, preset, promptPreset, true)
                }

                call = client.newCall(request)
                val response = call!!.execute()
                if (!response.isSuccessful) {
                    close(IOException("HTTP ${response.code}"))
                    return@launch
                }

                val stream = preset.config.stream
                if (stream) {
                    handleStream(response, preset.type, promptPreset.regexRules, this@callbackFlow)
                } else {
                    handleSingle(response, preset.type, promptPreset.regexRules)?.let { result ->
                        trySend(StreamEvent.Completed(result.first, result.second))
                    }
                }
                response.close()
                close()
            } catch (t: Throwable) {
                close(t)
            }
        }

        awaitClose {
            call?.cancel()
            job.cancel()
        }
    }

    private fun buildOpenAiRequest(
        session: SessionWithMessages,
        preset: ModelPreset,
        promptPreset: PromptPreset
    ): Request {
        val config = preset.config as com.example.aichat.model.OpenAIConfig
        val base = config.base.trimEnd('/')
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", config.stream)
            put("messages", buildOpenAiMessages(session, promptPreset))
            config.temperature?.let { put("temperature", it) }
            config.topP?.let { put("top_p", it) }
            config.maxTokens?.let { put("max_tokens", it) }
            if (config.useThinking) {
                config.thinkingEffort?.let { put("reasoning_effort", it) }
            }
        }

        val requestBody = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("$base/chat/completions")
            .post(requestBody)
            .header("Authorization", "Bearer ${config.key}")
            .build()
    }

    private fun buildOpenAiMessages(session: SessionWithMessages, prompt: PromptPreset): JsonArray {
        return buildJsonArray {
            if (prompt.systemPrompt.isNotBlank()) {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", prompt.systemPrompt)
                })
            }
            if (prompt.firstUser.isNotBlank()) {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt.firstUser)
                })
            }
            if (prompt.firstAssistant.isNotBlank()) {
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", prompt.firstAssistant)
                })
            }
            session.messages.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> add(buildJsonObject {
                        put("role", "user")
                        val content = if (prompt.messagePrefix.isNotBlank() && message == session.messages.last()) {
                            prompt.messagePrefix + message.content
                        } else message.content
                        put("content", content)
                    })
                    MessageRole.ASSISTANT -> add(buildJsonObject {
                        put("role", "assistant")
                        put("content", message.content)
                    })
                }
            }
            if (prompt.assistantPrefill.isNotBlank()) {
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", prompt.assistantPrefill)
                })
            }
        }
    }

    private fun buildGeminiRequest(
        session: SessionWithMessages,
        preset: ModelPreset,
        promptPreset: PromptPreset,
        isProxy: Boolean
    ): Request {
        val cfg = when (val c = preset.config) {
            is com.example.aichat.model.GeminiConfig -> c
            is com.example.aichat.model.GeminiProxyConfig -> c
            else -> error("Invalid Gemini config")
        }
        val base = when (cfg) {
            is com.example.aichat.model.GeminiConfig -> cfg.base.trimEnd('/')
            is com.example.aichat.model.GeminiProxyConfig -> cfg.proxyUrl.trimEnd('/')
            else -> error("Invalid Gemini config")
        }
        val model = cfg.model
        val action = if (cfg.stream) "streamGenerateContent" else "generateContent"
        val urlBuilder = StringBuilder("$base/v1beta/models/${model.encodeURLComponent()}:$action")
        val query = mutableListOf<String>()
        if (cfg.stream) query += "alt=sse"
        if (!isProxy) {
            (cfg as? com.example.aichat.model.GeminiConfig)?.key?.takeIf { it.isNotBlank() }
                ?.let { key -> query += "key=${key.encodeURLComponent()}" }
        }
        if (query.isNotEmpty()) {
            urlBuilder.append('?').append(query.joinToString("&"))
        }

        val contents = buildJsonArray {
            val prompt = promptPreset
            if (prompt.firstUser.isNotBlank()) {
                add(geminiUser(prompt.firstUser))
            }
            if (prompt.firstAssistant.isNotBlank()) {
                add(geminiModel(prompt.firstAssistant))
            }
            session.messages.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> {
                        val content = if (prompt.messagePrefix.isNotBlank() && message == session.messages.last()) {
                            prompt.messagePrefix + message.content
                        } else message.content
                        add(geminiUser(content))
                    }
                    MessageRole.ASSISTANT -> add(geminiModel(message.content))
                }
            }
            if (prompt.assistantPrefill.isNotBlank()) {
                add(geminiModel(prompt.assistantPrefill))
            }
        }

        val useThinking = when (cfg) {
            is com.example.aichat.model.GeminiConfig -> cfg.useThinking
            is com.example.aichat.model.GeminiProxyConfig -> cfg.useThinking
            else -> false
        }
        val topK = when (cfg) {
            is com.example.aichat.model.GeminiConfig -> cfg.topK
            is com.example.aichat.model.GeminiProxyConfig -> cfg.topK
            else -> null
        }
        val thinkingBudget = when (cfg) {
            is com.example.aichat.model.GeminiConfig -> cfg.thinkingBudget
            is com.example.aichat.model.GeminiProxyConfig -> cfg.thinkingBudget
            else -> null
        }
        val generationConfig = buildJsonObject {
            cfg.temperature?.let { put("temperature", it) }
            cfg.topP?.let { put("topP", it) }
            topK?.let { put("topK", it) }
            cfg.maxTokens?.let { put("maxOutputTokens", it) }
            if (useThinking) {
                val budget = thinkingBudget ?: -1
                putJsonObject("thinkingConfig") {
                    put("thinkingBudget", budget)
                    put("includeThoughts", true)
                }
            }
        }

        val body = buildJsonObject {
            putJsonArray("contents") {
                contents.forEach { add(it) }
            }
            if (promptPreset.systemPrompt.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", promptPreset.systemPrompt) })
                    }
                }
            }
            putJsonArray("safetySettings") {
                listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT"
                ).forEach { category ->
                    add(buildJsonObject {
                        put("category", category)
                        put("threshold", "OFF")
                    })
                }
            }
            put("generationConfig", generationConfig)
        }

        val requestBuilder = Request.Builder().url(urlBuilder.toString())
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))

        if (isProxy) {
            (cfg as? com.example.aichat.model.GeminiProxyConfig)?.proxyPass?.takeIf { it.isNotBlank() }
                ?.let { requestBuilder.header("Authorization", "Bearer $it") }
        } else {
            (cfg as? com.example.aichat.model.GeminiConfig)?.key?.takeIf { it.isNotBlank() }
                ?.let { requestBuilder.header("X-Goog-Api-Key", it) }
        }
        if (cfg.stream) {
            requestBuilder.header("Accept", "text/event-stream")
        }
        return requestBuilder.build()
    }

    private fun geminiUser(text: String): JsonObject = buildJsonObject {
        put("role", "user")
        putJsonArray("parts") { add(buildJsonObject { put("text", text) }) }
    }

    private fun geminiModel(text: String): JsonObject = buildJsonObject {
        put("role", "model")
        putJsonArray("parts") { add(buildJsonObject { put("text", text) }) }
    }

    private fun handleStream(
        response: Response,
        type: ModelType,
        regexRules: List<RegexRule>,
        emitter: ProducerScope<StreamEvent>
    ) {
        val source = response.body?.source() ?: return
        var content = ""
        var thinking = ""
        when (type) {
            ModelType.OPEN_AI -> parseOpenAiStream(source) { delta, thought ->
                content += delta
                if (thought.isNotEmpty()) thinking += thought
                emitter.trySend(StreamEvent.Delta(content, thinking))
            }
            ModelType.GEMINI_DIRECT, ModelType.GEMINI_PROXY -> parseGeminiStream(source) { delta, thought ->
                if (delta != null) {
                    content += delta
                }
                if (thought != null) thinking += thought
                emitter.trySend(StreamEvent.Delta(content, thinking))
            }
        }
        val processed = applyRegex(content, regexRules)
        emitter.trySend(StreamEvent.Completed(processed, thinking))
    }

    private fun handleSingle(
        response: Response,
        type: ModelType,
        regexRules: List<RegexRule>
    ): Pair<String, String>? {
        val body = response.body?.string() ?: return null
        return when (type) {
            ModelType.OPEN_AI -> parseOpenAiSingle(body)
            ModelType.GEMINI_DIRECT, ModelType.GEMINI_PROXY -> parseGeminiSingle(body)
        }?.let { (content, thinking) ->
            applyRegex(content, regexRules) to thinking
        }
    }

    private fun parseOpenAiSingle(body: String): Pair<String, String>? {
        val root = json.parseToJsonElement(body).jsonObject
        val choices = root["choices"] as? JsonArray ?: return null
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject ?: return null
        val content = message["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val thinking = message["reasoning_content"]?.jsonPrimitive?.contentOrNull ?: ""
        return content to thinking
    }

    private fun parseGeminiSingle(body: String): Pair<String, String>? {
        val root = json.parseToJsonElement(body).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: return null
        val candidate = candidates.firstOrNull()?.jsonObject ?: return null
        val parts = candidate["content"]?.jsonObject?.get("parts") as? JsonArray ?: return null
        val content = parts.joinToString(separator = "") { part ->
            part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        val thinking = candidate["thoughts"]?.jsonArray?.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }.orEmpty()
        return content to thinking
    }

    private fun parseOpenAiStream(source: BufferedSource, onDelta: (String, String) -> Unit) {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val element = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
            val choice = element["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
            val delta = choice["delta"]?.jsonObject
            val content = delta?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
            val thinking = delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull ?: ""
            if (content.isNotEmpty() || thinking.isNotEmpty()) {
                onDelta(content, thinking)
            }
        }
    }

    private fun parseGeminiStream(source: BufferedSource, onDelta: (String?, String?) -> Unit) {
        var buffer = ""
        while (!source.exhausted()) {
            val chunk = source.readUtf8Line() ?: continue
            if (chunk.isBlank()) continue
            if (!chunk.startsWith("data:")) continue
            val payload = chunk.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val element = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
            val candidates = element["candidates"]?.jsonArray ?: continue
            candidates.forEach { candidateElement ->
                val candidate = candidateElement.jsonObject
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
                val delta = parts?.joinToString("") { part -> part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                val thoughts = candidate["thoughts"]?.jsonArray?.joinToString("\n") { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }
                if (!delta.isNullOrEmpty() || !thoughts.isNullOrEmpty()) {
                    onDelta(delta, thoughts)
                }
            }
        }
    }

    private fun applyRegex(content: String, rules: List<RegexRule>): String {
        return rules.fold(content) { acc, rule -> rule.apply(acc) }
    }

    private fun String.encodeURLComponent(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}
