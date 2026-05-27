package com.rannichan.myagent.nativeapp.data.remote

import com.rannichan.myagent.nativeapp.data.model.ChatMessage
import com.rannichan.myagent.nativeapp.data.model.LlmConfig
import com.rannichan.myagent.nativeapp.data.model.SamplingSettings
import com.rannichan.myagent.nativeapp.data.model.StreamEvent
import com.rannichan.myagent.nativeapp.data.model.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmStreamClient {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun stream(
        llmConfig: LlmConfig,
        model: String,
        systemPrompt: String,
        messages: List<ChatMessage>,
        sampling: SamplingSettings,
        thinkingEnabled: Boolean,
        toolsEnabled: Boolean
    ): Flow<StreamEvent> = flow {
        val payload = buildPayload(model, systemPrompt, messages, sampling, thinkingEnabled, toolsEnabled, llmConfig.provider)
        val baseUrl = llmConfig.base_url
        val apiKey = llmConfig.api_key

        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val start = System.currentTimeMillis()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolCalls = mutableListOf<Map<String, String>>()
        var usage: TokenUsage? = null

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                emit(StreamEvent.Error("HTTP ${resp.code}: ${resp.body?.string().orEmpty()}"))
                return@flow
            }
            val source = resp.body?.source() ?: run {
                emit(StreamEvent.Error("Empty response"))
                return@flow
            }
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    emit(
                        StreamEvent.Done(
                            content = content.toString().trim(),
                            reasoning = reasoning.toString().trim(),
                            toolCalls = toolCalls.toList(),
                            latencyMs = (System.currentTimeMillis() - start).toInt(),
                            usage = usage
                        )
                    )
                    return@flow
                }
                val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue
                chunk.optJSONObject("usage")?.let {
                    usage = TokenUsage(
                        prompt_tokens = it.optInt("prompt_tokens", 0),
                        completion_tokens = it.optInt("completion_tokens", 0),
                        total_tokens = it.optInt("total_tokens", 0)
                    )
                }
                val choices = chunk.optJSONArray("choices") ?: JSONArray()
                if (choices.length() == 0) continue
                val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: JSONObject()

                val reasoningDelta = delta.optString("reasoning_content")
                    .ifBlank { delta.optString("reasoning") }
                if (reasoningDelta.isNotBlank()) {
                    reasoning.append(reasoningDelta)
                    emit(StreamEvent.Reasoning(reasoningDelta))
                }

                val token = delta.optString("content")
                if (token.isNotEmpty()) {
                    content.append(token)
                    emit(StreamEvent.Token(token))
                }

                val toolCallRaw = delta.optJSONArray("tool_calls")?.toString()
                if (!toolCallRaw.isNullOrBlank()) {
                    val item = mapOf("raw" to toolCallRaw)
                    toolCalls.add(item)
                    emit(StreamEvent.ToolCall(listOf(item)))
                }
            }
            emit(
                StreamEvent.Done(
                    content = content.toString().trim(),
                    reasoning = reasoning.toString().trim(),
                    toolCalls = toolCalls.toList(),
                    latencyMs = (System.currentTimeMillis() - start).toInt(),
                    usage = usage
                )
            )
        }
    }

    private fun buildPayload(
        model: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        sampling: SamplingSettings,
        thinkingEnabled: Boolean,
        toolsEnabled: Boolean,
        provider: String
    ): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("stream_options", JSONObject().put("include_usage", true))
        }

        val msgArray = JSONArray()
        msgArray.put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.forEach { msg ->
            msgArray.put(JSONObject().put("role", msg.role).put("content", msg.content))
        }
        payload.put("messages", msgArray)

        sampling.temperature?.let { payload.put("temperature", it) }
        sampling.top_p?.let { payload.put("top_p", it) }
        sampling.max_tokens?.let { payload.put("max_tokens", it.toInt()) }
        sampling.presence_penalty?.let { payload.put("presence_penalty", it) }
        sampling.frequency_penalty?.let { payload.put("frequency_penalty", it) }

        if (provider == "vllm") {
            payload.put("chat_template_kwargs", JSONObject().put("enable_thinking", thinkingEnabled))
        }
        if (toolsEnabled) {
            val functionDef = JSONObject()
                .put("name", "local_note")
                .put("description", "记录一个本地待办或事实。")
                .put(
                    "parameters",
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("text", JSONObject().put("type", "string")))
                        .put("required", JSONArray().put("text"))
                )
            val tool = JSONObject()
                .put("type", "function")
                .put("function", functionDef)
            payload.put(
                "tools",
                JSONArray().put(tool)
            )
            payload.put("tool_choice", "auto")
        }
        return payload.toString()
    }
}
