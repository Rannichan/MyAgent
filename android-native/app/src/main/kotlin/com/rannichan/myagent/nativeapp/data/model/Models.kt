package com.rannichan.myagent.nativeapp.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Mode { agent, npc }

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val mime_type: String,
    val url: String,
    val kind: String
)

@Serializable
data class TokenUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    val role: String,
    val content: String,
    val reasoning_content: String = "",
    val tool_calls: List<Map<String, String>> = emptyList(),
    val latency_ms: Int? = null,
    val usage: TokenUsage? = null,
    val created_at: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList()
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    val title: String = "新会话",
    val mode: Mode,
    val npc_id: String? = null,
    val agent_id: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)

@Serializable
data class NpcProfile(
    val id: String,
    val name: String,
    val system_prompt: String,
    val opening: String? = null
)

@Serializable
data class AgentProfile(
    val id: String,
    val name: String,
    val agent_md: String = "",
    val identity_md: String = "",
    val soul_md: String = "",
    val memory_md: String = ""
)

@Serializable
data class UserConfig(val content: String = "")

@Serializable
data class LlmConfig(
    val provider: String = "vllm",
    val model: String = "",
    val base_url: String = "http://127.0.0.1:8000/v1",
    val api_key: String = "EMPTY",
    val available_models: List<String> = emptyList()
)

@Serializable
enum class ThemeColorPreset { role, blue, green, purple, orange }

@Serializable
data class AppearanceSettings(
    val dark_mode: Boolean = false,
    val theme_color: ThemeColorPreset = ThemeColorPreset.role
)

@Serializable
data class SamplingSettings(
    val temperature: Double? = null,
    val top_p: Double? = null,
    val max_tokens: Double? = null,
    val presence_penalty: Double? = null,
    val frequency_penalty: Double? = null
)

sealed interface StreamEvent {
    data class Token(val content: String) : StreamEvent
    data class Reasoning(val content: String) : StreamEvent
    data class ToolCall(val items: List<Map<String, String>>) : StreamEvent
    data class Done(
        val content: String,
        val reasoning: String,
        val toolCalls: List<Map<String, String>>,
        val latencyMs: Int,
        val usage: TokenUsage?
    ) : StreamEvent

    data class Error(val message: String) : StreamEvent
}
