package com.scribe.app.data.repository

import com.scribe.app.data.local.MessageDao
import com.scribe.app.data.local.MessageEntity
import com.scribe.app.data.model.ChatMessage
import com.scribe.app.data.model.MessageRole
import java.util.UUID

class ChatRepository(private val messageDao: MessageDao) {

    suspend fun saveMessages(conversationId: String, messages: List<ChatMessage>, skillId: String? = null) {
        messageDao.deleteConversation(conversationId)
        for (msg in messages) {
            messageDao.insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = msg.role.name,
                    content = msg.content,
                    skillId = skillId,
                    timestamp = msg.timestamp
                )
            )
        }
    }

    suspend fun loadMessages(conversationId: String): Pair<List<ChatMessage>, String?> {
        val entities = messageDao.getMessages(conversationId)
        val skillId = entities.firstOrNull { it.skillId != null }?.skillId
        val messages = entities.map {
            ChatMessage(
                role = try { MessageRole.valueOf(it.role) } catch (_: Exception) { MessageRole.USER },
                content = it.content,
                timestamp = it.timestamp
            )
        }
        return Pair(messages, skillId)
    }

    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteConversation(conversationId)
    }

    suspend fun getAllConversationIds(): List<String> {
        return messageDao.getAllConversationIds()
    }

    suspend fun getConversationTitle(conversationId: String): String {
        val first = messageDao.getFirstUserMessage(conversationId)
        return first?.take(30)?.replace("\n", " ") ?: "新对话"
    }

    fun newConversationId(): String = UUID.randomUUID().toString()
}
