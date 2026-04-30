package com.scribe.app.network

import com.scribe.app.data.model.Provider
import com.scribe.app.data.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object LLMService {

    private val client = OkHttpClient.Builder()
        .callTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun chat(
        messages: List<Map<String, String>>,
        provider: Provider,
        baseUrl: String,
        apiKey: String,
        model: String,
        aiThinking: Boolean
    ): Flow<StreamEvent> = flow {

        val endpoint = when (provider) {
            Provider.OPENAI -> "${baseUrl.trimEnd('/')}/v1/chat/completions"
            Provider.ANTHROPIC -> "${baseUrl.trimEnd('/')}/v1/messages"
        }

        val body = when (provider) {
            Provider.OPENAI -> buildOpenAIBody(messages, model, aiThinking)
            Provider.ANTHROPIC -> buildAnthropicBody(messages, model, aiThinking)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")

        when (provider) {
            Provider.OPENAI -> requestBuilder.header("Authorization", "Bearer $apiKey")
            Provider.ANTHROPIC -> {
                requestBuilder.header("x-api-key", apiKey)
                requestBuilder.header("anthropic-version", "2023-06-01")
            }
        }

        // Blocking network call — safe because flowOn(IO) runs this on IO dispatcher
        val call = client.newCall(requestBuilder.build())
        val response = call.execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "HTTP ${response.code}"
            val errorMsg = try {
                JSONObject(errorBody).optJSONObject("error")?.optString("message", errorBody) ?: errorBody
            } catch (_: Exception) { errorBody }
            emit(StreamEvent.Error(errorMsg))
            return@flow
        }

        val bodyStream = response.body?.byteStream()
        if (bodyStream == null) {
            emit(StreamEvent.Error("空响应体"))
            return@flow
        }

        val reader = BufferedReader(InputStreamReader(bodyStream))
        var currentEvent: String? = null

        try {
            var rawLine: String? = reader.readLine()
            while (rawLine != null) {
                val line = rawLine.trim()
                if (line.startsWith("event: ")) {
                    currentEvent = line.removePrefix("event: ").trim()
                    rawLine = reader.readLine()
                    continue
                }

                val event = SseParser.parseLine(line, currentEvent, provider)
                currentEvent = null

                if (event != null) {
                    emit(event)
                }
                rawLine = reader.readLine()
            }
        } catch (e: Exception) {
            emit(StreamEvent.Error("流读取错误: ${e.message}"))
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildOpenAIBody(
        messages: List<Map<String, String>>,
        model: String,
        aiThinking: Boolean
    ): JSONObject {
        val msgsArray = JSONArray()
        for (msg in messages) {
            val obj = JSONObject()
            obj.put("role", msg["role"])
            obj.put("content", msg["content"])
            msgsArray.put(obj)
        }

        val body = JSONObject()
        body.put("model", model)
        body.put("messages", msgsArray)
        body.put("stream", true)
        body.put("stream_options", JSONObject().put("include_usage", true))

        val thinking = JSONObject()
        thinking.put("type", if (aiThinking) "enabled" else "disabled")
        body.put("thinking", thinking)

        if (aiThinking) {
            body.put("reasoning_effort", "high")
        }

        return body
    }

    private fun buildAnthropicBody(
        messages: List<Map<String, String>>,
        model: String,
        aiThinking: Boolean
    ): JSONObject {
        val msgsArray = JSONArray()
        var systemPrompt = ""

        for (msg in messages) {
            if (msg["role"] == "system") {
                systemPrompt = msg["content"] ?: ""
            } else {
                val obj = JSONObject()
                obj.put("role", msg["role"])
                obj.put("content", msg["content"])
                msgsArray.put(obj)
            }
        }

        val body = JSONObject()
        body.put("model", model)
        body.put("max_tokens", 8192)
        body.put("messages", msgsArray)
        body.put("stream", true)

        if (systemPrompt.isNotEmpty()) {
            body.put("system", systemPrompt)
        }

        val thinking = JSONObject()
        thinking.put("type", if (aiThinking) "enabled" else "disabled")
        if (aiThinking) {
            thinking.put("budget_tokens", 4000)
        }
        body.put("thinking", thinking)

        return body
    }
}
