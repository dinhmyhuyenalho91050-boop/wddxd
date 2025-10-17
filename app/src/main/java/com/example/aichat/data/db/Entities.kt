package com.example.aichat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val presetId: Int,
    val promptPresetId: Int
)

@Entity(tableName = "messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val thinking: String,
    val createdAt: String,
    val isStreaming: Boolean
)

@Entity(tableName = "model_presets")
data class ModelPresetEntity(
    @PrimaryKey val id: Int,
    val data: String
)

@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey val id: Int,
    val data: String
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val key: String,
    val value: String
)
