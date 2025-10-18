@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.aichat.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class ChatSession(
    val id: Long,
    val name: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
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
    val createdAt: Instant = Clock.System.now(),
    val isStreaming: Boolean = false
)

@Serializable
enum class MessageRole { USER, ASSISTANT }

data class SessionWithMessages(
    val session: ChatSession,
    val messages: List<ChatMessage>
)
