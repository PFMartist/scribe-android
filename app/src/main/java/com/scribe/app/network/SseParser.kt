package com.scribe.app.network

import android.util.Log
import com.scribe.app.data.model.Provider
import com.scribe.app.data.model.StreamEvent
import org.json.JSONObject

object SseParser {

    fun parseLine(line: String, event: String?, provider: Provider): StreamEvent? {
        if (line.isBlank()) return null
        if (!line.startsWith("data: ")) return null
        val data = line.removePrefix("data: ")
        if (data == "[DONE]") return StreamEvent.Done

        return try {
            when (provider) {
                Provider.OPENAI -> parseOpenAI(data)
                Provider.ANTHROPIC -> parseAnthropic(data, event)
            }
        } catch (e: Exception) {
            Log.w("Scribe-SSE", "解析 SSE 行失败: ${e.message}", e)
            null
        }
    }

    private fun parseOpenAI(data: String): StreamEvent? {
        val json = JSONObject(data)

        if (json.has("error")) {
            val msg = json.optJSONObject("error")?.optString("message", "未知错误") ?: "未知错误"
            return StreamEvent.Error(msg)
        }

        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) {
            val usage = json.optJSONObject("usage")
            if (usage != null) {
                return StreamEvent.Usage(
                    usage.optInt("prompt_tokens", 0),
                    usage.optInt("completion_tokens", 0)
                )
            }
            return null
        }

        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null

        // Explicit null-check: some Android versions' optString converts JSONObject.NULL to "null"
        val reasoning = safeGetString(delta, "reasoning_content")
        val content = safeGetString(delta, "content")

        return when {
            reasoning.isNotEmpty() -> StreamEvent.Reasoning(reasoning)
            content.isNotEmpty() -> StreamEvent.Content(content)
            else -> null
        }
    }

    private fun parseAnthropic(data: String, event: String?): StreamEvent? {
        val json = JSONObject(data)

        if (json.has("error")) {
            val msg = json.optJSONObject("error")?.optString("message", "未知错误") ?: "未知错误"
            return StreamEvent.Error(msg)
        }

        val type = safeGetString(json, "type")

        when (type) {
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta") ?: return null
                val deltaType = safeGetString(delta, "type")
                return when (deltaType) {
                    "thinking_delta" -> {
                        val text = safeGetString(delta, "thinking")
                        if (text.isNotEmpty()) StreamEvent.Reasoning(text) else null
                    }
                    "text_delta" -> {
                        val text = safeGetString(delta, "text")
                        if (text.isNotEmpty()) StreamEvent.Content(text) else null
                    }
                    else -> null
                }
            }
            "message_start" -> {
                val usage = json.optJSONObject("message")?.optJSONObject("usage")
                if (usage != null) {
                    return StreamEvent.Usage(
                        usage.optInt("input_tokens", 0),
                        usage.optInt("output_tokens", 0)
                    )
                }
                return null
            }
            "message_delta" -> {
                val usage = json.optJSONObject("usage")
                if (usage != null) {
                    return StreamEvent.Usage(
                        usage.optInt("input_tokens", usage.optInt("prompt_tokens", 0)),
                        usage.optInt("output_tokens", usage.optInt("completion_tokens", 0))
                    )
                }
                return null
            }
            else -> return null
        }
    }

    /**
     * Safely get a string value from JSON, treating JSON null the same as missing key.
     */
    private fun safeGetString(json: JSONObject, key: String, fallback: String = ""): String {
        if (!json.has(key)) return fallback
        if (json.isNull(key)) return fallback
        return json.optString(key, fallback)
    }
}
