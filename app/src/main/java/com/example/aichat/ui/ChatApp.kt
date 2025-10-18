@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalTime::class
)

package com.example.aichat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aichat.data.repository.ImportMode
import com.example.aichat.model.ChatMessage
import com.example.aichat.model.GeminiConfig
import com.example.aichat.model.GeminiProxyConfig
import com.example.aichat.model.MessageRole
import com.example.aichat.model.ModelPreset
import com.example.aichat.model.ModelType
import com.example.aichat.model.OpenAIConfig
import com.example.aichat.model.PromptPreset
import com.example.aichat.ui.theme.AccentBlue
import com.example.aichat.ui.theme.AccentGreen
import com.example.aichat.ui.theme.AccentYellow
import com.example.aichat.ui.theme.DangerRed
import com.example.aichat.util.FormattedContent
import com.example.aichat.util.FormattedSegment
import com.example.aichat.util.SegmentStyle
import com.example.aichat.util.StreamFormatter
import com.example.aichat.viewmodel.ChatViewModel
import com.example.aichat.viewmodel.ModelPresetDraft
import com.example.aichat.viewmodel.PromptPresetDraft
import com.example.aichat.viewmodel.SettingsDraft
import com.example.aichat.viewmodel.changeType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime

@Composable
fun ChatApp(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ChatTopBar(
                onMenu = viewModel::toggleSidebar,
                onSettings = viewModel::openSettings
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0A0E14), Color(0xFF141824))
                    )
                )
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            val isWide = maxWidth >= 820.dp
            val sidebarWidth = if (isWide) 320.dp else maxWidth * 0.85f

            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            ) {
                if (isWide) {
                    Row(Modifier.fillMaxSize()) {
                        Sidebar(
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight(),
                            uiState = uiState,
                            showCloseButton = false,
                            onClose = { viewModel.toggleSidebar() },
                            onNewChat = { presetId, promptId ->
                                viewModel.createSession("新的对话", presetId, promptId)
                            },
                            onSelectSession = viewModel::selectSession,
                            onRename = viewModel::renameSession,
                            onDelete = viewModel::deleteSession
                        )
                        ChatArea(
                            modifier = Modifier.weight(1f),
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
                            onRegenerate = viewModel::regenerateMessage,
                            onStopStreaming = viewModel::stopStreaming
                        )
                    }
                } else {
                    ChatArea(
                        modifier = Modifier.fillMaxSize(),
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
                        onRegenerate = viewModel::regenerateMessage,
                        onStopStreaming = viewModel::stopStreaming
                    )

                    val overlayAlpha by animateFloatAsState(
                        targetValue = if (uiState.isSidebarOpen) 0.6f else 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    )
                    val overlayInteraction = remember { MutableInteractionSource() }
                    if (overlayAlpha > 0.01f) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = overlayAlpha))
                                .clickable(
                                    interactionSource = overlayInteraction,
                                    indication = null
                                ) { viewModel.toggleSidebar() }
                        )
                    }

                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.TopStart),
                        visible = uiState.isSidebarOpen,
                        enter = slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
                        exit = slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
                    ) {
                        Sidebar(
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight(),
                            uiState = uiState,
                            showCloseButton = true,
                            onClose = { viewModel.toggleSidebar() },
                            onNewChat = { presetId, promptId ->
                                viewModel.createSession("新的对话", presetId, promptId)
                                viewModel.toggleSidebar()
                            },
                            onSelectSession = {
                                viewModel.selectSession(it)
                                viewModel.toggleSidebar()
                            },
                            onRename = viewModel::renameSession,
                            onDelete = viewModel::deleteSession
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    ErrorToast(
                        message = message,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 80.dp, end = 20.dp),
                        onDismiss = viewModel::dismissError
                    )
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
}

@Composable
private fun SettingsNavButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(AccentBlue, Color(0xFF3B82F6)))
                } else {
                    SolidColor(Color(0x22151921))
                }
            )
            .border(1.dp, if (selected) Color.Transparent else Color(0xFF1F2937), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else Color(0xFF9CA3AF),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsCard(
    title: String? = null,
    accent: Color = AccentBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33151921))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            Text(title, color = accent, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun SettingsField(
    label: String,
    alignTop: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = verticalAlignment
    ) {
        Text(
            label,
            color = Color(0xFF9CA3AF),
            fontSize = 13.sp,
            modifier = Modifier
                .width(120.dp)
                .padding(top = if (alignTop) 6.dp else 0.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun ThinkingPanel(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x22151921), shape)
            .border(1.dp, Color(0xFF1F2937), shape)
            .clickable { expanded = !expanded }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💭 思维链", color = AccentYellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (expanded) "收起 · ${text.length}字" else "展开 · ${text.length}字",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = fadeOut() + shrinkVertically()) {
            Text(text, fontSize = 13.sp, color = Color(0xFFE6E8EB))
        }
    }
}

@Composable
private fun ChatTopBar(onMenu: () -> Unit, onSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF151921), Color(0xFF0A0E14))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GradientTitle()
                PillLabel("v9.3⚡")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowButton(text = "对话列表", compact = true, onClick = onMenu)
                GlowButton(text = "设置", compact = true, onClick = onSettings)
            }
        }
    }
}

@Composable
private fun GradientTitle() {
    Text(
        "AI Chat",
        style = MaterialTheme.typography.titleLarge.copy(
            brush = Brush.linearGradient(listOf(AccentBlue, AccentYellow))
        ),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PillLabel(text: String) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFF151921))
            .border(1.dp, Color(0xFF1F2937), shape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private enum class ButtonTone { Default, Primary, Danger }

@Composable
private fun GlowButton(
    text: String,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Default,
    compact: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable (() -> Unit))? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    val background = when (tone) {
        ButtonTone.Default -> Brush.linearGradient(listOf(Color(0xFF151921), Color(0xFF11151A)))
        ButtonTone.Primary -> Brush.linearGradient(listOf(AccentBlue, Color(0xFF3B82F6)))
        ButtonTone.Danger -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
    }
    val borderColor = when (tone) {
        ButtonTone.Default -> Color(0xFF1F2937)
        else -> Color.Transparent
    }
    val contentColor = when (tone) {
        ButtonTone.Default -> Color(0xFFE6E8EB)
        else -> Color.White
    }
    val padding = if (compact) PaddingValues(horizontal = 14.dp, vertical = 6.dp) else PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    val highlightColor = when (tone) {
        ButtonTone.Default -> AccentBlue
        ButtonTone.Primary -> AccentBlue
        ButtonTone.Danger -> DangerRed
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )
    val glowElevation by animateDpAsState(
        targetValue = if (isPressed && enabled) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    )
    val baseModifier = modifier.alpha(if (enabled) 1f else 0.5f)
    val decoratedModifier = Modifier
        .then(
            if (glowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = glowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = highlightColor.copy(alpha = 0.35f),
                    spotColor = highlightColor.copy(alpha = 0.35f)
                )
            } else {
                Modifier
            }
        )
        .then(baseModifier)
        .background(background, shape)
        .then(
            if (borderColor == Color.Transparent) Modifier else Modifier.border(1.dp, borderColor, shape)
        )
        .clip(shape)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
        .padding(padding)
    Box(decoratedModifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            Text(
                text = text,
                color = contentColor,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ErrorToast(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    var visible by remember(message) { mutableStateOf(true) }

    LaunchedEffect(message) {
        visible = true
        delay(3200)
        visible = false
        delay(220)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally(
            initialOffsetX = { it / 2 },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
        exit = slideOutHorizontally(
            targetOffsetX = { it / 2 },
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(DangerRed)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun aiTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF151921),
    unfocusedContainerColor = Color(0xFF151921),
    focusedTextColor = Color(0xFFE6E8EB),
    unfocusedTextColor = Color(0xFFE6E8EB),
    cursorColor = AccentBlue,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color(0xFF1F2937),
    focusedPlaceholderColor = Color(0xFF6B7280),
    unfocusedPlaceholderColor = Color(0xFF6B7280)
)

private val TextFieldShape = RoundedCornerShape(16.dp)

@Composable
private fun Sidebar(
    modifier: Modifier = Modifier,
    uiState: ChatViewModel.ChatUiState,
    showCloseButton: Boolean,
    onClose: () -> Unit,
    onNewChat: (Int, Int) -> Unit,
    onSelectSession: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val enabledPresets = uiState.modelPresets.filter { it.enabled }
    var selectedPresetId by remember(uiState.modelPresets) {
        mutableIntStateOf(enabledPresets.firstOrNull()?.id ?: -1)
    }
    val activePromptId = uiState.activePromptPreset?.id ?: uiState.promptPresets.firstOrNull()?.id

    Column(
        modifier
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF151921), Color(0xFF0B0F16))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("对话列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (showCloseButton) {
                GlowButton(text = "关闭", compact = true, onClick = onClose)
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x33151921))
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (enabledPresets.isEmpty()) {
                Text("暂无启用的模型", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            } else {
                ModelSelectorBar(
                    presets = enabledPresets,
                    selectedId = selectedPresetId.takeIf { it != -1 },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { preset -> selectedPresetId = preset.id }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                GlowButton(
                    text = "新建",
                    tone = ButtonTone.Primary,
                    compact = true,
                    enabled = selectedPresetId != -1 && activePromptId != null,
                    onClick = {
                        val presetId = selectedPresetId
                        val promptId = activePromptId
                        if (presetId != -1 && promptId != null) {
                            onNewChat(presetId, promptId)
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(uiState.sessions, key = { it.id }) { session ->
                val isActive = session.id == uiState.currentSessionId
                val modelName = uiState.modelPresets.find { it.id == session.presetId }?.displayName ?: "未知"
                val promptName = uiState.promptPresets.find { it.id == session.promptPresetId }?.name ?: "默认"
                SessionCard(
                    session = session,
                    isActive = isActive,
                    modelName = modelName,
                    promptName = promptName,
                    onOpen = { onSelectSession(session.id) },
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
    modelName: String,
    promptName: String,
    onOpen: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(session.name) }
    val shape = RoundedCornerShape(18.dp)
    val background = if (isActive) {
        Brush.linearGradient(listOf(Color(0x332563EB), Color(0x333B82F6)))
    } else {
        Brush.linearGradient(listOf(Color(0x22151921), Color(0x2210141D)))
    }
    val borderColor = if (isActive) AccentBlue else Color(0xFF1F2937)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                session.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22151921))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showRename = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✎", fontSize = 11.sp, color = Color(0xFF9CA3AF))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x33151921))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    modelName,
                    color = AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "提示词: $promptName",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlowButton(text = "打开", compact = true, onClick = onOpen)
            GlowButton(
                text = "删除",
                compact = true,
                tone = ButtonTone.Danger,
                onClick = { onDelete(session.id) }
            )
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
                    label = { Text("名称") },
                    colors = aiTextFieldColors(),
                    shape = TextFieldShape,
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

@Composable
private fun ChatArea(
    modifier: Modifier = Modifier,
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
    onRegenerate: (Long) -> Unit,
    onStopStreaming: () -> Unit
) {
    val density = LocalDensity.current
    var composerHeightPx by remember { mutableIntStateOf(0) }
    val bottomPadding = with(density) { composerHeightPx.toDp() }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF10141E), Color(0xFF090D15))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
    ) {
        MessagesList(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding + 16.dp),
            messages = uiState.messages,
            streamingMessageId = uiState.streamingMessageId,
            streamingContent = uiState.streamingContent,
            thinking = uiState.streamingThinking,
            editingMessageId = uiState.editingMessageId,
            editingDraft = uiState.editingDraft,
            actionsEnabled = !uiState.isStreaming && uiState.editingMessageId == null,
            assistantModelName = uiState.activeModelPreset?.displayName,
            onStartEdit = onStartEdit,
            onEditChange = onEditChange,
            onSaveEdit = onSaveEdit,
            onCancelEdit = onCancelEdit,
            onDelete = onDelete,
            onRegenerate = onRegenerate
        )
        Composer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { composerHeightPx = it.size.height },
            uiState = uiState,
            onComposerChange = onComposerChange,
            onSend = onSend,
            onModelSelect = onModelSelect,
            onPromptSelect = onPromptSelect,
            onStop = onStopStreaming
        )
    }
}

@Composable
private fun MessagesList(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    streamingMessageId: Long?,
    streamingContent: FormattedContent,
    thinking: String,
    editingMessageId: Long?,
    editingDraft: String,
    actionsEnabled: Boolean,
    assistantModelName: String?,
    onStartEdit: (Long) -> Unit,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (Long) -> Unit,
    onRegenerate: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val isNearBottom by remember {
        derivedStateOf {
            if (messages.isEmpty()) {
                true
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= messages.lastIndex - 1
            }
        }
    }
    LaunchedEffect(messages.size, streamingMessageId, streamingContent, isNearBottom) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val isStreaming = message.id == streamingMessageId
            val draft = if (message.id == editingMessageId) editingDraft else null
            val regen = if (message.role == MessageRole.ASSISTANT) { { onRegenerate(message.id) } } else null
            val transitionState = remember(message.id) {
                MutableTransitionState(false).apply { targetState = true }
            }
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
                exit = slideOutVertically(
                    targetOffsetY = { -it / 4 },
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
            ) {
                MessageCard(
                    modifier = Modifier,
                    index = index,
                    message = message,
                    isStreaming = isStreaming,
                    streamingContent = if (isStreaming) streamingContent else null,
                    thinking = if (isStreaming) thinking else message.thinking,
                    editingDraft = draft,
                    actionsEnabled = actionsEnabled,
                    modelName = if (message.role == MessageRole.ASSISTANT) assistantModelName else null,
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
}

@Composable
private fun MessageCard(
    modifier: Modifier = Modifier,
    index: Int,
    message: ChatMessage,
    isStreaming: Boolean,
    streamingContent: FormattedContent?,
    thinking: String,
    editingDraft: String?,
    actionsEnabled: Boolean,
    modelName: String?,
    onStartEdit: () -> Unit,
    onEditChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: (() -> Unit)?
) {
    val background = when (message.role) {
        MessageRole.USER -> Brush.linearGradient(listOf(Color(0x33151F2D), Color(0x2210141D)))
        MessageRole.ASSISTANT -> Brush.linearGradient(listOf(Color(0x33213643), Color(0x22101824)))
    }
    val accent = when (message.role) {
        MessageRole.USER -> AccentBlue
        MessageRole.ASSISTANT -> AccentGreen
    }
    val staticFormatted = remember(message.id, message.content) { StreamFormatter.formatAll(message.content) }
    val displayContent = if (isStreaming && streamingContent != null) streamingContent else staticFormatted
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background, shape)
            .border(1.dp, Color(0xFF1F2937), shape)
            .drawBehind {
                drawRect(
                    color = accent,
                    topLeft = Offset.Zero,
                    size = Size(6.dp.toPx(), size.height)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0x3310151F),
                    shape.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        message.role.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFFE6E8EB)
                    )
                    if (message.role == MessageRole.ASSISTANT && !modelName.isNullOrBlank()) {
                        ModelBadge(modelName)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x33151921))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("#${index + 1}", fontSize = 12.sp, color = Color(0xFF9CA3AF))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (editingDraft != null) {
                OutlinedTextField(
                    value = editingDraft,
                    onValueChange = onEditChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 12,
                    colors = aiTextFieldColors(),
                    shape = TextFieldShape,
                )
            } else if (displayContent.isBlank()) {
                Text("(空内容)", color = Color(0xFF9CA3AF))
            } else {
                FormattedMessageBody(content = displayContent, showCursor = isStreaming)
            }
            if (thinking.isNotBlank()) {
                ThinkingPanel(thinking)
            }
            if (isStreaming) {
                Text("生成中…", color = AccentBlue, fontSize = 12.sp)
            }
        }
        Divider(color = Color(0x331F2937))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0x22101921),
                    shape.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (editingDraft != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlowButton(text = "取消", compact = true, onClick = onCancelEdit)
                    Spacer(Modifier.width(10.dp))
                    GlowButton(
                        text = "保存",
                        compact = true,
                        tone = ButtonTone.Primary,
                        enabled = editingDraft.isNotBlank(),
                        onClick = onSaveEdit
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlowButton(text = "编辑", compact = true, enabled = actionsEnabled, onClick = onStartEdit)
                    onRegenerate?.let { regen ->
                        GlowButton(text = "重新生成", compact = true, enabled = actionsEnabled, onClick = regen)
                    }
                    GlowButton(
                        text = "删除",
                        compact = true,
                        tone = ButtonTone.Danger,
                        enabled = actionsEnabled,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelBadge(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x3360A5FA))
            .border(1.dp, AccentBlue, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(name, color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
    Text(
        annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE6E8EB))
    )
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
    modifier: Modifier = Modifier,
    uiState: ChatViewModel.ChatUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onModelSelect: (ModelPreset) -> Unit,
    onPromptSelect: (PromptPreset) -> Unit,
    onStop: () -> Unit
) {
    val composerEnabled = uiState.editingMessageId == null && !uiState.isStreaming
    val isStreaming = uiState.isStreaming
    val buttonEnabled = if (isStreaming) {
        true
    } else {
        composerEnabled && uiState.composerText.isNotBlank()
    }
    val buttonTone = if (isStreaming) ButtonTone.Danger else ButtonTone.Primary
    val buttonText = if (isStreaming) "停止" else "发送"
    val buttonAction = if (isStreaming) onStop else onSend
    val enabledPresets = uiState.modelPresets.filter { it.enabled }
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF151921), Color(0xFF10141E))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .imePadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (enabledPresets.size > 1) {
            ModelSelectorBar(
                presets = enabledPresets,
                selectedId = uiState.activeModelPreset?.id,
                modifier = Modifier.fillMaxWidth(),
                onSelect = onModelSelect
            )
        }
        if (uiState.promptPresets.size > 1) {
            PromptSelectorChips(
                prompts = uiState.promptPresets,
                selectedId = uiState.activePromptPreset?.id,
                onSelect = onPromptSelect
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = uiState.composerText,
                onValueChange = onComposerChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息后点击右侧“发送”", color = Color(0xFF9CA3AF)) },
                maxLines = 6,
                minLines = 3,
                enabled = composerEnabled,
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
            GlowButton(
                text = buttonText,
                tone = buttonTone,
                enabled = buttonEnabled,
                leading = if (isStreaming) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                } else null,
                onClick = buttonAction
            )
        }
    }
}

@Composable
private fun ModelSelectorBar(
    presets: List<ModelPreset>,
    selectedId: Int?,
    modifier: Modifier = Modifier,
    onSelect: (ModelPreset) -> Unit
) {
    if (presets.isEmpty()) {
        Text("暂无启用的模型", color = Color(0xFF9CA3AF), fontSize = 12.sp)
        return
    }
    if (presets.size <= 1) {
        return
    }
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33151921))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            val isSelected = preset.id == selectedId
            val shape = RoundedCornerShape(12.dp)
            val background = if (isSelected) {
                Brush.linearGradient(listOf(AccentBlue, Color(0xFF3B82F6)))
            } else {
                Brush.linearGradient(listOf(Color(0xFF151921), Color(0xFF11151A)))
            }
            val borderColor = if (isSelected) Color.Transparent else Color(0xFF1F2937)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(background, shape)
                    .border(1.dp, borderColor, shape)
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    preset.displayName,
                    color = if (isSelected) Color.White else Color(0xFFCBD5F5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PromptSelectorChips(
    prompts: List<PromptPreset>,
    selectedId: Int?,
    onSelect: (PromptPreset) -> Unit
) {
    if (prompts.isEmpty()) {
        Text("暂无提示词", color = Color(0xFF9CA3AF), fontSize = 12.sp)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        prompts.forEach { preset ->
            val isSelected = preset.id == selectedId
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(listOf(AccentBlue, Color(0xFF3B82F6)))
                        } else {
                            SolidColor(Color(0x33151921))
                        }
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else Color(0xFF1F2937),
                        shape
                    )
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    preset.name,
                    color = if (isSelected) Color.White else Color(0xFFE6E8EB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private enum class SettingsTab { MODELS, PROMPTS, BACKUP }

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
    var selectedTab by remember { mutableStateOf(SettingsTab.MODELS) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF151921), Color(0xFF10141E))
                        )
                    )
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(20.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    GlowButton(text = "关闭", compact = true, onClick = onDismiss)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier.width(200.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsNavButton("模型预设", selected = selectedTab == SettingsTab.MODELS) {
                            selectedTab = SettingsTab.MODELS
                        }
                        SettingsNavButton("提示词", selected = selectedTab == SettingsTab.PROMPTS) {
                            selectedTab = SettingsTab.PROMPTS
                        }
                        SettingsNavButton("备份", selected = selectedTab == SettingsTab.BACKUP) {
                            selectedTab = SettingsTab.BACKUP
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0x331F2937))
                    )
                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x22151921))
                            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 8 },
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    )
                                ) togetherWith (
                                fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(
                                        targetOffsetY = { -it / 10 },
                                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                                    )
                                )
                        }
                    ) { tab ->
                        when (tab) {
                            SettingsTab.MODELS -> ModelPresetPane(draft.modelPresets, onUpdateModel)
                            SettingsTab.PROMPTS -> PromptPresetPane(
                                draft = draft,
                                onUpdatePrompt = onUpdatePrompt,
                                onAddRegex = onAddRegex,
                                onRemoveRegex = onRemoveRegex,
                                onSelectPrompt = onSelectPrompt,
                                onCreatePrompt = onCreatePrompt,
                                onSavePromptAs = onSavePromptAs,
                                onDeletePrompt = onDeletePrompt
                            )
                            SettingsTab.BACKUP -> BackupPane(exportJson = exportJson, onExportAll = onExportAll, onImport = onImport, onClearAll = onClearAll)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlowButton(text = "取消", compact = true, onClick = onDismiss)
                    Spacer(Modifier.width(12.dp))
                    GlowButton(text = "应用", compact = true, tone = ButtonTone.Primary, onClick = onApply)
                }
            }
        }
    }
}

@Composable
private fun ModelPresetPane(presets: List<ModelPresetDraft>, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        presets.forEach { preset ->
            SettingsCard(title = "预设${preset.id}") {
                SettingsField("启用") {
                    Switch(
                        checked = preset.enabled,
                        onCheckedChange = { checked -> onUpdate(preset.id) { it.copy(enabled = checked) } }
                    )
                }
                SettingsField("显示名称") {
                    OutlinedTextField(
                        value = preset.displayName,
                        onValueChange = { value -> onUpdate(preset.id) { it.copy(displayName = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = aiTextFieldColors(),
                        shape = TextFieldShape,
                    )
                }
                SettingsField("模型类型") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModelType.values().forEach { type ->
                            GlowButton(
                                text = typeLabel(type),
                                compact = true,
                                tone = if (preset.type == type) ButtonTone.Primary else ButtonTone.Default,
                                onClick = { onUpdate(preset.id) { it.changeType(type) } }
                            )
                        }
                    }
                }
                when (val config = preset.config) {
                    is OpenAIConfig -> OpenAIConfigEditor(preset.id, config, onUpdate)
                    is GeminiConfig -> GeminiConfigEditor(preset.id, config, onUpdate)
                    is GeminiProxyConfig -> GeminiProxyConfigEditor(preset.id, config, onUpdate)
                }
            }
        }
    }
}

@Composable
private fun OpenAIConfigEditor(id: Int, config: OpenAIConfig, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsField("Base URL") {
            OutlinedTextField(
                value = config.base,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(base = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("API Key") {
            OutlinedTextField(
                value = config.key,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(key = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("模型") {
            OutlinedTextField(
                value = config.model,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(model = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Temperature") {
            OutlinedTextField(
                value = config.temperature?.toString().orEmpty(),
                onValueChange = { number ->
                    val parsed = number.toDoubleOrNull()
                    onUpdate(id) { draft -> draft.copy(config = config.copy(temperature = parsed)) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Top P") {
            OutlinedTextField(
                value = config.topP?.toString().orEmpty(),
                onValueChange = { number ->
                    val parsed = number.toDoubleOrNull()
                    onUpdate(id) { draft -> draft.copy(config = config.copy(topP = parsed)) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Max Tokens") {
            OutlinedTextField(
                value = config.maxTokens?.toString().orEmpty(),
                onValueChange = { number ->
                    val parsed = number.toIntOrNull()
                    onUpdate(id) { draft -> draft.copy(config = config.copy(maxTokens = parsed)) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("流式输出") {
            Switch(
                checked = config.stream,
                onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(stream = checked)) } }
            )
        }
        SettingsField("启用思维链") {
            Switch(
                checked = config.useThinking,
                onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(useThinking = checked)) } }
            )
        }
        if (config.useThinking) {
            SettingsField("推理强度") {
                OutlinedTextField(
                    value = config.thinkingEffort.orEmpty(),
                    onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(thinkingEffort = value.ifBlank { null })) } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = aiTextFieldColors(),
                    shape = TextFieldShape,
                )
            }
        }
    }
}

@Composable
private fun GeminiConfigEditor(id: Int, config: GeminiConfig, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsField("Base URL") {
            OutlinedTextField(
                value = config.base,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(base = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("API Key") {
            OutlinedTextField(
                value = config.key,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(key = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("模型") {
            OutlinedTextField(
                value = config.model,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(model = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Temperature") {
            OutlinedTextField(
                value = config.temperature?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(temperature = number.toDoubleOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Top P") {
            OutlinedTextField(
                value = config.topP?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(topP = number.toDoubleOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Top K") {
            OutlinedTextField(
                value = config.topK?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(topK = number.toIntOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Max Output Tokens") {
            OutlinedTextField(
                value = config.maxTokens?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(maxTokens = number.toIntOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("流式输出") {
            Switch(
                checked = config.stream,
                onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(stream = checked)) } }
            )
        }
        SettingsField("启用思维链") {
            Switch(
                checked = config.useThinking,
                onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(useThinking = checked)) } }
            )
        }
        if (config.useThinking) {
            SettingsField("思维预算") {
                OutlinedTextField(
                    value = config.thinkingBudget?.toString().orEmpty(),
                    onValueChange = { number ->
                        onUpdate(id) { draft ->
                            draft.copy(config = config.copy(thinkingBudget = number.toIntOrNull()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = aiTextFieldColors(),
                    shape = TextFieldShape,
                )
            }
        }
    }
}

@Composable
private fun GeminiProxyConfigEditor(id: Int, config: GeminiProxyConfig, onUpdate: (Int, (ModelPresetDraft) -> ModelPresetDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsField("代理地址") {
            OutlinedTextField(
                value = config.proxyUrl,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(proxyUrl = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("代理密钥") {
            OutlinedTextField(
                value = config.proxyPass,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(proxyPass = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("模型") {
            OutlinedTextField(
                value = config.model,
                onValueChange = { value -> onUpdate(id) { draft -> draft.copy(config = config.copy(model = value)) } },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Temperature") {
            OutlinedTextField(
                value = config.temperature?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(temperature = number.toDoubleOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Top P") {
            OutlinedTextField(
                value = config.topP?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(topP = number.toDoubleOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Top K") {
            OutlinedTextField(
                value = config.topK?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(topK = number.toIntOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("Max Output Tokens") {
            OutlinedTextField(
                value = config.maxTokens?.toString().orEmpty(),
                onValueChange = { number ->
                    onUpdate(id) { draft ->
                        draft.copy(config = config.copy(maxTokens = number.toIntOrNull()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
        }
        SettingsField("流式输出") {
            Switch(
                checked = config.stream,
                onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(stream = checked)) } }
            )
        }
        SettingsField("启用思维链") {
            Switch(
                checked = config.useThinking,
                onCheckedChange = { checked -> onUpdate(id) { draft -> draft.copy(config = config.copy(useThinking = checked)) } }
            )
        }
        if (config.useThinking) {
            SettingsField("思维预算") {
                OutlinedTextField(
                    value = config.thinkingBudget?.toString().orEmpty(),
                    onValueChange = { number ->
                        onUpdate(id) { draft ->
                            draft.copy(config = config.copy(thinkingBudget = number.toIntOrNull()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = aiTextFieldColors(),
                    shape = TextFieldShape,
                )
            }
        }
    }
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
    val scrollState = rememberScrollState()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(modifier = Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlowButton(
                text = "新建提示词",
                tone = ButtonTone.Primary,
                onClick = onCreatePrompt,
                modifier = Modifier.fillMaxWidth()
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33151921))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(draft.promptPresets, key = { it.id }) { preset ->
                        val isSelected = preset.id == draft.selectedPromptId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.linearGradient(listOf(AccentBlue, Color(0xFF3B82F6)))
                                    } else {
                                        SolidColor(Color(0x22151921))
                                    }
                                )
                                .border(1.dp, if (isSelected) Color.Transparent else Color(0xFF1F2937), RoundedCornerShape(12.dp))
                                .clickable { onSelectPrompt(preset.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(preset.name, fontWeight = FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFE6E8EB))
                                Text("ID: ${preset.id}", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            }
                        }
                    }
                }
            }
        }
        val current = draft.promptPresets.firstOrNull { it.id == draft.selectedPromptId }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (current == null) {
                Text("请选择左侧的提示词预设进行编辑", color = Color(0xFF9CA3AF))
            } else {
                SettingsCard(title = "基础配置") {
                    SettingsField("预设名称") {
                        OutlinedTextField(
                            value = current.name,
                            onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(name = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = aiTextFieldColors(),
                            shape = TextFieldShape,
                        )
                    }
                    SettingsField("系统提示", alignTop = true) {
                        OutlinedTextField(
                            value = current.systemPrompt,
                            onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(systemPrompt = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = aiTextFieldColors(),
                            shape = TextFieldShape,
                        )
                    }
                    SettingsField("首条用户消息", alignTop = true) {
                        OutlinedTextField(
                            value = current.firstUser,
                            onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(firstUser = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = aiTextFieldColors(),
                            shape = TextFieldShape,
                        )
                    }
                    SettingsField("首条助手消息", alignTop = true) {
                        OutlinedTextField(
                            value = current.firstAssistant,
                            onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(firstAssistant = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            colors = aiTextFieldColors(),
                            shape = TextFieldShape,
                        )
                    }
                    SettingsField("消息前缀", alignTop = true) {
                        OutlinedTextField(
                            value = current.messagePrefix,
                            onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(messagePrefix = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = aiTextFieldColors(),
                            shape = TextFieldShape,
                        )
                    }
                    SettingsField("助手预填充", alignTop = true) {
                        OutlinedTextField(
                            value = current.assistantPrefill,
                            onValueChange = { value -> onUpdatePrompt(current.id) { it.copy(assistantPrefill = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = aiTextFieldColors(),
                            shape = TextFieldShape,
                        )
                    }
                }

                SettingsCard(title = "正则替换", accent = AccentYellow) {
                    if (current.regexRules.isEmpty()) {
                        Text("暂无规则", color = Color(0xFF9CA3AF))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            current.regexRules.forEachIndexed { index, rule ->
                                SettingsCard(title = "规则 #${index + 1}", accent = AccentYellow) {
                                    SettingsField("Pattern", alignTop = true) {
                                        OutlinedTextField(
                                            value = rule.pattern,
                                            onValueChange = { value ->
                                                onUpdatePrompt(current.id) { draft ->
                                                    draft.copy(
                                                        regexRules = draft.regexRules.map { currentRule ->
                                                            if (currentRule.id == rule.id) currentRule.copy(pattern = value) else currentRule
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = aiTextFieldColors(),
                                            shape = TextFieldShape,
                                        )
                                    }
                                    SettingsField("Replacement", alignTop = true) {
                                        OutlinedTextField(
                                            value = rule.replacement,
                                            onValueChange = { value ->
                                                onUpdatePrompt(current.id) { draft ->
                                                    draft.copy(
                                                        regexRules = draft.regexRules.map { currentRule ->
                                                            if (currentRule.id == rule.id) currentRule.copy(replacement = value) else currentRule
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = aiTextFieldColors(),
                                            shape = TextFieldShape,
                                        )
                                    }
                                    SettingsField("Flags") {
                                        OutlinedTextField(
                                            value = rule.flags,
                                            onValueChange = { value ->
                                                onUpdatePrompt(current.id) { draft ->
                                                    draft.copy(
                                                        regexRules = draft.regexRules.map { currentRule ->
                                                            if (currentRule.id == rule.id) currentRule.copy(flags = value) else currentRule
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = aiTextFieldColors(),
                                            shape = TextFieldShape,
                                        )
                                    }
                                    GlowButton(
                                        text = "删除规则",
                                        compact = true,
                                        tone = ButtonTone.Danger,
                                        onClick = { onRemoveRegex(current.id, rule.id) }
                                    )
                                }
                            }
                        }
                    }
                    GlowButton(text = "添加规则", compact = true, onClick = { onAddRegex(current.id) })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlowButton(text = "另存为新预设", compact = true, onClick = { onSavePromptAs(current.id) })
                    GlowButton(text = "删除预设", compact = true, tone = ButtonTone.Danger, onClick = { onDeletePrompt(current.id) })
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
    var importText by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard(title = "存储说明", accent = AccentBlue) {
            Text(
                "使用 IndexedDB 存储，容量更大，性能更好。请定期导出备份重要对话。",
                color = Color(0xFFE6E8EB)
            )
        }
        SettingsCard(title = "导出") {
            GlowButton(text = "导出所有数据", tone = ButtonTone.Primary, onClick = onExportAll)
            if (exportJson != null) {
                OutlinedTextField(
                    value = exportJson,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    readOnly = true,
                    colors = aiTextFieldColors(),
                    shape = TextFieldShape,
                )
            }
        }
        SettingsCard(title = "导入") { 
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text("粘贴备份 JSON") },
                colors = aiTextFieldColors(),
                shape = TextFieldShape,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowButton(
                    text = "合并导入",
                    compact = true,
                    enabled = importText.isNotBlank(),
                    onClick = { onImport(importText, ImportMode.MERGE) }
                )
                GlowButton(
                    text = "替换导入",
                    compact = true,
                    tone = ButtonTone.Primary,
                    enabled = importText.isNotBlank(),
                    onClick = { onImport(importText, ImportMode.REPLACE) }
                )
            }
        }
        GlowButton(text = "清空所有数据", tone = ButtonTone.Danger, onClick = onClearAll)
    }
}

private fun typeLabel(type: ModelType): String = when (type) {
    ModelType.OPEN_AI -> "OpenAI"
    ModelType.GEMINI_DIRECT -> "Gemini直连"
    ModelType.GEMINI_PROXY -> "Gemini代理"
}
