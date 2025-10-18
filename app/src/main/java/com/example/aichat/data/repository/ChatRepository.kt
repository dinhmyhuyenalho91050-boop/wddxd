@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.example.aichat.data.repository

import android.content.Context
import com.example.aichat.data.db.AppDatabase
import com.example.aichat.data.db.ChatDao
import com.example.aichat.data.db.ChatMessageEntity
import com.example.aichat.data.db.ChatSessionEntity
import com.example.aichat.data.db.ModelPresetEntity
import com.example.aichat.data.db.PromptPresetEntity
import com.example.aichat.model.ChatMessage
import com.example.aichat.model.ChatSession
import com.example.aichat.model.DefaultModelPresets
import com.example.aichat.model.DefaultPromptPreset
import com.example.aichat.model.MessageRole
import com.example.aichat.model.ModelPreset
import com.example.aichat.model.ModelType
import com.example.aichat.model.PromptPreset
import com.example.aichat.model.SessionWithMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

class ChatRepository private constructor(
    private val dao: ChatDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
) {

    val sessions: Flow<List<ChatSession>> = dao.observeSessions().map { list ->
        list.map { it.toDomain() }
    }

    fun messages(sessionId: Long): Flow<List<ChatMessage>> =
        dao.observeMessages(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun messagesOnce(sessionId: Long): List<ChatMessage> = withContext(Dispatchers.IO) {
        dao.getMessages(sessionId).map { it.toDomain() }
    }

    suspend fun message(id: Long): ChatMessage? = withContext(Dispatchers.IO) {
        dao.getMessageById(id)?.toDomain()
    }

    val modelPresets: Flow<List<ModelPreset>> = dao.observeModelPresets().map { list ->
        list.mapNotNull { entity -> runCatching { json.decodeFromString<ModelPreset>(entity.data) }.getOrNull() }
    }

    val promptPresets: Flow<List<PromptPreset>> = dao.observePromptPresets().map { list ->
        list.mapNotNull { entity -> runCatching { json.decodeFromString<PromptPreset>(entity.data) }.getOrNull() }
    }

    suspend fun ensureDefaults(): Pair<List<ModelPreset>, List<PromptPreset>> = withContext(Dispatchers.IO) {
        val presetEntities = dao.getModelPresets()
        val promptEntities = dao.getPromptPresets()

        if (presetEntities.isEmpty()) {
            DefaultModelPresets.forEach { preset ->
                dao.upsertModelPreset(ModelPresetEntity(preset.id, json.encodeToString(preset)))
            }
        }

        if (promptEntities.isEmpty()) {
            dao.upsertPromptPreset(
                PromptPresetEntity(DefaultPromptPreset.id, json.encodeToString(DefaultPromptPreset))
            )
        }

        val updatedPresets = dao.getModelPresets().mapNotNull { json.decodeOrNull<ModelPreset>(it.data) }
        val updatedPrompts = dao.getPromptPresets().mapNotNull { json.decodeOrNull<PromptPreset>(it.data) }

        val hasSession = dao.observeSessions().first().isNotEmpty()
        if (!hasSession) {
            val now = Clock.System.now()
            dao.upsertSession(
                ChatSessionEntity(
                    name = "新的对话",
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                    presetId = updatedPresets.firstOrNull()?.id ?: 1,
                    promptPresetId = updatedPrompts.firstOrNull()?.id ?: DefaultPromptPreset.id
                )
            )
        }

        updatedPresets to updatedPrompts
    }

    suspend fun createSession(name: String, presetId: Int, promptPresetId: Int): Long = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toString()
        dao.upsertSession(
            ChatSessionEntity(
                name = name,
                createdAt = now,
                updatedAt = now,
                presetId = presetId,
                promptPresetId = promptPresetId
            )
        )
    }

    suspend fun renameSession(id: Long, name: String) = withContext(Dispatchers.IO) {
        val entity = dao.getSession(id) ?: return@withContext
        dao.upsertSession(entity.copy(name = name, updatedAt = Clock.System.now().toString()))
    }

    suspend fun updateSessionModel(id: Long, presetId: Int) = withContext(Dispatchers.IO) {
        val entity = dao.getSession(id) ?: return@withContext
        dao.upsertSession(entity.copy(presetId = presetId, updatedAt = Clock.System.now().toString()))
    }

    suspend fun updateSessionPrompt(id: Long, promptId: Int) = withContext(Dispatchers.IO) {
        val entity = dao.getSession(id) ?: return@withContext
        dao.upsertSession(entity.copy(promptPresetId = promptId, updatedAt = Clock.System.now().toString()))
    }

    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteMessagesForSession(id)
        dao.deleteSession(id)
    }

    suspend fun appendMessage(sessionId: Long, message: ChatMessage): Long = withContext(Dispatchers.IO) {
        val id = dao.insertMessage(message.toEntity())
        val session = dao.getSession(sessionId)
        if (session != null) {
            dao.upsertSession(session.copy(updatedAt = Clock.System.now().toString()))
        }
        id
    }

    suspend fun updateMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        dao.insertMessage(message.toEntity())
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteMessage(id)
    }

    suspend fun deleteMessages(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            dao.deleteMessages(ids)
        }
    }

    suspend fun saveModelPreset(preset: ModelPreset) = withContext(Dispatchers.IO) {
        dao.upsertModelPreset(ModelPresetEntity(preset.id, json.encodeToString(preset)))
    }

    suspend fun savePromptPreset(preset: PromptPreset) = withContext(Dispatchers.IO) {
        dao.upsertPromptPreset(PromptPresetEntity(preset.id, json.encodeToString(preset)))
    }

    suspend fun deletePromptPreset(id: Int) = withContext(Dispatchers.IO) {
        dao.deletePromptPreset(id)
    }

    suspend fun backup(): BackupBundle = withContext(Dispatchers.IO) {
        val presets = dao.getModelPresets().mapNotNull { json.decodeOrNull<ModelPreset>(it.data) }
        val prompts = dao.getPromptPresets().mapNotNull { json.decodeOrNull<PromptPreset>(it.data) }
        val sessions = dao.observeSessions().first().map { session ->
            val domain = session.toDomain()
            val messages = dao.observeMessages(domain.id).first().map { it.toDomain() }
            SessionWithMessages(domain, messages)
        }
        BackupBundle(presets, prompts, sessions)
    }

    suspend fun import(bundle: BackupBundle, mode: ImportMode) = withContext(Dispatchers.IO) {
        if (mode == ImportMode.REPLACE) {
            dao.clearMessages()
            dao.clearSessions()
            dao.clearModelPresets()
            dao.clearPromptPresets()
        }

        bundle.modelPresets.forEach { preset ->
            dao.upsertModelPreset(ModelPresetEntity(preset.id, json.encodeToString(preset)))
        }

        bundle.promptPresets.forEach { preset ->
            dao.upsertPromptPreset(PromptPresetEntity(preset.id, json.encodeToString(preset)))
        }

        bundle.sessions.forEach { (session, messages) ->
            val sessionId = dao.upsertSession(session.toEntity())
            messages.sortedBy { it.createdAt }.forEach { message ->
                dao.insertMessage(message.copy(sessionId = if (session.id == 0L) sessionId else session.id).toEntity())
            }
        }
    }

    private fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
        id = id,
        name = name,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        presetId = presetId,
        promptPresetId = promptPresetId
    )

    private fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = if (role == MessageRole.ASSISTANT.name) MessageRole.ASSISTANT else MessageRole.USER,
        content = content,
        thinking = thinking,
        createdAt = Instant.parse(createdAt),
        isStreaming = isStreaming
    )

    private fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = if (id == 0L) 0 else id,
        sessionId = sessionId,
        role = role.name,
        content = content,
        thinking = thinking,
        createdAt = createdAt.toString(),
        isStreaming = isStreaming
    )

    private fun ChatSession.toEntity(): ChatSessionEntity = ChatSessionEntity(
        id = if (id == 0L) 0 else id,
        name = name,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        presetId = presetId,
        promptPresetId = promptPresetId
    )

    private inline fun <reified T> Json.decodeOrNull(raw: String): T? =
        runCatching { decodeFromString<T>(raw) }.getOrNull()

    companion object {
        fun create(context: Context): ChatRepository = ChatRepository(AppDatabase.get(context).chatDao())
    }
}

enum class ImportMode { MERGE, REPLACE }

data class BackupBundle(
    val modelPresets: List<ModelPreset>,
    val promptPresets: List<PromptPreset>,
    val sessions: List<SessionWithMessages>
) {
    fun toJson(json: Json = Json { encodeDefaults = true; prettyPrint = true }): String =
        json.encodeToString(this)

    companion object {
        fun fromJson(raw: String, json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }): BackupBundle =
            json.decodeFromString(raw)
    }
}
