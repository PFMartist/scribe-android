package com.scribe.app.data.model

import com.squareup.moshi.JsonClass

enum class Provider { OPENAI, ANTHROPIC }

enum class MessageRole { SYSTEM, USER, ASSISTANT }

@JsonClass(generateAdapter = true)
data class SkillFrontmatter(
    val name: String,
    val description: String
)

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val body: String,
    val appendices: List<SkillAppendix>
)

data class SkillAppendix(
    val label: String,
    val content: String
)

data class Conversation(
    val id: String = "",
    val title: String = "新对话",
    val provider: Provider = Provider.OPENAI,
    val skillId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val reasoning: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

sealed class StreamEvent {
    data class Reasoning(val text: String) : StreamEvent()
    data class Content(val text: String) : StreamEvent()
    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    data object Done : StreamEvent()
}
