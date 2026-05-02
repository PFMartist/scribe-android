package com.scribe.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scribe.app.data.model.MessageRole
import kotlinx.coroutines.delay
import com.scribe.app.data.repository.SkillManager
import com.scribe.app.ui.components.ChatBubble
import com.scribe.app.ui.components.MessageInput
import com.scribe.app.ui.components.SkillSelector
import com.scribe.app.viewmodel.ChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onNewConversation: () -> Unit,
    onLoadConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSkillSelected: (String) -> Unit,
    onDeleteSkill: (String) -> Unit,
    onImportSkill: (String) -> Unit,
    onImportSkillZip: (Uri) -> Unit,
    onToggleReasoning: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRegenerateLastResponse: () -> Unit,
    onBindSkillToConversation: (String) -> Unit,
    skillMetas: List<SkillManager.SkillMeta>,
    modifier: Modifier = Modifier
) {
    var showHistoryDrawer by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var messageToDelete by remember { mutableStateOf<String?>(null) }

    val zipFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportSkillZip(uri)
            showImportDialog = false
        }
    }

    val listState = rememberLazyListState()
    val visibleMessages = uiState.messages.filter { it.role != MessageRole.SYSTEM }
    val lastAssistant = visibleMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
    val lastAssistantHasContent = lastAssistant?.content?.isNotBlank() == true

    // Auto-scroll only when user is at the absolute bottom (canScrollForward == false)
    val isAtBottom by remember {
        derivedStateOf { !listState.canScrollForward }
    }

    // Auto-scroll on new messages
    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty() && isAtBottom) {
            listState.animateScrollToEnd()
        }
    }

    // Auto-scroll during streaming: watch both content and reasoning
    val streamingContent = if (uiState.isStreaming) {
        visibleMessages.lastOrNull { it.role == MessageRole.ASSISTANT }?.let {
            it.content + it.reasoning
        }.orEmpty()
    } else ""
    LaunchedEffect(streamingContent) {
        if (uiState.isStreaming && isAtBottom && visibleMessages.isNotEmpty()) {
            listState.animateScrollToEnd()
        }
    }

    // Auto-scroll when keyboard opens — delayed to let IME animation settle
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && visibleMessages.isNotEmpty()) {
            delay(50)
            listState.animateScrollToEnd()
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入 Skill") },
            text = {
                Column {
                    Text(
                        "粘贴 SKILL.md 的完整内容（YAML frontmatter + Markdown）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("此处贴入 SKILL.md 内容...") }
                    )
                }
            },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = {
                        zipFilePicker.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                    }) {
                        Text("从压缩包导入")
                    }
                    TextButton(onClick = {
                        if (importText.isNotBlank()) {
                            onImportSkill(importText.trim())
                            importText = ""
                            showImportDialog = false
                        }
                    }) {
                        Text("导入文本")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("删除消息") },
            text = { Text("将同时删除该消息及其后续的 AI 回复，确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    messageToDelete?.let { onDeleteMessage(it) }
                    messageToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = rememberDrawerState(initialValue = DrawerValue.Closed).also {
            if (showHistoryDrawer) {
                LaunchedEffect(Unit) { it.open() }
            }
        },
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "对话历史",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()

                if (uiState.conversationIds.isEmpty()) {
                    Text(
                        "暂无历史对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn {
                        items(uiState.conversationIds) { convId ->
                            val isCurrent = convId == uiState.conversationId
                            ListItem(
                                headlineContent = { Text("对话 ${convId.take(8)}...", maxLines = 1) },
                                supportingContent = { Text(if (isCurrent) "当前" else "", color = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable {
                                    onLoadConversation(convId)
                                    showHistoryDrawer = false
                                },
                                colors = if (isCurrent) ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ) else ListItemDefaults.colors(),
                                trailingContent = {
                                    IconButton(onClick = { onDeleteConversation(convId) }) {
                                        Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("自动手记人偶")
                            if (uiState.model.isNotEmpty()) {
                                Text(
                                    "${uiState.provider.name} | ${uiState.model}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showHistoryDrawer = true }) {
                            Icon(Icons.Default.History, "历史")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNewConversation) {
                            Icon(Icons.Default.Add, "新对话")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, "设置")
                        }
                    }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                ) {
                    if (uiState.tokenUsage != null) {
                        Text(
                            uiState.tokenUsage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SkillSelector(
                        skills = skillMetas,
                        currentSkillName = uiState.skillName,
                        onSkillSelected = onSkillSelected,
                        onDeleteSkill = onDeleteSkill,
                        onImportSkill = { showImportDialog = true },
                        onSkillBoundToConversation = onBindSkillToConversation,
                        hasMessages = visibleMessages.isNotEmpty()
                    )

                    HorizontalDivider()

                    if (uiState.streamError != null) {
                        Text(
                            text = "错误: ${uiState.streamError}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }

                    MessageInput(
                        onSend = onSendMessage,
                        enabled = !uiState.isStreaming
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                if (uiState.isStreaming) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (visibleMessages.isEmpty() && !uiState.isStreaming) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "开始新对话",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = visibleMessages,
                            key = { it.id }
                        ) { msg ->
                            ChatBubble(
                                role = msg.role,
                                content = msg.content,
                                reasoning = msg.reasoning,
                                reasoningExpanded = msg.id !in uiState.collapsedReasoningIds,
                                onToggleReasoning = { onToggleReasoning(msg.id) },
                                onDelete = if (msg.role == MessageRole.USER) {
                                    { messageToDelete = msg.id }
                                } else null,
                                onRegenerate = if (msg.role == MessageRole.ASSISTANT
                                    && msg.id == lastAssistant?.id
                                    && (lastAssistantHasContent || msg.incomplete)
                                    && !uiState.isStreaming
                                ) {
                                    { onRegenerateLastResponse() }
                                } else null,
                                incomplete = msg.incomplete
                            )
                        }

                        if (uiState.isStreaming &&
                            visibleMessages.none { it.role == MessageRole.ASSISTANT }
                        ) {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.animateScrollToEnd() {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return
    // First pass: animate to make the last item visible
    animateScrollToItem(total - 1)
    // When the last item is taller than the viewport, push further
    // to align its bottom edge. Use animateScrollToItem again (not scrollToItem)
    // so the overflow portion is also animated, not a sudden jump.
    val info = layoutInfo
    val lastItem = info.visibleItemsInfo.lastOrNull { it.index == total - 1 }
    if (lastItem != null) {
        val overflow = lastItem.offset + lastItem.size - info.viewportSize.height
        if (overflow > 0) {
            animateScrollToItem(total - 1, overflow)
        }
    }
}
