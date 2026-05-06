package com.scribe.app.network

import com.scribe.app.data.model.Provider
import com.scribe.app.data.model.StreamEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object LLMService {

    private val trustAllCerts: Array<TrustManager> = arrayOf(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    private val client = OkHttpClient.Builder()
        .callTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
        .sslSocketFactory(
            SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, SecureRandom()) }.socketFactory,
            trustAllCerts[0] as X509TrustManager
        )
        .hostnameVerifier { _, _ -> true }
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun chat(
        messages: List<Map<String, String>>,
        provider: Provider,
        baseUrl: String,
        apiKey: String,
        model: String,
        aiThinking: Boolean
    ): Flow<StreamEvent> = callbackFlow {

        val endpoint = buildEndpoint(baseUrl, provider)

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

        val call = client.newCall(requestBuilder.build())
        call.enqueue(object : Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    val errorBody = try { response.body?.string() ?: "HTTP ${response.code}" } catch (_: Exception) { "HTTP ${response.code}" }
                    val errorMsg = try {
                        JSONObject(errorBody).optJSONObject("error")?.optString("message", errorBody) ?: errorBody
                    } catch (_: Exception) { errorBody }
                    trySend(StreamEvent.Error(errorMsg))
                    close()
                    return
                }

                val bodyStream = try { response.body?.byteStream() } catch (_: Exception) { null }
                if (bodyStream == null) {
                    trySend(StreamEvent.Error("空响应体"))
                    close()
                    return
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
                            trySend(event)
                        }
                        rawLine = reader.readLine()
                    }
                } catch (e: Exception) {
                    trySend(StreamEvent.Error("流读取错误: ${e.message}"))
                } finally {
                    try { reader.close() } catch (_: Exception) {}
                    try { response.close() } catch (_: Exception) {}
                    close()
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                close(e)
            }
        })

        awaitClose { call.cancel() }
    }

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

        if (aiThinking) {
            val thinking = JSONObject()
            thinking.put("type", "enabled")
            body.put("thinking", thinking)
            body.put("reasoning_effort", "high")
        } else {
            val thinking = JSONObject()
            thinking.put("type", "disabled")
            body.put("thinking", thinking)
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

    private fun buildEndpoint(baseUrl: String, provider: Provider): String {
        val trimmed = baseUrl.trimEnd('/')
        val hasV1 = trimmed.endsWith("/v1")
        return when (provider) {
            Provider.OPENAI -> if (hasV1) "$trimmed/chat/completions" else "$trimmed/v1/chat/completions"
            Provider.ANTHROPIC -> if (hasV1) "$trimmed/messages" else "$trimmed/v1/messages"
        }
    }
}
