package com.rannichan.myagent.nativeapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rannichan.myagent.nativeapp.data.local.LocalStore
import com.rannichan.myagent.nativeapp.data.model.AgentProfile
import com.rannichan.myagent.nativeapp.data.model.AppearanceSettings
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
            val appearance = store.loadAppearance()
            val selectedModel = llmConfig.model
            _state.update {
                it.copy(
                    conversations = conversations,
                    activeConversation = conversations.firstOrNull(),
                    npcs = npcs,
                    agents = agents,
                    userConfig = user,
                    llmConfig = llmConfig,
                    appearance = appearance,
                    selectedNpcId = npcs.firstOrNull()?.id,
                    selectedAgentId = agents.firstOrNull()?.id,
                    model = selectedModel,
                    modelOptions = buildModelOptions(llmConfig, selectedModel)
                )
            }
        }
    }

    /** Create a new conversation for a specific role and return its ID immediately. */
    fun createConversationForRole(mode: Mode, roleId: String): String {
        val conversation = Conversation(
            mode = mode,
            npc_id = if (mode == Mode.npc) roleId else null,
            agent_id = if (mode == Mode.agent) roleId else null
        )
        viewModelScope.launch { store.saveConversation(conversation) }
        _state.update {
            it.copy(
                conversations = listOf(conversation) + it.conversations,
                activeConversation = conversation,
                mode = mode,
                selectedNpcId = if (mode == Mode.npc) roleId else it.selectedNpcId,
                selectedAgentId = if (mode == Mode.agent) roleId else it.selectedAgentId
            )
        }
        return conversation.id
    }

    fun selectConversation(id: String) {
        val item = _state.value.conversations.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                activeConversation = item,
                mode = item.mode,
                selectedNpcId = item.npc_id ?: it.selectedNpcId,
                selectedAgentId = item.agent_id ?: it.selectedAgentId
            )
        }
    }

    fun updateConversationTitle(id: String, title: String) {
        _state.update { state ->
            val updated = state.conversations.map { c -> if (c.id == id) c.copy(title = title) else c }
            val active = if (state.activeConversation?.id == id) state.activeConversation.copy(title = title) else state.activeConversation
            state.copy(conversations = updated, activeConversation = active)
        }
        val conv = _state.value.activeConversation ?: return
        viewModelScope.launch { store.saveConversation(conv) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch { store.deleteConversation(id) }
        _state.update { state ->
            val remaining = state.conversations.filter { it.id != id }
            state.copy(
                conversations = remaining,
                activeConversation = if (state.activeConversation?.id == id) remaining.firstOrNull() else state.activeConversation
            )
        }
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
        _state.update { it.copy(isSending = true, error = null) }

        viewModelScope.launch {
            try {
                val npc = _state.value.npcs.firstOrNull { it.id == _state.value.selectedNpcId }
                val agent = _state.value.agents.firstOrNull { it.id == _state.value.selectedAgentId }
                val prompt = SystemPromptBuilder.build(
                    _state.value.mode, npc, agent,
                    _state.value.userConfig.content, _state.value.thinkingEnabled
                )
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
            } finally {
                _state.update { it.copy(isSending = false) }
            }
        }
    }

    private fun appendAssistantDelta(delta: String, reasoning: Boolean) {
        val current = _state.value.activeConversation ?: return
        val last = current.messages.lastOrNull()
        val assistant = if (last?.role == "assistant") {
            if (reasoning) last.copy(reasoning_content = last.reasoning_content + delta)
            else last.copy(content = last.content + delta)
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
                val next = (it.npcs.filterNot { x -> x.id == profile.id } + profile)
                    .sortedBy { x -> x.name.lowercase() }
                it.copy(npcs = next, selectedNpcId = profile.id)
            }
        }
    }

    fun deleteNpc(id: String) {
        viewModelScope.launch { store.deleteNpc(id) }
        _state.update { state ->
            val remaining = state.npcs.filter { it.id != id }
            state.copy(
                npcs = remaining,
                selectedNpcId = if (state.selectedNpcId == id) remaining.firstOrNull()?.id else state.selectedNpcId
            )
        }
    }

    fun saveAgent(profile: AgentProfile) {
        viewModelScope.launch {
            store.saveAgent(profile)
            _state.update {
                val next = (it.agents.filterNot { x -> x.id == profile.id } + profile)
                    .sortedBy { x -> x.name.lowercase() }
                it.copy(agents = next, selectedAgentId = profile.id)
            }
        }
    }

    fun deleteAgent(id: String) {
        viewModelScope.launch { store.deleteAgent(id) }
        _state.update { state ->
            val remaining = state.agents.filter { it.id != id }
            state.copy(
                agents = remaining,
                selectedAgentId = if (state.selectedAgentId == id) remaining.firstOrNull()?.id else state.selectedAgentId
            )
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
            _state.update {
                val selectedModel = it.model.ifBlank { config.model }
                it.copy(
                    llmConfig = config,
                    model = selectedModel,
                    modelOptions = buildModelOptions(config, selectedModel)
                )
            }
        }
    }

    fun refreshModels(config: LlmConfig) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingModels = true, error = null) }
            runCatching { llm.fetchModels(config) }
                .onSuccess { models ->
                    val currentState = _state.value
                    val preparedConfig = config.copy(available_models = models)
                    val selectedModel = currentState.model.ifBlank { currentState.llmConfig.model.ifBlank { models.firstOrNull().orEmpty() } }
                    val updatedConfig = preparedConfig.copy(
                        model = preparedConfig.model.ifBlank { selectedModel }
                    )
                    store.saveLlmConfig(updatedConfig)
                    _state.update {
                        it.copy(
                            llmConfig = updatedConfig,
                            model = selectedModel,
                            modelOptions = buildModelOptions(updatedConfig, selectedModel),
                            isLoadingModels = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoadingModels = false,
                            error = "拉取模型失败：${error.message ?: error::class.java.simpleName}"
                        )
                    }
                }
        }
    }

    fun saveAppearance(settings: AppearanceSettings) {
        viewModelScope.launch {
            store.saveAppearance(settings)
            _state.update { it.copy(appearance = settings) }
        }
    }

    fun setMode(mode: Mode) { _state.update { it.copy(mode = mode) } }
    fun setNpc(id: String?) { _state.update { it.copy(selectedNpcId = id) } }
    fun setAgent(id: String?) { _state.update { it.copy(selectedAgentId = id) } }
    fun setThinking(enabled: Boolean) { _state.update { it.copy(thinkingEnabled = enabled) } }
    fun setTools(enabled: Boolean) { _state.update { it.copy(toolsEnabled = enabled) } }
    fun setModel(model: String) {
        _state.update {
            it.copy(
                model = model,
                modelOptions = buildModelOptions(it.llmConfig, model)
            )
        }
    }
    fun setSampling(sampling: SamplingSettings) { _state.update { it.copy(sampling = sampling) } }
    fun clearError() { _state.update { it.copy(error = null) } }

    private fun buildModelOptions(config: LlmConfig, selectedModel: String): List<String> = buildSet {
        addAll(config.available_models.filter { it.isNotBlank() })
        if (config.model.isNotBlank()) add(config.model)
        if (selectedModel.isNotBlank()) add(selectedModel)
    }.sorted()
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
    val appearance: AppearanceSettings = AppearanceSettings(),
    val model: String = "",
    val modelOptions: List<String> = emptyList(),
    val sampling: SamplingSettings = SamplingSettings(),
    val thinkingEnabled: Boolean = false,
    val toolsEnabled: Boolean = false,
    val isLoadingModels: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)
