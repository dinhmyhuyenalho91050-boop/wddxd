package com.example.aichat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ModelType {
    @SerialName("openai")
    OPEN_AI,

    @SerialName("gemini")
    GEMINI_DIRECT,

    @SerialName("gemini-proxy")
    GEMINI_PROXY
}
