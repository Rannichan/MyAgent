package com.rannichan.myagent.nativeapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rannichan.myagent.nativeapp.data.local.LocalStore
import com.rannichan.myagent.nativeapp.data.model.AgentProfile
import com.rannichan.myagent.nativeapp.data.model.ChatMessage
import com.rannichan.myagent.nativeapp.data.model.Conversation
import com.rannichan.myagent.nativeapp.data.model.LlmConfig
import com.rannichan.myagent.nativeapp.data.model.Mode
import com.rannichan.myagent.nativeapp.data.model.NpcProfile
import com.rannichan.myagent.nativeapp.data.model.SamplingSettings
import com.rannichan.myagent.nativeapp.data.model.StreamEvent
import com.rannichan.myagent.nativeapp.data.model.UserConfig
import com.rannichan.myagent.nativeapp.data.remote.LlmStreamClient
import com.rannichan.myagent.nativeapp.domain.SystemPromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val llm = LlmStreamClient()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            val conversations = store.listConversations()
            val npcs = store.listNpcs()
            val agents = store.listAgents()
            val user = store.loadUser()
            val llmConfig = store.loadLlmConfig()
            _state.update {
                it.copy(
                    conversations = conversations,
                    activeConversation = conversations.firstOrNull(),
                    npcs = npcs,
                    agents = agents,
                    userConfig = user,
                    llmConfig = llmConfig,
                    selectedNpcId = npcs.firstOrNull()?.id,
                    selectedAgentId = agents.firstOrNull()?.id
                )
            }
        }
    }

    fun newConversation(mode: Mode) {
        viewModelScope.launch {
            val current = _state.value
            val conversation = Conversation(
                mode = mode,
                npc_id = current.selectedNpcId,
                agent_id = current.selectedAgentId
            )
            store.saveConversation(conversation)
            _state.update {
                it.copy(
                    conversations = listOf(conversation) + it.conversations,
                    activeConversation = conversation,
                    mode = mode
                )
            }
        }
    }

    fun selectConversation(id: String) {
        val item = _state.value.conversations.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(activeConversation = item, mode = item.mode) }
    }

    fun send(content: String) {
        val current = _state.value
        val active = current.activeConversation ?: return
        if (content.isBlank()) return

        val userMessage = ChatMessage(role = "user", content = content)
        val preConversation = active.copy(
            messages = active.messages + userMessage,
            updated_at = System.currentTimeMillis(),
            title = if (active.title == "新会话") content.take(32) else active.title
        )
        updateConversation(preConversation)

        viewModelScope.launch {
            val npc = _state.value.npcs.firstOrNull { it.id == _state.value.selectedNpcId }
            val agent = _state.value.agents.firstOrNull { it.id == _state.value.selectedAgentId }
            val prompt = SystemPromptBuilder.build(_state.value.mode, npc, agent, _state.value.userConfig.content, _state.value.thinkingEnabled)

            llm.stream(
                llmConfig = _state.value.llmConfig,
                model = _state.value.model.ifBlank { _state.value.llmConfig.model },
                systemPrompt = prompt,
                messages = preConversation.messages,
                sampling = _state.value.sampling,
                thinkingEnabled = _state.value.thinkingEnabled,
                toolsEnabled = _state.value.toolsEnabled
            ).collect { event ->
                when (event) {
                    is StreamEvent.Token -> appendAssistantDelta(event.content, false)
                    is StreamEvent.Reasoning -> appendAssistantDelta(event.content, true)
                    is StreamEvent.ToolCall -> Unit
                    is StreamEvent.Done -> finalizeAssistant(event)
                    is StreamEvent.Error -> _state.update { it.copy(error = event.message) }
                }
            }
        }
    }

    private fun appendAssistantDelta(delta: String, reasoning: Boolean) {
        val current = _state.value.activeConversation ?: return
        val last = current.messages.lastOrNull()
        val assistant = if (last?.role == "assistant") {
            if (reasoning) last.copy(reasoning_content = last.reasoning_content + delta) else last.copy(content = last.content + delta)
        } else {
            if (reasoning) ChatMessage(role = "assistant", content = "", reasoning_content = delta)
            else ChatMessage(role = "assistant", content = delta)
        }
        val nextMessages = if (last?.role == "assistant") current.messages.dropLast(1) + assistant else current.messages + assistant
        updateConversation(current.copy(messages = nextMessages, updated_at = System.currentTimeMillis()))
    }

    private fun finalizeAssistant(done: StreamEvent.Done) {
        val current = _state.value.activeConversation ?: return
        val last = current.messages.lastOrNull()
        val assistant = if (last?.role == "assistant") {
            last.copy(
                content = done.content,
                reasoning_content = done.reasoning,
                latency_ms = done.latencyMs,
                usage = done.usage,
                tool_calls = done.toolCalls
            )
        } else {
            ChatMessage(
                role = "assistant",
                content = done.content,
                reasoning_content = done.reasoning,
                latency_ms = done.latencyMs,
                usage = done.usage,
                tool_calls = done.toolCalls
            )
        }
        val nextMessages = if (last?.role == "assistant") current.messages.dropLast(1) + assistant else current.messages + assistant
        updateConversation(current.copy(messages = nextMessages, updated_at = System.currentTimeMillis()))
    }

    private fun updateConversation(conversation: Conversation) {
        viewModelScope.launch { store.saveConversation(conversation) }
        _state.update {
            val next = it.conversations.filterNot { item -> item.id == conversation.id }
            it.copy(conversations = listOf(conversation) + next, activeConversation = conversation)
        }
    }

    fun saveNpc(profile: NpcProfile) {
        viewModelScope.launch {
            store.saveNpc(profile)
            _state.update {
                val next = (it.npcs.filterNot { x -> x.id == profile.id } + profile).sortedBy { x -> x.name.lowercase() }
                it.copy(npcs = next, selectedNpcId = profile.id)
            }
        }
    }

    fun saveAgent(profile: AgentProfile) {
        viewModelScope.launch {
            store.saveAgent(profile)
            _state.update {
                val next = (it.agents.filterNot { x -> x.id == profile.id } + profile).sortedBy { x -> x.name.lowercase() }
                it.copy(agents = next, selectedAgentId = profile.id)
            }
        }
    }

    fun saveUser(content: String) {
        viewModelScope.launch {
            store.saveUser(content)
            _state.update { it.copy(userConfig = UserConfig(content)) }
        }
    }

    fun saveLlmConfig(config: LlmConfig) {
        viewModelScope.launch {
            store.saveLlmConfig(config)
            _state.update { it.copy(llmConfig = config) }
        }
    }

    fun setMode(mode: Mode) { _state.update { it.copy(mode = mode) } }
    fun setNpc(id: String?) { _state.update { it.copy(selectedNpcId = id) } }
    fun setAgent(id: String?) { _state.update { it.copy(selectedAgentId = id) } }
    fun setThinking(enabled: Boolean) { _state.update { it.copy(thinkingEnabled = enabled) } }
    fun setTools(enabled: Boolean) { _state.update { it.copy(toolsEnabled = enabled) } }
    fun setModel(model: String) { _state.update { it.copy(model = model) } }
}

data class UiState(
    val conversations: List<Conversation> = emptyList(),
    val activeConversation: Conversation? = null,
    val mode: Mode = Mode.agent,
    val selectedNpcId: String? = null,
    val selectedAgentId: String? = null,
    val npcs: List<NpcProfile> = emptyList(),
    val agents: List<AgentProfile> = emptyList(),
    val userConfig: UserConfig = UserConfig(),
    val llmConfig: LlmConfig = LlmConfig(),
    val model: String = "",
    val sampling: SamplingSettings = SamplingSettings(),
    val thinkingEnabled: Boolean = false,
    val toolsEnabled: Boolean = false,
    val error: String? = null
)
