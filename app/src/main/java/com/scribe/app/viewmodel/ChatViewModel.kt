package com.scribe.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scribe.app.data.local.AppDatabase
import com.scribe.app.data.local.SettingsDataStore
import com.scribe.app.data.model.*
import com.scribe.app.data.repository.ChatRepository
import com.scribe.app.data.repository.ConversationExporter
import com.scribe.app.data.repository.SkillManager
import com.scribe.app.data.repository.SkillRepository
import com.scribe.app.network.LLMService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamError: String? = null,
    val tokenUsage: String? = null,
    val conversationId: String = "",
    val conversationIds: List<String> = emptyList(),
    val skillName: String? = null,
    val skillMetas: List<SkillManager.SkillMeta> = emptyList(),
    val provider: Provider = Provider.OPENAI,
    val model: String = "",
    val showReasoning: Boolean = true,
    val aiThinking: Boolean = true,
    val collapsedReasoningIds: Set<String> = emptySet(),
    val scrollToBottomTrigger: Long = 0L,
    val conversationTitles: Map<String, String> = emptyMap(),
    val conversationSummary: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val chatRepo = ChatRepository(db.messageDao(), db.conversationDao())
    private val skillRepo = SkillRepository(application)
    private val settingsStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSettings = SettingsDataStore.AppSettings()
    private var currentSkill: Skill? = null
    private var settingsReady = false
    private var streamingMessageId: String? = null
    private var streamingJob: Job? = null

    init {
        val storedVersion = settingsStore.getBundledSkillsVersion()
        val newVersion = skillRepo.init(storedVersion)
        if (newVersion != storedVersion) {
            settingsStore.setBundledSkillsVersion(newVersion)
        }

        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                currentSettings = settings

                val provider = settings.provider
                val model = when (provider) {
                    Provider.OPENAI -> settings.openaiModel
                    Provider.ANTHROPIC -> settings.anthropicModel
                }

                _uiState.update {
                    it.copy(provider = provider, model = model,
                        showReasoning = settings.showReasoning,
                        aiThinking = settings.aiThinking)
                }

                if (!settingsReady && settings.lastSkillId.isNotBlank()) {
                    loadSkillInternal(settings.lastSkillId)
                }

                if (!settingsReady) {
                    settingsReady = true
                    newConversation()
                }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(conversationIds = chatRepo.getAllConversationIds()) }
        }

        refreshSkillMetas()
        refreshConversationTitles()
    }

    private fun refreshSkillMetas() {
        _uiState.update { it.copy(skillMetas = skillRepo.getSkillMetas()) }
    }

    private fun refreshConversationTitles() {
        viewModelScope.launch {
            _uiState.update { it.copy(conversationTitles = chatRepo.getAllConversationTitles()) }
        }
    }

    fun updateConversationTitle(convId: String, title: String) {
        viewModelScope.launch {
            chatRepo.updateConversationTitle(convId, title)
            refreshConversationTitles()
        }
    }

    fun aiSummarizeTitle(convId: String) {
        if (_uiState.value.isStreaming) return
        viewModelScope.launch {
            val (messages, _) = chatRepo.loadMessages(convId)
            val visible = messages.filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
            if (visible.isEmpty()) return@launch

            val conversationText = visible.joinToString("\n") { msg ->
                "${if (msg.role == MessageRole.USER) "User" else "Assistant"}: ${msg.content}"
            }

            val prompt = listOf(
                mapOf("role" to "user", "content" to "用不超过10个字总结以下对话的主题，只输出标题文本，不要引号或额外解释：\n\n$conversationText")
            )

            val settings = currentSettings
            val baseUrl = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiBaseUrl
                Provider.ANTHROPIC -> settings.anthropicBaseUrl
            }
            val apiKey = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiApiKey
                Provider.ANTHROPIC -> settings.anthropicApiKey
            }
            val model = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiModel
                Provider.ANTHROPIC -> settings.anthropicModel
            }

            if (apiKey.isBlank()) return@launch

            var result = ""
            LLMService.chat(
                messages = prompt,
                provider = _uiState.value.provider,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                aiThinking = false
            ).collect { event ->
                when (event) {
                    is StreamEvent.Content -> result += event.text
                    is StreamEvent.Error -> return@collect
                    is StreamEvent.Done -> {
                        val title = result.trim().take(20)
                        if (title.isNotBlank()) {
                            chatRepo.updateConversationTitle(convId, title)
                            refreshConversationTitles()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun compressContext(convId: String) {
        if (_uiState.value.isStreaming) return
        viewModelScope.launch {
            val (allMessages, _) = chatRepo.loadMessages(convId)
            val visible = allMessages.filter { it.role != MessageRole.SYSTEM }
            val keepCount = 5

            if (visible.size <= keepCount) return@launch

            val toSummarize = visible.dropLast(keepCount)

            val conversationText = toSummarize.joinToString("\n") { msg ->
                "${if (msg.role == MessageRole.USER) "User" else "Assistant"}: ${msg.content}"
            }

            if (conversationText.isBlank()) return@launch

            val prompt = listOf(
                mapOf("role" to "user", "content" to "用简短的文字总结以下对话的关键信息和上下文，保留重要事实、决定和讨论要点：\n\n$conversationText")
            )

            val settings = currentSettings
            val baseUrl = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiBaseUrl
                Provider.ANTHROPIC -> settings.anthropicBaseUrl
            }
            val apiKey = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiApiKey
                Provider.ANTHROPIC -> settings.anthropicApiKey
            }
            val model = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiModel
                Provider.ANTHROPIC -> settings.anthropicModel
            }

            if (apiKey.isBlank()) return@launch

            var result = ""
            LLMService.chat(
                messages = prompt,
                provider = _uiState.value.provider,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                aiThinking = false
            ).collect { event ->
                when (event) {
                    is StreamEvent.Content -> result += event.text
                    is StreamEvent.Error -> return@collect
                    is StreamEvent.Done -> {
                        val summary = result.trim()
                        if (summary.isNotBlank()) {
                            chatRepo.updateConversationSummary(convId, summary)
                            if (convId == _uiState.value.conversationId) {
                                _uiState.update { it.copy(conversationSummary = summary) }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    suspend fun fetchConversationSummary(convId: String): String? {
        return chatRepo.getConversationSummary(convId)
    }

    fun clearConversationSummary(convId: String) {
        viewModelScope.launch {
            chatRepo.clearConversationSummary(convId)
            if (convId == _uiState.value.conversationId) {
                _uiState.update { it.copy(conversationSummary = null) }
            }
        }
    }

    fun newConversation() {
        streamingMessageId = null
        val convId = chatRepo.newConversationId()
        _uiState.update {
            it.copy(
                messages = buildSystemMessage(currentSkill),
                isStreaming = false,
                streamError = null,
                tokenUsage = null,
                conversationId = convId,
                conversationSummary = null
            )
        }
        viewModelScope.launch {
            _uiState.update { it.copy(conversationIds = chatRepo.getAllConversationIds()) }
            refreshConversationTitles()
        }
    }

    fun loadConversation(convId: String) {
        streamingMessageId = null
        viewModelScope.launch {
            val (stored, storedSkillId) = chatRepo.loadMessages(convId)

            // storedSkillId == null: old record (pre-migration), keep current skill
            // storedSkillId == "":   explicitly no skill, clear
            // storedSkillId == "x":  load that skill
            if (storedSkillId != null && storedSkillId != currentSkill?.id) {
                if (storedSkillId.isNotEmpty()) {
                    val skill = skillRepo.loadSkill(storedSkillId)
                    if (skill != null) {
                        currentSkill = skill
                        _uiState.update { it.copy(skillName = skill.name) }
                        settingsStore.setLastSkillId(storedSkillId)
                    } else {
                        currentSkill = null
                        _uiState.update { it.copy(skillName = null) }
                    }
                } else {
                    // empty string = explicitly saved without skill
                    currentSkill = null
                    _uiState.update { it.copy(skillName = null) }
                }
            }

            val msgs = buildSystemMessage(currentSkill) + stored
            val summary = chatRepo.getConversationSummary(convId)
            _uiState.update {
                it.copy(
                    messages = msgs,
                    conversationId = convId,
                    isStreaming = false,
                    streamError = null,
                    tokenUsage = null,
                    scrollToBottomTrigger = it.scrollToBottomTrigger + 1,
                    conversationSummary = summary
                )
            }
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            chatRepo.deleteConversation(convId)
            val ids = chatRepo.getAllConversationIds()
            val titles = chatRepo.getAllConversationTitles()
            _uiState.update { it.copy(conversationIds = ids, conversationTitles = titles) }
            if (convId == _uiState.value.conversationId) {
                newConversation()
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isStreaming) return

        val userMsg = ChatMessage(role = MessageRole.USER, content = content)
        val assistantId = java.util.UUID.randomUUID().toString()
        streamingMessageId = assistantId

        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            msgs.add(userMsg)
            msgs.add(ChatMessage(id = assistantId, role = MessageRole.ASSISTANT, content = ""))
            state.copy(
                messages = msgs,
                isStreaming = true,
                streamError = null,
                tokenUsage = null
            )
        }

        performChatStreaming(assistantId)
    }

    fun regenerateLastResponse() {
        val state = _uiState.value
        if (state.isStreaming) return

        val lastAssistantIdx = state.messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (lastAssistantIdx < 0) return

        val lastAssistant = state.messages[lastAssistantIdx]
        // Use partial content as prefill when continuing an interrupted stream
        val prefillContent = if (lastAssistant.incomplete) lastAssistant.content else ""

        val assistantId = lastAssistant.id
        streamingMessageId = assistantId

        _uiState.update { currentState ->
            val msgs = currentState.messages.toMutableList()
            msgs[lastAssistantIdx] = msgs[lastAssistantIdx].copy(
                content = prefillContent,
                reasoning = "",
                incomplete = false
            )
            currentState.copy(
                messages = msgs,
                isStreaming = true,
                streamError = null,
                tokenUsage = null
            )
        }

        performChatStreaming(assistantId)
    }

    fun deleteMessage(messageId: String) {
        if (_uiState.value.isStreaming) return

        _uiState.update { currentState ->
            val msgs = currentState.messages.toMutableList()
            val idx = msgs.indexOfFirst { it.id == messageId }
            if (idx < 0) return@update currentState
            if (msgs[idx].role != MessageRole.USER) return@update currentState

            msgs.removeAt(idx)
            if (idx < msgs.size && msgs[idx].role == MessageRole.ASSISTANT) {
                msgs.removeAt(idx)
            }

            currentState.copy(messages = msgs)
        }

        viewModelScope.launch {
            val finalState = _uiState.value
            val savableMessages = finalState.messages
                .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
            chatRepo.saveMessages(finalState.conversationId, savableMessages, currentSkill?.id)
            _uiState.update { it.copy(conversationIds = chatRepo.getAllConversationIds()) }
        }
    }

    private fun performChatStreaming(assistantId: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val settings = currentSettings

            val baseUrl = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiBaseUrl
                Provider.ANTHROPIC -> settings.anthropicBaseUrl
            }
            val apiKey = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiApiKey
                Provider.ANTHROPIC -> settings.anthropicApiKey
            }
            val model = when (_uiState.value.provider) {
                Provider.OPENAI -> settings.openaiModel
                Provider.ANTHROPIC -> settings.anthropicModel
            }

            if (apiKey.isBlank()) {
                _uiState.update { it.copy(isStreaming = false, streamError = "未设置 API Key") }
                return@launch
            }

            val state = _uiState.value
            val allMessages = state.messages
            val summary = state.conversationSummary
            val apiMessages = if (summary != null) {
                val visible = allMessages.filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
                val recent = visible.takeLast(5)
                val skillMsg = allMessages.firstOrNull { it.role == MessageRole.SYSTEM }
                buildList {
                    if (skillMsg != null) {
                        add(mapOf("role" to "system", "content" to skillMsg.content))
                    }
                    add(mapOf("role" to "system", "content" to "📝 历史对话摘要:\n$summary"))
                    addAll(recent.map { mapOf("role" to it.role.name.lowercase(), "content" to it.content) })
                }
            } else {
                allMessages
                    .filter { it.content.isNotBlank() || it.role == MessageRole.SYSTEM }
                    .map { mapOf("role" to it.role.name.lowercase(), "content" to it.content) }
            }

            // Start with prefill content when continuing an interrupted stream
            val prefillStart = allMessages.find { it.id == assistantId }?.content ?: ""
            var fullResponse = prefillStart
            var fullReasoning = ""
            var errorOccurred = false

            LLMService.chat(
                messages = apiMessages,
                provider = _uiState.value.provider,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                aiThinking = _uiState.value.aiThinking
            ).catch { e ->
                val detail = when {
                    e.message?.contains("Unable to resolve host") == true -> "DNS解析失败"
                    e.message?.contains("timed out") == true -> "连接超时"
                    e.message?.contains("NetworkOnMainThread") == true -> "网络线程错误"
                    e.message?.contains("connect") == true -> "连接失败: ${e.message}"
                    else -> e.message ?: "未知错误"
                }
                _uiState.update { it.copy(isStreaming = false, streamError = "网络错误: $detail") }
            }.collect { event ->
                when (event) {
                    is StreamEvent.Reasoning -> {
                        if (_uiState.value.showReasoning) {
                            fullReasoning += event.text
                            _uiState.update { currentState ->
                                val msgs = currentState.messages.toMutableList()
                                val idx = msgs.indexOfLast { it.id == assistantId }
                                if (idx >= 0) {
                                    msgs[idx] = msgs[idx].copy(reasoning = fullReasoning)
                                }
                                currentState.copy(messages = msgs)
                            }
                        }
                    }
                    is StreamEvent.Content -> {
                        fullResponse += event.text
                        _uiState.update { currentState ->
                            val msgs = currentState.messages.toMutableList()
                            val idx = msgs.indexOfLast { it.id == assistantId }
                            if (idx >= 0) {
                                msgs[idx] = msgs[idx].copy(content = fullResponse)
                            }
                            currentState.copy(messages = msgs)
                        }
                    }
                    is StreamEvent.Usage -> {
                        _uiState.update {
                            it.copy(tokenUsage = "Prompt: ${event.promptTokens} | Completion: ${event.completionTokens}")
                        }
                    }
                    is StreamEvent.Error -> {
                        errorOccurred = true
                        _uiState.update { it.copy(isStreaming = false, streamError = event.message) }
                    }
                    is StreamEvent.Done -> {
                        _uiState.update { it.copy(isStreaming = false) }
                    }
                }
            }

            if (!errorOccurred) {
                val finalState = _uiState.value
                val savableMessages = finalState.messages
                    .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
                chatRepo.saveMessages(finalState.conversationId, savableMessages, currentSkill?.id)
                _uiState.update { it.copy(conversationIds = chatRepo.getAllConversationIds()) }
            } else {
                _uiState.update { currentState ->
                    val msgs = currentState.messages.toMutableList()
                    msgs.removeAll { it.id == assistantId && it.content.isBlank() }
                    currentState.copy(messages = msgs)
                }
            }
        }
    }

    fun loadSkill(skillId: String) {
        if (skillId.isBlank()) {
            currentSkill = null
            _uiState.update { it.copy(skillName = null) }
            viewModelScope.launch { settingsStore.setLastSkillId("") }
            newConversation()
            return
        }

        loadSkillInternal(skillId)
        viewModelScope.launch { settingsStore.setLastSkillId(skillId) }
        newConversation()
    }

    fun bindSkillToConversation(skillId: String) {
        if (skillId.isBlank()) {
            currentSkill = null
            _uiState.update { it.copy(skillName = null) }
            viewModelScope.launch {
                settingsStore.setLastSkillId("")
                chatRepo.updateSkillId(_uiState.value.conversationId, "")
            }
        } else {
            val skill = skillRepo.loadSkill(skillId)
            if (skill != null) {
                currentSkill = skill
                _uiState.update { it.copy(skillName = skill.name) }
                viewModelScope.launch {
                    settingsStore.setLastSkillId(skillId)
                    chatRepo.updateSkillId(_uiState.value.conversationId, skillId)
                }
            }
        }

        val state = _uiState.value
        val stored = state.messages.filter { it.role != MessageRole.SYSTEM }
        val msgs = buildSystemMessage(currentSkill) + stored
        _uiState.update { it.copy(messages = msgs) }
    }

    fun deleteSkill(skillId: String) {
        skillRepo.deleteSkill(skillId)
        refreshSkillMetas()
        if (currentSkill?.id == skillId) {
            currentSkill = null
            _uiState.update { it.copy(skillName = null) }
            viewModelScope.launch { settingsStore.setLastSkillId("") }
            newConversation()
        }
    }

    fun importSkill(content: String): Boolean {
        val result = skillRepo.importSkill(content) != null
        if (result) refreshSkillMetas()
        return result
    }

    fun importSkillFromZip(uri: android.net.Uri): Boolean {
        val result = skillRepo.importSkillFromZip(uri) != null
        if (result) refreshSkillMetas()
        return result
    }

    private fun loadSkillInternal(skillId: String) {
        val skill = skillRepo.loadSkill(skillId)
        if (skill != null) {
            currentSkill = skill
            _uiState.update { it.copy(skillName = skill.name) }
        }
    }

    fun toggleReasoningCollapse(messageId: String) {
        _uiState.update { state ->
            val set = state.collapsedReasoningIds.toMutableSet()
            if (set.contains(messageId)) set.remove(messageId) else set.add(messageId)
            state.copy(collapsedReasoningIds = set)
        }
    }

    fun stopGeneration() {
        val job = streamingJob ?: return
        if (!job.isActive) return

        val state = _uiState.value
        val streamingId = streamingMessageId
        val partial = state.messages
            .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
            .map { msg ->
                if (streamingId != null && msg.id == streamingId) msg.copy(incomplete = true)
                else msg
            }

        viewModelScope.launch {
            chatRepo.saveMessages(state.conversationId, partial, currentSkill?.id)
            _uiState.update { it.copy(conversationIds = chatRepo.getAllConversationIds()) }
        }

        job.cancel()
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val idx = msgs.indexOfLast { it.id == streamingId }
            if (idx >= 0) {
                msgs[idx] = msgs[idx].copy(incomplete = true)
            }
            state.copy(messages = msgs, isStreaming = false)
        }
    }

    fun exportConversation(convId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val exporter = ConversationExporter(chatRepo)
                val json = exporter.exportToJson(convId)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(streamError = "导出失败: ${e.message}") }
            }
        }
    }

    fun importConversation(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.reader().readText()
                } ?: throw Exception("无法读取文件")
                val exporter = ConversationExporter(chatRepo)
                val (convId, title, messages) = exporter.importFromJson(json) ?: throw Exception("文件格式错误")
                chatRepo.importConversation(convId, title, messages)
                refreshConversationTitles()
                _uiState.update {
                    it.copy(conversationIds = chatRepo.getAllConversationIds())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(streamError = "导入失败: ${e.message}") }
            }
        }
    }

    fun handleBackground() {
        if (currentSettings.continueInBackground) return
        val job = streamingJob ?: return
        if (!job.isActive) return

        val state = _uiState.value
        val streamingId = streamingMessageId
        val partial = state.messages
            .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
            .map { msg ->
                if (streamingId != null && msg.id == streamingId) msg.copy(incomplete = true)
                else msg
            }

        runBlocking {
            chatRepo.saveMessages(state.conversationId, partial, currentSkill?.id)
        }

        job.cancel()
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val idx = msgs.indexOfLast { it.id == streamingId }
            if (idx >= 0) {
                msgs[idx] = msgs[idx].copy(incomplete = true)
            }
            state.copy(messages = msgs, isStreaming = false)
        }
    }

    fun getSkillMetas(): List<SkillManager.SkillMeta> = skillRepo.getSkillMetas()

    private fun buildSystemMessage(skill: Skill?): List<ChatMessage> {
        val systemContent = if (skill != null) {
            buildString {
                append("你是一个智能助手。\n\n当前已加载技能:\n- skill_id: ${skill.id}\n- name: ${skill.name}\n- description: ${skill.description}\n\n以下是必须遵循的技能说明:\n${skill.body}")
                if (skill.appendices.isNotEmpty()) {
                    append("\n\n以下是该技能的补充附录。仅在涉及剧情、关系、设定或扩展背景时调用；若与主技能说明冲突，始终以主技能说明为准。")
                    for (appendix in skill.appendices) {
                        append("\n\n## 附录：${appendix.label}\n\n${appendix.content}")
                    }
                }
            }
        } else {
            "你是一个智能助手。"
        }

        return listOf(ChatMessage(role = MessageRole.SYSTEM, content = systemContent))
    }
}
