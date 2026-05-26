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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.AgentProfile
import com.rannichan.myagent.nativeapp.data.model.LlmConfig
import com.rannichan.myagent.nativeapp.data.model.NpcProfile
import com.rannichan.myagent.nativeapp.ui.AppViewModel
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, padding: PaddingValues) {
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
                    Switch(checked = state.thinkingEnabled, onCheckedChange = vm::setThinking)
                    Text("Tools")
                    Switch(checked = state.toolsEnabled, onCheckedChange = vm::setTools)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NPC 管理")
                OutlinedTextField(value = npcId, onValueChange = { npcId = it; vm.setNpc(it) }, label = { Text("NPC id") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = npcPrompt, onValueChange = { npcPrompt = it }, label = { Text("system.md") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveNpc(NpcProfile(id = npcId, name = npcId, system_prompt = npcPrompt)) }) { Text("保存NPC") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agent 管理")
                OutlinedTextField(value = agentId, onValueChange = { agentId = it; vm.setAgent(it) }, label = { Text("Agent id") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveAgent(AgentProfile(id = agentId, name = agentId)) }) { Text("创建/保存Agent") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("用户设定")
                OutlinedTextField(value = userText, onValueChange = { userText = it }, label = { Text("agent/user.md") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveUser(userText) }) { Text("保存用户设定") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LLM 配置")
                OutlinedTextField(value = model, onValueChange = { model = it; vm.setModel(it) }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val cfg = if (state.llmConfig.provider == "vllm") {
                        state.llmConfig.copy(model = model, vllm_base_url = baseUrl, vllm_api_key = apiKey)
                    } else {
                        state.llmConfig.copy(model = model, llamacpp_base_url = baseUrl, llamacpp_api_key = apiKey)
                    }
                    vm.saveLlmConfig(cfg)
                }) { Text("保存LLM配置") }
            }
        }
    }
}
