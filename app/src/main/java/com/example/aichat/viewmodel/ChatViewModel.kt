package com.example.aichat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.repository.BackupBundle
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.repository.ImportMode
import com.example.aichat.model.ChatMessage
import com.example.aichat.model.ChatSession
import com.example.aichat.model.MessageRole
import com.example.aichat.model.ModelPreset
import com.example.aichat.model.PromptPreset
import com.example.aichat.model.SessionWithMessages
import com.example.aichat.model.apply as applyRegexRule
import com.example.aichat.network.ChatService
import com.example.aichat.network.ChatService.StreamEvent
import com.example.aichat.util.FormattedContent
import com.example.aichat.util.StreamFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private data class CoreState(
    val sessions: List<ChatSession>,
    val models: List<ModelPreset>,
    val prompts: List<PromptPreset>,
    val messages: List<ChatMessage>
)

private data class InteractionState(
    val core: CoreState,
    val composerText: String,
    val isSidebarOpen: Boolean,
    val isSettingsOpen: Boolean
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val service: ChatService = ChatService()
) : ViewModel() {

    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val composerText = MutableStateFlow("")
    private val sidebarOpen = MutableStateFlow(false)
    private val settingsOpen = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val exportJson = MutableStateFlow<String?>(null)
    private val streamingState = MutableStateFlow<StreamingState?>(null)
    private val settingsDraft = MutableStateFlow<SettingsDraft?>(null)
    private val editingState = MutableStateFlow<EditingState?>(null)

    private val sessionsState = repository.sessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val modelPresetsState = repository.modelPresets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val promptPresetsState = repository.promptPresets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val messagesState = selectedSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.messages(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private var streamingJob: Job? = null

    private data class AggregateState(
        val interaction: InteractionState,
        val streaming: StreamingState?,
        val error: String?,
        val export: String?,
        val draft: SettingsDraft?
    )

    private val coreState = combine(
        sessionsState,
        modelPresetsState,
        promptPresetsState,
        messagesState
    ) { sessions, models, prompts, messages ->
        CoreState(sessions, models, prompts, messages)
    }

    private val interactionState = combine(
        coreState,
        composerText,
        sidebarOpen,
        settingsOpen
    ) { core, composer, sidebar, settings ->
        InteractionState(
            core = core,
            composerText = composer,
            isSidebarOpen = sidebar,
            isSettingsOpen = settings
        )
    }

    private val aggregateState = combine(
        interactionState,
        streamingState,
        errorMessage,
        exportJson,
        settingsDraft
    ) { interaction, streaming, error, export, draft ->
        AggregateState(
            interaction = interaction,
            streaming = streaming,
            error = error,
            export = export,
            draft = draft
        )
    }

    val uiState = combine(
        aggregateState,
        editingState
    ) { aggregate, editing ->
        val interaction = aggregate.interaction
        val core = interaction.core
        val sessions = core.sessions
        val currentId = selectedSessionId.value ?: sessions.firstOrNull()?.id
        val session = sessions.find { it.id == currentId }
        val activeModel = session?.let { s ->
            core.models.find { it.id == s.presetId && it.enabled }
        } ?: core.models.firstOrNull { it.enabled }
        val activePrompt = session?.let { s ->
            core.prompts.find { it.id == s.promptPresetId }
        } ?: core.prompts.firstOrNull()

        ChatUiState(
            sessions = sessions,
            currentSessionId = currentId,
            messages = core.messages,
            composerText = interaction.composerText,
            isSidebarOpen = interaction.isSidebarOpen,
            isSettingsOpen = interaction.isSettingsOpen,
            modelPresets = core.models,
            promptPresets = core.prompts,
            activeModelPreset = activeModel,
            activePromptPreset = activePrompt,
            isStreaming = aggregate.streaming != null,
            streamingMessageId = aggregate.streaming?.messageId,
            streamingThinking = aggregate.streaming?.thinking.orEmpty(),
            streamingContent = aggregate.streaming?.formatted ?: FormattedContent.Empty,
            errorMessage = aggregate.error,
            exportJson = aggregate.export,
            settingsDraft = if (interaction.isSettingsOpen) aggregate.draft else null,
            editingMessageId = editing?.message?.id,
            editingDraft = editing?.draft ?: ""
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ChatUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
        }
        viewModelScope.launch {
            sessionsState.collect { sessions ->
                if (selectedSessionId.value == null) {
                    selectedSessionId.value = sessions.firstOrNull()?.id
                } else if (selectedSessionId.value !in sessions.map { it.id }) {
                    selectedSessionId.value = sessions.firstOrNull()?.id
                }
            }
        }
        viewModelScope.launch {
            messagesState.collect { messages ->
                val editing = editingState.value ?: return@collect
                val updated = messages.find { it.id == editing.message.id }
                when {
                    updated == null -> editingState.value = null
                    updated != editing.message -> editingState.value = editing.copy(message = updated)
                }
            }
        }
    }

    fun toggleSidebar() {
        sidebarOpen.update { !it }
    }

    fun selectSession(id: Long) {
        selectedSessionId.value = id
        sidebarOpen.value = false
        editingState.value = null
    }

    fun updateComposer(text: String) {
        composerText.value = text
    }

    fun openSettings() {
        val newDraft = SettingsDraft(
            modelPresets = modelPresetsState.value.map { it.toDraft() },
            promptPresets = promptPresetsState.value.map { it.toDraft() },
            selectedPromptId = promptPresetsState.value.firstOrNull()?.id
        )
        settingsDraft.value = newDraft
        settingsOpen.value = true
    }

    fun closeSettings() {
        settingsOpen.value = false
        exportJson.value = null
    }

    fun setSelectedPromptForSettings(id: Int) {
        settingsDraft.update { draft -> draft?.copy(selectedPromptId = id) }
    }

    fun updateModelPreset(draftId: Int, transform: (ModelPresetDraft) -> ModelPresetDraft) {
        settingsDraft.update { draft ->
            draft?.copy(modelPresets = draft.modelPresets.map { if (it.id == draftId) transform(it) else it })
        }
    }

    fun updatePromptPreset(draftId: Int, transform: (PromptPresetDraft) -> PromptPresetDraft) {
        settingsDraft.update { draft ->
            draft?.copy(promptPresets = draft.promptPresets.map { if (it.id == draftId) transform(it) else it })
        }
    }

    fun addRegexRuleToPrompt(promptId: Int) {
        updatePromptPreset(promptId) { prompt ->
            val nextId = (prompt.regexRules.maxOfOrNull { it.id } ?: -1) + 1
            prompt.copy(regexRules = prompt.regexRules + RegexRuleDraft(nextId, pattern = "", replacement = "", flags = ""))
        }
    }

    fun removeRegexRule(promptId: Int, ruleId: Int) {
        updatePromptPreset(promptId) { prompt ->
            prompt.copy(regexRules = prompt.regexRules.filterNot { it.id == ruleId })
        }
    }

    fun applySettings() {
        val draft = settingsDraft.value ?: return
        viewModelScope.launch {
            draft.modelPresets.forEach { repository.saveModelPreset(it.toPreset()) }
            draft.promptPresets.forEach { repository.savePromptPreset(it.toPreset()) }
            settingsOpen.value = false
        }
    }

    fun savePromptPresetAsNew(baseId: Int) {
        val draft = settingsDraft.value ?: return
        val base = draft.promptPresets.find { it.id == baseId } ?: return
        val nextId = (promptPresetsState.value.maxOfOrNull { it.id } ?: 0) + 1
        settingsDraft.update {
            it?.copy(promptPresets = it.promptPresets + base.copy(id = nextId, name = base.name + " 副本"))
        }
    }

    fun deletePromptPresetFromSettings(id: Int) {
        settingsDraft.update { draft ->
            draft?.copy(
                promptPresets = draft.promptPresets.filterNot { it.id == id },
                selectedPromptId = draft.promptPresets.firstOrNull { it.id != id }?.id
            )
        }
        viewModelScope.launch { repository.deletePromptPreset(id) }
    }

    fun createPromptPreset() {
        val nextId = (promptPresetsState.value.maxOfOrNull { it.id } ?: 0) + 1
        val newDraft = PromptPresetDraft(
            id = nextId,
            name = "预设$nextId",
            systemPrompt = "",
            firstUser = "",
            firstAssistant = "",
            messagePrefix = "",
            assistantPrefill = "",
            regexRules = emptyList()
        )
        settingsDraft.update { draft ->
            draft?.copy(
                promptPresets = draft.promptPresets + newDraft,
                selectedPromptId = newDraft.id
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.import(BackupBundle(emptyList(), emptyList(), emptyList()), ImportMode.REPLACE)
            settingsDraft.value = null
            repository.ensureDefaults()
        }
    }

    fun createSession(name: String, presetId: Int, promptId: Int) {
        viewModelScope.launch {
            val id = repository.createSession(name, presetId, promptId)
            val prompt = promptPresetsState.value.find { it.id == promptId }
            if (prompt != null && prompt.firstAssistant.isNotBlank()) {
                repository.appendMessage(
                    id,
                    ChatMessage(
                        id = 0,
                        sessionId = id,
                        role = MessageRole.ASSISTANT,
                        content = prompt.firstAssistant,
                        createdAt = Clock.System.now()
                    )
                )
            }
            selectedSessionId.value = id
        }
    }

    fun updateSessionModel(presetId: Int) {
        val id = selectedSessionId.value ?: return
        viewModelScope.launch { repository.updateSessionModel(id, presetId) }
    }

    fun updateSessionPrompt(promptId: Int) {
        val id = selectedSessionId.value ?: return
        viewModelScope.launch { repository.updateSessionPrompt(id, promptId) }
    }

    fun renameSession(id: Long, name: String) {
        viewModelScope.launch { repository.renameSession(id, name) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            repository.deleteSession(id)
            if (selectedSessionId.value == id) {
                val sessions = sessionsState.value
                selectedSessionId.value = sessions.firstOrNull { it.id != id }?.id
            }
        }
    }

    fun exportAll() {
        viewModelScope.launch {
            val json = repository.backup().toJson()
            exportJson.value = json
        }
    }

    fun importFromJson(raw: String, mode: ImportMode) {
        viewModelScope.launch {
            val bundle = BackupBundle.fromJson(raw)
            repository.import(bundle, mode)
            settingsDraft.value = null
            exportJson.value = null
        }
    }

    fun sendMessage() {
        val text = composerText.value.trim()
        val sessionId = selectedSessionId.value ?: return
        if (editingState.value != null) {
            errorMessage.value = "请先完成或取消正在进行的编辑"
            return
        }
        if (text.isEmpty()) return

        val session = sessionsState.value.find { it.id == sessionId } ?: return
        val model = modelPresetsState.value.find { it.id == session.presetId && it.enabled }
            ?: modelPresetsState.value.firstOrNull { it.enabled }
            ?: return
        val prompt = promptPresetsState.value.find { it.id == session.promptPresetId }
            ?: promptPresetsState.value.firstOrNull()
            ?: return

        streamingJob?.cancel()
        viewModelScope.launch {
            try {
                composerText.value = ""
                val createdAt = Clock.System.now()
                val userMessage = ChatMessage(
                    id = 0,
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = text,
                    createdAt = createdAt
                )
                val userId = repository.appendMessage(sessionId, userMessage)
                val assistantTime = Clock.System.now()
                val assistantMessage = ChatMessage(
                    id = 0,
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = prompt.assistantPrefill,
                    createdAt = assistantTime,
                    thinking = "",
                    isStreaming = true
                )
                val assistantId = repository.appendMessage(sessionId, assistantMessage)
                val history = messagesState.value + userMessage.copy(id = userId)
                startStreaming(
                    session = session,
                    prompt = prompt,
                    model = model,
                    assistantMessage = assistantMessage.copy(id = assistantId),
                    history = history
                )
            } catch (t: Throwable) {
                errorMessage.value = t.message ?: "发送失败"
            }
        }
    }

    fun startEditingMessage(messageId: Long) {
        if (streamingState.value != null) {
            errorMessage.value = "请等待当前消息发送完成"
            return
        }
        if (editingState.value != null) {
            errorMessage.value = "请先完成或取消正在进行的编辑"
            return
        }
        val message = messagesState.value.find { it.id == messageId } ?: return
        editingState.value = EditingState(message = message, draft = message.content)
    }

    fun updateEditingDraft(text: String) {
        editingState.update { editing -> editing?.copy(draft = text) }
    }

    fun cancelEditing() {
        editingState.value = null
    }

    fun saveEditing() {
        val editing = editingState.value ?: return
        val newContent = editing.draft.trim()
        if (newContent.isEmpty()) {
            errorMessage.value = "消息内容不能为空"
            return
        }
        viewModelScope.launch {
            try {
                val session = sessionsState.value.find { it.id == editing.message.sessionId } ?: return@launch
                val prompt = promptPresetsState.value.find { it.id == session.promptPresetId }
                    ?: promptPresetsState.value.firstOrNull()
                    ?: return@launch
                val finalContent = if (editing.message.role == MessageRole.ASSISTANT) {
                    applyRegexRules(newContent, prompt)
                } else {
                    newContent
                }
                repository.updateMessage(editing.message.copy(content = finalContent))
                editingState.value = null
            } catch (t: Throwable) {
                errorMessage.value = t.message ?: "保存失败"
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                if (streamingState.value?.messageId == messageId) {
                    streamingJob?.cancel()
                    streamingState.value = null
                }
                if (editingState.value?.message?.id == messageId) {
                    editingState.value = null
                }
                repository.deleteMessage(messageId)
            } catch (t: Throwable) {
                errorMessage.value = t.message ?: "删除失败"
            }
        }
    }

    fun regenerateMessage(messageId: Long) {
        val sessionId = selectedSessionId.value ?: return
        if (editingState.value != null) {
            errorMessage.value = "请先完成或取消正在进行的编辑"
            return
        }
        if (streamingState.value != null) {
            errorMessage.value = "请等待当前消息发送完成"
            return
        }
        viewModelScope.launch {
            try {
                val session = sessionsState.value.find { it.id == sessionId } ?: return@launch
                val model = modelPresetsState.value.find { it.id == session.presetId && it.enabled }
                    ?: modelPresetsState.value.firstOrNull { it.enabled }
                    ?: return@launch
                val prompt = promptPresetsState.value.find { it.id == session.promptPresetId }
                    ?: promptPresetsState.value.firstOrNull()
                    ?: return@launch
                val messages = repository.messagesOnce(sessionId)
                val targetIndex = messages.indexOfFirst { it.id == messageId && it.role == MessageRole.ASSISTANT }
                if (targetIndex == -1) {
                    errorMessage.value = "未找到可重新生成的助手消息"
                    return@launch
                }
                val userIndex = (targetIndex - 1 downTo 0).firstOrNull { messages[it].role == MessageRole.USER }
                if (userIndex == null) {
                    errorMessage.value = "未找到可重新生成的用户消息"
                    return@launch
                }
                val toRemove = messages.subList(userIndex + 1, messages.size).map { it.id }
                repository.deleteMessages(toRemove)
                val history = messages.subList(0, userIndex + 1)
                val assistantMessage = ChatMessage(
                    id = 0,
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = prompt.assistantPrefill,
                    createdAt = Clock.System.now(),
                    thinking = "",
                    isStreaming = true
                )
                val assistantId = repository.appendMessage(sessionId, assistantMessage)
                startStreaming(
                    session = session,
                    prompt = prompt,
                    model = model,
                    assistantMessage = assistantMessage.copy(id = assistantId),
                    history = history
                )
            } catch (t: Throwable) {
                errorMessage.value = t.message ?: "重新生成失败"
            }
        }
    }

    private fun startStreaming(
        session: ChatSession,
        prompt: PromptPreset,
        model: ModelPreset,
        assistantMessage: ChatMessage,
        history: List<ChatMessage>
    ) {
        streamingJob?.cancel()
        val formatter = StreamFormatter()
        if (assistantMessage.content.isNotEmpty()) {
            formatter.feed(assistantMessage.content)
        }
        streamingState.value = StreamingState(
            sessionId = session.id,
            messageId = assistantMessage.id,
            thinking = assistantMessage.thinking,
            formatter = formatter,
            consumed = assistantMessage.content.length,
            formatted = formatter.snapshot()
        )

        val sessionWithMessages = SessionWithMessages(session, history)

        streamingJob = viewModelScope.launch {
            service.streamCompletion(sessionWithMessages, model, prompt).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        val current = streamingState.value
                        val formatterState = current?.formatter
                        val consumed = current?.consumed ?: 0
                        val content = event.content
                        val delta = if (content.length >= consumed) content.substring(consumed) else content
                        val formatted = formatterState?.let {
                            if (delta.isNotEmpty()) it.feed(delta) else it.snapshot()
                        } ?: FormattedContent.Empty
                        val updated = assistantMessage.copy(
                            content = event.content,
                            thinking = event.thinking,
                            isStreaming = true
                        )
                        repository.updateMessage(updated)
                        if (current != null && formatterState != null) {
                            streamingState.value = current.copy(
                                thinking = event.thinking,
                                consumed = content.length,
                                formatted = formatted
                            )
                        }
                    }
                    is StreamEvent.Completed -> {
                        val current = streamingState.value
                        val formatterState = current?.formatter
                        val consumed = current?.consumed ?: 0
                        val delta = if (event.content.length >= consumed) {
                            event.content.substring(consumed)
                        } else event.content
                        val formatted = formatterState?.let {
                            if (delta.isNotEmpty()) it.feed(delta) else it.snapshot()
                        }
                        val updated = assistantMessage.copy(
                            content = event.content,
                            thinking = event.thinking,
                            isStreaming = false
                        )
                        repository.updateMessage(updated)
                        if (current != null && formatterState != null && formatted != null) {
                            streamingState.value = current.copy(
                                thinking = event.thinking,
                                consumed = event.content.length,
                                formatted = formatted
                            )
                        }
                        streamingState.value = null
                    }
                }
            }
        }.also { job ->
            job.invokeOnCompletion { throwable ->
                if (throwable != null) {
                    errorMessage.value = throwable.message ?: "发送失败"
                    viewModelScope.launch {
                        repository.updateMessage(assistantMessage.copy(isStreaming = false))
                    }
                    streamingState.value = null
                }
            }
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    data class StreamingState(
        val sessionId: Long,
        val messageId: Long,
        val thinking: String,
        val formatter: StreamFormatter,
        val consumed: Int,
        val formatted: FormattedContent
    )

    data class ChatUiState(
        val sessions: List<ChatSession> = emptyList(),
        val currentSessionId: Long? = null,
        val messages: List<ChatMessage> = emptyList(),
        val composerText: String = "",
        val isSidebarOpen: Boolean = false,
        val isSettingsOpen: Boolean = false,
        val modelPresets: List<ModelPreset> = emptyList(),
        val promptPresets: List<PromptPreset> = emptyList(),
        val activeModelPreset: ModelPreset? = null,
        val activePromptPreset: PromptPreset? = null,
        val isStreaming: Boolean = false,
        val streamingMessageId: Long? = null,
        val streamingThinking: String = "",
        val streamingContent: FormattedContent = FormattedContent.Empty,
        val errorMessage: String? = null,
        val exportJson: String? = null,
        val settingsDraft: SettingsDraft? = null,
        val editingMessageId: Long? = null,
        val editingDraft: String = ""
    )

    data class EditingState(
        val message: ChatMessage,
        val draft: String
    )

    private fun applyRegexRules(content: String, prompt: PromptPreset): String {
        return prompt.regexRules.fold(content) { acc, rule -> rule.applyRegexRule(acc) }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ChatViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
