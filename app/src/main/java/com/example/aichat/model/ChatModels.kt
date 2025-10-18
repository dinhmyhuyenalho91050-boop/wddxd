@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.aichat.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

private fun currentInstant(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

@Serializable
data class ChatSession(
    val id: Long,
    val name: String,
    val createdAt: Instant = currentInstant(),
    val updatedAt: Instant = currentInstant(),
    val presetId: Int = 1,
    val promptPresetId: Int = 1
)

@Serializable
data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val role: MessageRole,
    val content: String,
    val thinking: String = "",
    val createdAt: Instant = currentInstant(),
    val isStreaming: Boolean = false
)

@Serializable
enum class MessageRole { USER, ASSISTANT }

data class SessionWithMessages(
    val session: ChatSession,
    val messages: List<ChatMessage>
)
