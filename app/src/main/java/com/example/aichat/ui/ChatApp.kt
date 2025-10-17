package com.example.aichat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aichat.model.ChatMessage
import com.example.aichat.model.MessageRole
import com.example.aichat.model.ModelPreset
import com.example.aichat.model.ModelType
import com.example.aichat.model.OpenAIConfig
import com.example.aichat.model.GeminiConfig
import com.example.aichat.model.GeminiProxyConfig
import com.example.aichat.model.PromptPreset
import com.example.aichat.ui.theme.AccentBlue
import com.example.aichat.ui.theme.AccentGreen
import com.example.aichat.ui.theme.AccentYellow
import com.example.aichat.ui.theme.BgDark
import com.example.aichat.viewmodel.ChatViewModel
import com.example.aichat.viewmodel.ModelPresetDraft
import com.example.aichat.viewmodel.PromptPresetDraft
import com.example.aichat.viewmodel.RegexRuleDraft
import com.example.aichat.viewmodel.SettingsDraft
import com.example.aichat.viewmodel.changeType
import com.example.aichat.data.repository.ImportMode
import com.example.aichat.util.FormattedContent
import com.example.aichat.util.FormattedSegment
import com.example.aichat.util.SegmentStyle
import com.example.aichat.util.StreamFormatter
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ChatApp(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                onMenu = viewModel::toggleSidebar,
                onSettings = viewModel::openSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            val isWide = maxWidth > 900.dp

            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ) {
                AnimatedVisibility(visible = isWide || uiState.isSidebarOpen) {
                    Sidebar(
                        uiState = uiState,
                        onClose = { viewModel.toggleSidebar() },
                        onNewChat = newChat@{
                            val presetId = uiState.modelPresets.firstOrNull { it.enabled }?.id ?: return@newChat
                            val promptId = uiState.promptPresets.firstOrNull()?.id ?: return@newChat
                            viewModel.createSession("新的对话", presetId, promptId)
                        },
                        onSelectSession = viewModel::selectSession,
                        onRename = viewModel::renameSession,
                        onDelete = viewModel::deleteSession
                    )
                }

                ChatArea(
                    uiState = uiState,
                    onComposerChange = viewModel::updateComposer,
                    onSend = viewModel::sendMessage,
                    onModelSelect = { viewModel.updateSessionModel(it.id) },
                    onPromptSelect = { viewModel.updateSessionPrompt(it.id) },
                    onStartEdit = viewModel::startEditingMessage,
                    onEditChange = viewModel::updateEditingDraft,
                    onSaveEdit = viewModel::saveEditing,
                    onCancelEdit = viewModel::cancelEditing,
                    onDelete = viewModel::deleteMessage,
                    onRegenerate = viewModel::regenerateMessage
                )
            }

            if (!isWide && uiState.isSidebarOpen) {
                Box(Modifier.fillMaxSize().clickable { viewModel.toggleSidebar() })
            }

            if (uiState.isSettingsOpen) {
                SettingsDialog(
                    draft = uiState.settingsDraft,
                    onDismiss = viewModel::closeSettings,
                    onApply = viewModel::applySettings,
                    onUpdateModel = viewModel::updateModelPreset,
                    onUpdatePrompt = viewModel::updatePromptPreset,
                    onAddRegex = viewModel::addRegexRuleToPrompt,
                    onRemoveRegex = viewModel::removeRegexRule,
                    onSelectPrompt = viewModel::setSelectedPromptForSettings,
                    onCreatePrompt = viewModel::createPromptPreset,
                    onSavePromptAs = viewModel::savePromptPresetAsNew,
                    onDeletePrompt = viewModel::deletePromptPresetFromSettings,
                    onExportAll = viewModel::exportAll,
                    exportJson = uiState.exportJson,
                    onImport = viewModel::importFromJson,
                    onClearAll = viewModel::clearAllData
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(onMenu: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "菜单", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AI Chat", style = MaterialTheme.typography.titleLarge)
                Text("v9.3⚡", color = AccentYellow, fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = onSettings) {
            Text("设置")
        }
    }
}

@Composable
private fun Sidebar(
    uiState: ChatViewModel.ChatUiState,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("对话列表", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }
        Spacer(Modifier.height(12.dp))
        ElevatedButton(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("新建对话")
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(uiState.sessions, key = { it.id }) { session ->
                val isActive = session.id == uiState.currentSessionId
                SessionCard(
                    session = session,
                    isActive = isActive,
                    onClick = { onSelectSession(session.id) },
                    onRename = onRename,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: com.example.aichat.model.ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(session.name) }
    val background = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(session.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(formatInstant(session.updatedAt), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { showRename = true }) { Text("重命名") }
                TextButton(onClick = { onDelete(session.id) }) { Text("删除", color = Color.Red) }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(session.id, newName)
                    showRename = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            }
        )
    }
}

private fun formatInstant(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.date} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

@Composable
private fun ChatArea(
    uiState: ChatViewModel.ChatUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onModelSelect: (ModelPreset) -> Unit,
    onPromptSelect: (PromptPreset) -> Unit,
    onStartEdit: (Long) -> Unit,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: (Long) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
    ) {
        MessagesList(
            modifier = Modifier.weight(1f),
            messages = uiState.messages,
            streamingMessageId = uiState.streamingMessageId,
            streamingContent = uiState.streamingContent,
            thinking = uiState.streamingThinking,
            editingMessageId = uiState.editingMessageId,
            editingDraft = uiState.editingDraft,
            actionsEnabled = !uiState.isStreaming && uiState.editingMessageId == null,
            onStartEdit = onStartEdit,
            onEditChange = onEditChange,
            onSaveEdit = onSaveEdit,
            onCancelEdit = onCancelEdit,
            onDelete = onDelete,
            onRegenerate = onRegenerate
        )
        Divider()
        Composer(
            uiState = uiState,
            onComposerChange = onComposerChange,
            onSend = onSend,
            onModelSelect = onModelSelect,
            onPromptSelect = onPromptSelect
        )
    }
}

@Composable
private fun ColumnScope.MessagesList(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    streamingMessageId: Long?,
    streamingContent: FormattedContent,
    thinking: String,
    editingMessageId: Long?,
    editingDraft: String,
    actionsEnabled: Boolean,
    onStartEdit: (Long) -> Unit,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val isStreaming = message.id == streamingMessageId
            val draft = if (message.id == editingMessageId) editingDraft else null
            val regen = if (message.role == MessageRole.ASSISTANT) { { onRegenerate(message.id) } } else null
            MessageCard(
                index = index,
                message = message,
                isStreaming = isStreaming,
                streamingContent = if (isStreaming) streamingContent else null,
                thinking = if (isStreaming) thinking else message.thinking,
                editingDraft = draft,
                actionsEnabled = actionsEnabled,
                onStartEdit = { onStartEdit(message.id) },
                onEditChange = onEditChange,
                onSaveEdit = onSaveEdit,
                onCancelEdit = onCancelEdit,
                onDelete = { onDelete(message.id) },
                onRegenerate = regen
            )
        }
    }
}

@Composable
private fun MessageCard(
    index: Int,
    message: ChatMessage,
    isStreaming: Boolean,
    streamingContent: FormattedContent?,
    thinking: String,
    editingDraft: String?,
    actionsEnabled: Boolean,
    onStartEdit: () -> Unit,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: (() -> Unit)?
) {
    val background = when (message.role) {
        MessageRole.USER -> MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        MessageRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    }
    val accent = when (message.role) {
        MessageRole.USER -> AccentBlue
        MessageRole.ASSISTANT -> AccentGreen
    }
    val staticFormatted = remember(message.id, message.content) { StreamFormatter.formatAll(message.content) }
    val displayContent = if (isStreaming && streamingContent != null) streamingContent else staticFormatted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (message.role == MessageRole.USER) "用户" else "助手", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("#${index + 1}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (editingDraft != null) {
            OutlinedTextField(
                value = editingDraft,
                onValueChange = onEditChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 12
            )
        } else if (displayContent.isBlank()) {
            Text("(空内容)", color = Color.White.copy(alpha = 0.6f))
        } else {
            FormattedMessageBody(content = displayContent, showCursor = isStreaming)
        }
        AnimatedVisibility(visible = thinking.isNotBlank(), enter = expandVertically() + fadeIn(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 12.dp)) {
                Text("思维链", color = AccentYellow, fontSize = 12.sp)
                Text(thinking, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
        if (isStreaming) {
            Spacer(Modifier.height(8.dp))
            Text("生成中…", color = AccentBlue, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (editingDraft != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                OutlinedButton(onClick = onCancelEdit) { Text("取消") }
                Button(onClick = onSaveEdit, enabled = editingDraft.isNotBlank()) { Text("保存") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onStartEdit, enabled = actionsEnabled) { Text("编辑") }
                onRegenerate?.let { regen ->
                    TextButton(onClick = regen, enabled = actionsEnabled) { Text("重新生成") }
                }
                TextButton(onClick = onDelete, enabled = actionsEnabled) { Text("删除", color = Color.Red) }
            }
        }
    }
}

@Composable
private fun FormattedMessageBody(content: FormattedContent, showCursor: Boolean) {
    val annotated = remember(content, showCursor) {
        buildAnnotatedString {
            appendSegments(content.prefix)
            appendSegments(content.tail)
            if (showCursor) {
                withStyle(SpanStyle(color = AccentBlue, fontWeight = FontWeight.SemiBold)) {
                    append("▌")
                }
            }
        }
    }
    Text(annotated)
}

private fun AnnotatedString.Builder.appendSegments(segments: List<FormattedSegment>) {
    segments.forEach { segment ->
        if (segment.text.isEmpty()) return@forEach
        when (segment.style) {
            SegmentStyle.PLAIN -> append(segment.text)
            SegmentStyle.DOUBLE_QUOTE, SegmentStyle.CHINESE_QUOTE -> withStyle(
                SpanStyle(color = AccentGreen, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)
            ) {
                append(segment.text)
            }
            SegmentStyle.BOLD -> withStyle(
                SpanStyle(color = AccentYellow, fontWeight = FontWeight.SemiBold)
            ) {
                append(segment.text)
            }
        }
    }
}

@Composable
private fun Composer(
    uiState: ChatViewModel.ChatUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onModelSelect: (ModelPreset) -> Unit,
    onPromptSelect: (PromptPreset) -> Unit
) {
    val composerEnabled = uiState.editingMessageId == null && !uiState.isStreaming
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .imePadding()
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("模型预设", fontWeight = FontWeight.SemiBold)
            ModelSelector(
                presets = uiState.modelPresets.filter { it.enabled },
                selectedId = uiState.activeModelPreset?.id,
                onSelect = onModelSelect
            )
            Spacer(Modifier.weight(1f))
            Text("提示词", fontWeight = FontWeight.SemiBold)
            PromptSelector(
                prompts = uiState.promptPresets,
                selectedId = uiState.activePromptPreset?.id,
                onSelect = onPromptSelect
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.composerText,
                onValueChange = onComposerChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息后点击右侧“发送”") },
                maxLines = 6,
                enabled = composerEnabled
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSend, enabled = composerEnabled && uiState.composerText.isNotBlank()) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (uiState.isStreaming) "进行中" else "发送")
            }
        }
    }
}

@Composable
private fun ModelSelector(presets: List<ModelPreset>, selectedId: Int?, onSelect: (ModelPreset) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = { onSelect(preset) },
                label = { Text(preset.displayName) }
            )
        }
    }
}

@Composable
private fun PromptSelector(prompts: List<PromptPreset>, selectedId: Int?, onSelect: (PromptPreset) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        prompts.forEach { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = { onSelect(preset) },
                label = { Text(preset.name) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    draft: SettingsDraft?,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onUpdateModel: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit,
    onUpdatePrompt: (Int, (PromptPresetDraft) -> PromptPresetDraft) -> Unit,
    onAddRegex: (Int) -> Unit,
    onRemoveRegex: (Int, Int) -> Unit,
    onSelectPrompt: (Int) -> Unit,
    onCreatePrompt: () -> Unit,
    onSavePromptAs: (Int) -> Unit,
    onDeletePrompt: (Int) -> Unit,
    onExportAll: () -> Unit,
    exportJson: String?,
    onImport: (String, ImportMode) -> Unit,
    onClearAll: () -> Unit
) {
    if (draft == null) return
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(20.dp)) {
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("模型预设", Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("提示词", Modifier.padding(12.dp)) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("备份", Modifier.padding(12.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            when (selectedTab) {
                0 -> ModelPresetPane(draft.modelPresets, onUpdateModel)
                1 -> PromptPresetPane(
                    draft = draft,
                    onUpdatePrompt = onUpdatePrompt,
                    onAddRegex = onAddRegex,
                    onRemoveRegex = onRemoveRegex,
                    onSelectPrompt = onSelectPrompt,
                    onCreatePrompt = onCreatePrompt,
                    onSavePromptAs = onSavePromptAs,
                    onDeletePrompt = onDeletePrompt
                )
                2 -> BackupPane(exportJson = exportJson, onExportAll = onExportAll, onImport = onImport, onClearAll = onClearAll)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onApply) { Text("应用") }
            }
        }
    }
}

@Composable
private fun ModelPresetPane(presets: List<ModelPresetDraft>, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        presets.forEach { preset ->
            OutlinedCard { Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("预设${preset.id}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("启用")
                    Switch(checked = preset.enabled, onCheckedChange = { checked ->
                        onUpdate(preset.id) { it.copy(enabled = checked) }
                    })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = preset.displayName,
                    onValueChange = { value -> onUpdate(preset.id) { it.copy(displayName = value) } },
                    label = { Text("显示名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("模型类型", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelType.values().forEach { type ->
                        FilterChip(
                            selected = preset.type == type,
                            onClick = { onUpdate(preset.id) { it.changeType(type) } },
                            label = { Text(typeLabel(type)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (val config = preset.config) {
                    is OpenAIConfig -> OpenAIConfigEditor(preset.id, config, onUpdate)
                    is GeminiConfig -> GeminiConfigEditor(preset.id, config, onUpdate)
                    is GeminiProxyConfig -> GeminiProxyConfigEditor(preset.id, config, onUpdate)
                }
            } }
        }
    }
}

@Composable
private fun OpenAIConfigEditor(id: Int, config: OpenAIConfig, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = config.base, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(base = value)) } }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = config.key, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(key = value)) } }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = config.model, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(model = value)) } }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth())
        NumberField(label = "Temperature", value = config.temperature?.toString() ?: "", onChange = { number ->
            val parsed = number.toDoubleOrNull()
            onUpdate(id) { draft -> draft.copy(config = config.copy(temperature = parsed)) }
        })
        NumberField(label = "Top P", value = config.topP?.toString() ?: "", onChange = { number ->
            val parsed = number.toDoubleOrNull()
            onUpdate(id) { draft -> draft.copy(config = config.copy(topP = parsed)) }
        })
        NumberField(label = "Max Tokens", value = config.maxTokens?.toString() ?: "", onChange = { number ->
            val parsed = number.toIntOrNull()
            onUpdate(id) { draft -> draft.copy(config = config.copy(maxTokens = parsed)) }
        })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = config.stream, onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(stream = checked)) } })
            Text("流式输出")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = config.useThinking, onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(useThinking = checked)) } })
            Text("启用思维链")
        }
        if (config.useThinking) {
            OutlinedTextField(value = config.thinkingEffort.orEmpty(), onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(thinkingEffort = value.ifBlank { null })) } }, label = { Text("推理强度") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GeminiConfigEditor(id: Int, config: GeminiConfig, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = config.base, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(base = value)) } }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = config.key, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(key = value)) } }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = config.model, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(model = value)) } }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth())
        NumberField("Temperature", config.temperature?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(temperature = number.toDoubleOrNull())) }
        }
        NumberField("Top P", config.topP?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(topP = number.toDoubleOrNull())) }
        }
        NumberField("Top K", config.topK?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(topK = number.toIntOrNull())) }
        }
        NumberField("Max Output Tokens", config.maxTokens?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(maxTokens = number.toIntOrNull())) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = config.stream, onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(stream = checked)) } })
            Text("流式输出")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = config.useThinking, onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(useThinking = checked)) } })
            Text("启用思维链")
        }
        if (config.useThinking) {
            NumberField("思维预算", config.thinkingBudget?.toString() ?: "") { number ->
                onUpdate(id) { draft -> draft.copy(config = config.copy(thinkingBudget = number.toIntOrNull())) }
            }
        }
    }
}

@Composable
private fun GeminiProxyConfigEditor(id: Int, config: GeminiProxyConfig, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = config.proxyUrl, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(proxyUrl = value)) } }, label = { Text("代理地址") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = config.proxyPass, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(proxyPass = value)) } }, label = { Text("代理密钥") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = config.model, onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(model = value)) } }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth())
        NumberField("Temperature", config.temperature?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(temperature = number.toDoubleOrNull())) }
        }
        NumberField("Top P", config.topP?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(topP = number.toDoubleOrNull())) }
        }
        NumberField("Top K", config.topK?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(topK = number.toIntOrNull())) }
        }
        NumberField("Max Output Tokens", config.maxTokens?.toString() ?: "") { number ->
            onUpdate(id) { draft -> draft.copy(config = config.copy(maxTokens = number.toIntOrNull())) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = config.stream, onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(stream = checked)) } })
            Text("流式输出")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = config.useThinking, onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(useThinking = checked)) } })
            Text("启用思维链")
        }
        if (config.useThinking) {
            NumberField("思维预算", config.thinkingBudget?.toString() ?: "") { number ->
                onUpdate(id) { draft -> draft.copy(config = config.copy(thinkingBudget = number.toIntOrNull())) }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PromptPresetPane(
    draft: SettingsDraft,
    onUpdatePrompt: (Int, (PromptPresetDraft) -> PromptPresetDraft) -> Unit,
    onAddRegex: (Int) -> Unit,
    onRemoveRegex: (Int, Int) -> Unit,
    onSelectPrompt: (Int) -> Unit,
    onCreatePrompt: () -> Unit,
    onSavePromptAs: (Int) -> Unit,
    onDeletePrompt: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedButton(onClick = onCreatePrompt, modifier = Modifier.fillMaxWidth()) {
                Text("新建提示词")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(draft.promptPresets, key = { it.id }) { preset ->
                    val isSelected = preset.id == draft.selectedPromptId
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectPrompt(preset.id) },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(preset.name, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("ID: ${preset.id}", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        val current = draft.promptPresets.firstOrNull { it.id == draft.selectedPromptId }
        if (current != null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = current.name, onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(name = value) } }, label = { Text("预设名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = current.systemPrompt, onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(systemPrompt = value) } }, label = { Text("系统提示") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = current.firstUser, onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(firstUser = value) } }, label = { Text("首条用户消息") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = current.firstAssistant, onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(firstAssistant = value) } }, label = { Text("首条助手消息") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = current.messagePrefix, onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(messagePrefix = value) } }, label = { Text("消息前缀") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = current.assistantPrefill, onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(assistantPrefill = value) } }, label = { Text("助手预填充") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("正则替换", fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.regexRules.forEach { rule ->
                        OutlinedCard {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = rule.pattern, onValueChange = { value -> onUpdatePrompt(current.id) { draft -> draft.copy(regexRules = draft.regexRules.map { if (it.id == rule.id) it.copy(pattern = value) else it }) } }, label = { Text("Pattern") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = rule.replacement, onValueChange = { value -> onUpdatePrompt(current.id) { draft -> draft.copy(regexRules = draft.regexRules.map { if (it.id == rule.id) it.copy(replacement = value) else it }) } }, label = { Text("Replacement") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = rule.flags, onValueChange = { value -> onUpdatePrompt(current.id) { draft -> draft.copy(regexRules = draft.regexRules.map { if (it.id == rule.id) it.copy(flags = value) else it }) } }, label = { Text("Flags") }, modifier = Modifier.fillMaxWidth())
                                TextButton(onClick = { onRemoveRegex(current.id, rule.id) }) { Text("删除规则", color = Color.Red) }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { onAddRegex(current.id) }) {
                    Text("添加规则")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onSavePromptAs(current.id) }) { Text("另存为新预设") }
                    OutlinedButton(onClick = { onDeletePrompt(current.id) }) { Text("删除预设", color = Color.Red) }
                }
            }
        }
    }
}

@Composable
private fun BackupPane(
    exportJson: String?,
    onExportAll: () -> Unit,
    onImport: (String, ImportMode) -> Unit,
    onClearAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("导出当前数据为 JSON")
        Button(onClick = onExportAll) { Text("导出所有数据") }
        if (exportJson != null) {
            OutlinedTextField(value = exportJson, onValueChange = {}, modifier = Modifier.fillMaxWidth().height(160.dp), readOnly = true)
        }
        Divider()
        Text("导入 JSON 数据")
        var importText by remember { mutableStateOf("") }
        OutlinedTextField(value = importText, onValueChange = { importText = it }, modifier = Modifier.fillMaxWidth().height(160.dp), label = { Text("粘贴备份 JSON") })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onImport(importText, ImportMode.MERGE) }, enabled = importText.isNotBlank()) { Text("合并导入") }
            Button(onClick = { onImport(importText, ImportMode.REPLACE) }, enabled = importText.isNotBlank()) { Text("替换导入") }
        }
        Divider()
        TextButton(onClick = onClearAll) { Text("清空所有数据", color = Color.Red) }
    }
}

private fun typeLabel(type: ModelType): String = when (type) {
    ModelType.OPEN_AI -> "OpenAI"
    ModelType.GEMINI_DIRECT -> "Gemini直连"
    ModelType.GEMINI_PROXY -> "Gemini代理"
}
