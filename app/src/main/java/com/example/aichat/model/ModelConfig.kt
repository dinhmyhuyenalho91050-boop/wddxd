package com.example.aichat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ModelConfig {
    abstract val model: String
    abstract val stream: Boolean
    abstract val temperature: Double?
    abstract val topP: Double?
    abstract val maxTokens: Int?
}

@Serializable
@SerialName("openai")
data class OpenAIConfig(
    val base: String = "https://api.openai.com/v1",
    val key: String = "",
    override val model: String = "gpt-4o-mini",
    override val stream: Boolean = true,
    override val temperature: Double? = 0.7,
    override val topP: Double? = null,
    override val maxTokens: Int? = null,
    val useThinking: Boolean = false,
    val thinkingEffort: String? = null
) : ModelConfig()

@Serializable
@SerialName("gemini")
data class GeminiConfig(
    val base: String = "https://generativelanguage.googleapis.com",
    val key: String = "",
    override val model: String = "gemini-2.5-flash",
    override val stream: Boolean = true,
    override val temperature: Double? = 0.7,
    override val topP: Double? = null,
    val topK: Int? = null,
    override val maxTokens: Int? = null,
    val useThinking: Boolean = false,
    val thinkingBudget: Int? = null
) : ModelConfig()

@Serializable
@SerialName("gemini-proxy")
data class GeminiProxyConfig(
    val proxyUrl: String = "http://127.0.0.1:8889",
    val proxyPass: String = "",
    override val model: String = "gemini-2.5-flash",
    override val stream: Boolean = true,
    override val temperature: Double? = 0.7,
    override val topP: Double? = null,
    val topK: Int? = null,
    override val maxTokens: Int? = null,
    val useThinking: Boolean = false,
    val thinkingBudget: Int? = null
) : ModelConfig()
