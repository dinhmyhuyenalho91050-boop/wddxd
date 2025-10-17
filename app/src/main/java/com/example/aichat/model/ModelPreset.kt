package com.example.aichat.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelPreset(
    val id: Int,
    val enabled: Boolean = true,
    val displayName: String,
    val type: ModelType,
    val config: ModelConfig
)

val DefaultModelPresets = listOf(
    ModelPreset(
        id = 1,
        enabled = true,
        displayName = "GPT-4o mini",
        type = ModelType.OPEN_AI,
        config = OpenAIConfig()
    ),
    ModelPreset(
        id = 2,
        enabled = false,
        displayName = "Gemini Flash",
        type = ModelType.GEMINI_DIRECT,
        config = GeminiConfig()
    )
)
