package com.scribe.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scribe.app.data.local.AppDatabase
import com.scribe.app.data.local.SettingsDataStore
import com.scribe.app.data.model.*
import com.scribe.app.data.repository.ChatRepository
import com.scribe.app.data.repository.SkillManager
import com.scribe.app.data.repository.SkillRepository
import com.scribe.app.network.LLMService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val collapsedReasoningIds: Set<String> = emptySet()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val chatRepo = ChatRepository(db.messageDao())
    private val skillRepo = SkillRepository(application)
    private val settingsStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSettings = SettingsDataStore.AppSettings()
    private var currentSkill: Skill? = null
    private var settingsReady = false
    private var streamingMessageId: String? = null

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
    }

    private fun refreshSkillMetas() {
        _uiState.update { it.copy(skillMetas = skillRepo.getSkillMetas()) }
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
                conversationId = convId
            )
        }
        viewModelScope.launch {
            _uiState.update { it.copy(conversationIds = chatRepo.getAllConversationIds()) }
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
            _uiState.update {
                it.copy(
                    messages = msgs,
                    conversationId = convId,
                    isStreaming = false,
                    streamError = null,
                    tokenUsage = null
                )
            }
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            chatRepo.deleteConversation(convId)
            val ids = chatRepo.getAllConversationIds()
            _uiState.update { it.copy(conversationIds = ids) }
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

        val assistantId = state.messages[lastAssistantIdx].id
        streamingMessageId = assistantId

        _uiState.update { currentState ->
            val msgs = currentState.messages.toMutableList()
            msgs[lastAssistantIdx] = msgs[lastAssistantIdx].copy(content = "", reasoning = "")
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
        viewModelScope.launch {
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

            val allMessages = _uiState.value.messages
            val apiMessages = allMessages
                .filter { it.content.isNotBlank() || it.role == MessageRole.SYSTEM }
                .map { mapOf("role" to it.role.name.lowercase(), "content" to it.content) }

            var fullResponse = ""
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
