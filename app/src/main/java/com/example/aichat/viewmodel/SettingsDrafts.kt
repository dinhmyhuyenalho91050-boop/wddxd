package com.example.aichat.viewmodel

import com.example.aichat.model.GeminiConfig
import com.example.aichat.model.GeminiProxyConfig
import com.example.aichat.model.ModelConfig
import com.example.aichat.model.ModelPreset
import com.example.aichat.model.ModelType
import com.example.aichat.model.OpenAIConfig
import com.example.aichat.model.PromptPreset
import com.example.aichat.model.RegexRule

data class ModelPresetDraft(
    val id: Int,
    val enabled: Boolean,
    val displayName: String,
    val type: ModelType,
    val config: ModelConfig
) {
    fun toPreset(): ModelPreset = ModelPreset(
        id = id,
        enabled = enabled,
        displayName = displayName.ifBlank { defaultDisplayName() },
        type = type,
        config = config
    )

    private fun defaultDisplayName(): String = when (type) {
        ModelType.OPEN_AI -> "OpenAI"
        ModelType.GEMINI_DIRECT -> "Gemini"
        ModelType.GEMINI_PROXY -> "Gemini代理"
    }
}

data class PromptPresetDraft(
    val id: Int,
    val name: String,
    val systemPrompt: String,
    val firstUser: String,
    val firstAssistant: String,
    val messagePrefix: String,
    val assistantPrefill: String,
    val regexRules: List<RegexRuleDraft>
) {
    fun toPreset(): PromptPreset = PromptPreset(
        id = id,
        name = name.ifBlank { "预设$id" },
        systemPrompt = systemPrompt,
        firstUser = firstUser,
        firstAssistant = firstAssistant,
        messagePrefix = messagePrefix,
        assistantPrefill = assistantPrefill,
        regexRules = regexRules.map { it.toRule() }
    )
}

data class RegexRuleDraft(
    val id: Int,
    val pattern: String,
    val replacement: String,
    val flags: String
) {
    fun toRule(): RegexRule = RegexRule(pattern = pattern, replacement = replacement, flags = flags)
}

fun ModelPreset.toDraft(): ModelPresetDraft = ModelPresetDraft(id, enabled, displayName, type, config)

fun PromptPreset.toDraft(): PromptPresetDraft = PromptPresetDraft(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    firstUser = firstUser,
    firstAssistant = firstAssistant,
    messagePrefix = messagePrefix,
    assistantPrefill = assistantPrefill,
    regexRules = regexRules.mapIndexed { index, rule ->
        RegexRuleDraft(index, rule.pattern, rule.replacement, rule.flags)
    }
)

fun createDefaultDrafts(): SettingsDraft = SettingsDraft(
    modelPresets = emptyList(),
    promptPresets = emptyList(),
    selectedPromptId = null
)

data class SettingsDraft(
    val modelPresets: List<ModelPresetDraft>,
    val promptPresets: List<PromptPresetDraft>,
    val selectedPromptId: Int?
)

fun SettingsDraft.withModelPresets(presets: List<ModelPreset>): SettingsDraft = copy(
    modelPresets = presets.map { it.toDraft() }
)

fun SettingsDraft.withPromptPresets(prompts: List<PromptPreset>): SettingsDraft = copy(
    promptPresets = prompts.map { it.toDraft() },
    selectedPromptId = prompts.firstOrNull()?.id
)

fun ModelPresetDraft.changeType(newType: ModelType): ModelPresetDraft {
    val newConfig: ModelConfig = when (newType) {
        ModelType.OPEN_AI -> OpenAIConfig()
        ModelType.GEMINI_DIRECT -> GeminiConfig()
        ModelType.GEMINI_PROXY -> GeminiProxyConfig()
    }
    return copy(type = newType, config = newConfig)
}
