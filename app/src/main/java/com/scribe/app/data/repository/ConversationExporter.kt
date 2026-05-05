package com.scribe.app.data.repository

import com.scribe.app.data.model.ChatMessage
import com.scribe.app.data.model.MessageRole
import org.json.JSONArray
import org.json.JSONObject

class ConversationExporter(private val chatRepo: ChatRepository) {

    suspend fun exportToJson(convId: String): String {
        val (messages, _) = chatRepo.loadMessages(convId)
        val title = chatRepo.getConversationTitle(convId)

        val json = JSONObject()
        json.put("version", 1)
        json.put("conversation", JSONObject().apply {
            put("id", convId)
            put("title", title ?: "")
        })
        json.put("messages", JSONArray().apply {
            for (msg in messages.filter { it.role != MessageRole.SYSTEM }) {
                put(JSONObject().apply {
                    put("role", msg.role.name)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                })
            }
        })

        return json.toString(2)
    }

    fun importFromJson(jsonString: String): Triple<String, String, List<ChatMessage>>? {
        val json = JSONObject(jsonString)
        val conversation = json.getJSONObject("conversation")
        val convId = conversation.getString("id")
        val title = conversation.optString("title", "")

        val messagesArray = json.getJSONArray("messages")
        val messages = mutableListOf<ChatMessage>()
        for (i in 0 until messagesArray.length()) {
            val msgJson = messagesArray.getJSONObject(i)
            messages.add(ChatMessage(
                role = MessageRole.valueOf(msgJson.getString("role")),
                content = msgJson.getString("content"),
                timestamp = msgJson.optLong("timestamp", System.currentTimeMillis())
            ))
        }

        return Triple(convId, title, messages)
    }
}
