package com.example.aichat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: ChatSessionEntity): Long

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: ChatMessageEntity): Long

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getMessages(sessionId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<Long>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelPreset(entity: ModelPresetEntity)

    @Query("SELECT * FROM model_presets ORDER BY id")
    fun observeModelPresets(): Flow<List<ModelPresetEntity>>

    @Query("SELECT * FROM model_presets ORDER BY id")
    suspend fun getModelPresets(): List<ModelPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPromptPreset(entity: PromptPresetEntity)

    @Query("SELECT * FROM prompt_presets ORDER BY id")
    fun observePromptPresets(): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets ORDER BY id")
    suspend fun getPromptPresets(): List<PromptPresetEntity>

    @Query("DELETE FROM prompt_presets WHERE id = :id")
    suspend fun deletePromptPreset(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(entity: AppMetadataEntity)

    @Query("SELECT value FROM app_metadata WHERE key = :key")
    suspend fun getMetadata(key: String): String?

    @Query("DELETE FROM app_metadata WHERE key = :key")
    suspend fun deleteMetadata(key: String)

    @Query("DELETE FROM model_presets")
    suspend fun clearModelPresets()

    @Query("DELETE FROM prompt_presets")
    suspend fun clearPromptPresets()

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM messages")
    suspend fun clearMessages()
}
