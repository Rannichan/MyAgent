package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.AgentProfile
import com.rannichan.myagent.nativeapp.data.model.LlmConfig
import com.rannichan.myagent.nativeapp.data.model.Mode
import com.rannichan.myagent.nativeapp.data.model.NpcProfile
import com.rannichan.myagent.nativeapp.data.model.UserConfig
import com.rannichan.myagent.nativeapp.ui.AppViewModel
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, padding: PaddingValues) {
    SettingsScreenContent(
        state = state,
        padding = padding,
        onSetThinking = vm::setThinking,
        onSetTools = vm::setTools,
        onSetNpc = vm::setNpc,
        onSaveNpc = vm::saveNpc,
        onSetAgent = vm::setAgent,
        onSaveAgent = vm::saveAgent,
        onSaveUser = vm::saveUser,
        onSetModel = vm::setModel,
        onSaveLlmConfig = vm::saveLlmConfig
    )
}

@Composable
fun SettingsScreenContent(
    state: UiState,
    padding: PaddingValues,
    onSetThinking: (Boolean) -> Unit,
    onSetTools: (Boolean) -> Unit,
    onSetNpc: (String?) -> Unit,
    onSaveNpc: (NpcProfile) -> Unit,
    onSetAgent: (String?) -> Unit,
    onSaveAgent: (AgentProfile) -> Unit,
    onSaveUser: (String) -> Unit,
    onSetModel: (String) -> Unit,
    onSaveLlmConfig: (LlmConfig) -> Unit
) {
    var npcId by remember { mutableStateOf(state.selectedNpcId ?: "") }
    var npcPrompt by remember { mutableStateOf(state.npcs.firstOrNull { it.id == npcId }?.system_prompt ?: "") }
    var agentId by remember { mutableStateOf(state.selectedAgentId ?: "") }
    var userText by remember { mutableStateOf(state.userConfig.content) }
    var model by remember { mutableStateOf(if (state.model.isBlank()) state.llmConfig.model else state.model) }
    var baseUrl by remember { mutableStateOf(if (state.llmConfig.provider == "vllm") state.llmConfig.vllm_base_url else state.llmConfig.llamacpp_base_url) }
    var apiKey by remember { mutableStateOf(if (state.llmConfig.provider == "vllm") state.llmConfig.vllm_api_key else state.llmConfig.llamacpp_api_key) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("能力开关")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thinking")
                    Switch(checked = state.thinkingEnabled, onCheckedChange = onSetThinking)
                    Text("Tools")
                    Switch(checked = state.toolsEnabled, onCheckedChange = onSetTools)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NPC 管理")
                OutlinedTextField(value = npcId, onValueChange = { npcId = it; onSetNpc(it) }, label = { Text("NPC id") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = npcPrompt, onValueChange = { npcPrompt = it }, label = { Text("system.md") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSaveNpc(NpcProfile(id = npcId, name = npcId, system_prompt = npcPrompt)) }) { Text("保存NPC") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agent 管理")
                OutlinedTextField(value = agentId, onValueChange = { agentId = it; onSetAgent(it) }, label = { Text("Agent id") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSaveAgent(AgentProfile(id = agentId, name = agentId)) }) { Text("创建/保存Agent") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("用户设定")
                OutlinedTextField(value = userText, onValueChange = { userText = it }, label = { Text("agent/user.md") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSaveUser(userText) }) { Text("保存用户设定") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LLM 配置")
                OutlinedTextField(value = model, onValueChange = { model = it; onSetModel(it) }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val cfg = if (state.llmConfig.provider == "vllm") {
                        state.llmConfig.copy(model = model, vllm_base_url = baseUrl, vllm_api_key = apiKey)
                    } else {
                        state.llmConfig.copy(model = model, llamacpp_base_url = baseUrl, llamacpp_api_key = apiKey)
                    }
                    onSaveLlmConfig(cfg)
                }) { Text("保存LLM配置") }
            }
        }
    }
}

@Preview(name = "Settings Light", showBackground = true, widthDp = 411, heightDp = 891)
@Preview(name = "Settings Dark", showBackground = true, widthDp = 411, heightDp = 891, uiMode = 0x20)
@Composable
private fun SettingsScreenPreview() {
    val sampleState = UiState(
        mode = Mode.agent,
        selectedNpcId = "npc_guide",
        selectedAgentId = "agent_default",
        npcs = listOf(NpcProfile(id = "npc_guide", name = "Guide", system_prompt = "你是行程规划助手")),
        agents = listOf(AgentProfile(id = "agent_default", name = "Default Agent")),
        userConfig = UserConfig("请用简体中文回答"),
        llmConfig = LlmConfig(model = "gpt-4o-mini", vllm_base_url = "https://api.openai.com/v1", vllm_api_key = "sk-..."),
        model = "gpt-4o-mini",
        thinkingEnabled = true,
        toolsEnabled = false
    )

    MaterialTheme {
        SettingsScreenContent(
            state = sampleState,
            padding = PaddingValues(0.dp),
            onSetThinking = {},
            onSetTools = {},
            onSetNpc = {},
            onSaveNpc = {},
            onSetAgent = {},
            onSaveAgent = {},
            onSaveUser = {},
            onSetModel = {},
            onSaveLlmConfig = {}
        )
    }
}
