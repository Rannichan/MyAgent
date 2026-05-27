package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.ChatMessage
import com.rannichan.myagent.nativeapp.data.model.Conversation
import com.rannichan.myagent.nativeapp.data.model.Mode
import com.rannichan.myagent.nativeapp.ui.AppViewModel
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun ChatScreen(state: UiState, vm: AppViewModel, padding: PaddingValues) {
    ChatScreenContent(
        state = state,
        padding = padding,
        onNewAgentConversation = { vm.newConversation(Mode.agent) },
        onNewNpcConversation = { vm.newConversation(Mode.npc) },
        onSelectConversation = vm::selectConversation,
        onSendMessage = vm::send
    )
}

@Composable
fun ChatScreenContent(
    state: UiState,
    padding: PaddingValues,
    onNewAgentConversation: () -> Unit,
    onNewNpcConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onSendMessage: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(0.35f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewAgentConversation) { Text("新建Agent会话") }
                Button(onClick = onNewNpcConversation) { Text("新建NPC会话") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.conversations, key = { it.id }) { c ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onSelectConversation(c.id) }) {
                        Column(Modifier.padding(10.dp)) {
                            Text(c.title)
                            Text("${c.mode} · ${c.messages.size} msgs")
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(0.65f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前模式: ${state.mode}")
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.activeConversation?.messages ?: emptyList(), key = { it.id }) { m ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${m.role}: ${m.content}")
                            if (m.reasoning_content.isNotBlank()) Text("thinking: ${m.reasoning_content}")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), label = { Text("输入消息") })
                Button(onClick = { onSendMessage(input); input = "" }) { Text("发送") }
            }
        }
    }
}

@Preview(name = "Chat Light", showBackground = true, widthDp = 411, heightDp = 891)
@Preview(name = "Chat Dark", showBackground = true, widthDp = 411, heightDp = 891, uiMode = 0x20)
@Composable
private fun ChatScreenPreview() {
    val sampleConversation = Conversation(
        title = "周末计划",
        mode = Mode.agent,
        messages = listOf(
            ChatMessage(role = "user", content = "给我安排一个两天的杭州行程"),
            ChatMessage(role = "assistant", content = "可以，先确认你偏好自然风景还是城市探索？", reasoning_content = "先澄清偏好再给路线")
        )
    )
    val sampleState = UiState(
        conversations = listOf(
            sampleConversation,
            Conversation(title = "代码评审", mode = Mode.npc, messages = listOf(ChatMessage(role = "user", content = "看下这个 PR")))
        ),
        activeConversation = sampleConversation,
        mode = Mode.agent
    )

    MaterialTheme {
        ChatScreenContent(
            state = sampleState,
            padding = PaddingValues(0.dp),
            onNewAgentConversation = {},
            onNewNpcConversation = {},
            onSelectConversation = {},
            onSendMessage = {}
        )
    }
}
