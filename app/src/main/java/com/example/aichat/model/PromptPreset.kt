package com.example.aichat.model

import kotlinx.serialization.Serializable

@Serializable
data class PromptPreset(
    val id: Int,
    val name: String,
    val systemPrompt: String = "",
    val firstUser: String = "",
    val firstAssistant: String = "",
    val messagePrefix: String = "",
    val assistantPrefill: String = "",
    val regexRules: List<RegexRule> = emptyList()
)

val DefaultPromptPreset = PromptPreset(
    id = 1,
    name = "默认预设"
)
