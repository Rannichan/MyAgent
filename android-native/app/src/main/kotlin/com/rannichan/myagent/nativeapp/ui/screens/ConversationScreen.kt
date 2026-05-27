@file:OptIn(ExperimentalMaterial3Api::class)

package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.ChatMessage
import com.rannichan.myagent.nativeapp.data.model.SamplingSettings
import com.rannichan.myagent.nativeapp.ui.AppViewModel
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun ConversationScreen(
    conversationId: String,
    state: UiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val conversation = state.conversations.firstOrNull { it.id == conversationId }
        ?: state.activeConversation

    var inputText by rememberSaveable { mutableStateOf("") }
    var showParams by rememberSaveable { mutableStateOf(false) }
    var titleEditing by remember { mutableStateOf(false) }
    var titleText by remember(conversation?.title) { mutableStateOf(conversation?.title ?: "") }
    var showMoreMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val messages = conversation?.messages ?: emptyList()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    if (titleEditing) {
                        BasicTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            textStyle = LocalTextStyle.current,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = titleText.ifBlank { "对话" },
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    if (titleEditing) {
                        TextButton(onClick = {
                            titleEditing = false
                            if (conversation != null) vm.updateConversationTitle(conversation.id, titleText)
                        }) { Text("保存") }
                    } else {
                        IconButton(onClick = { titleEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑标题")
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        if (conversation != null) vm.deleteConversation(conversation.id)
                                        onBack()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = showParams) {
                    SamplingParamsPanel(
                        sampling = state.sampling,
                        thinkingEnabled = state.thinkingEnabled,
                        toolsEnabled = state.toolsEnabled,
                        onSamplingChange = vm::setSampling,
                        onThinkingChange = vm::setThinking,
                        onToolsChange = vm::setTools
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { showParams = !showParams }) {
                        Icon(
                            if (showParams) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (showParams) "收起参数" else "展开参数"
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("输入消息…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp)
                    )
                    if (state.isSending) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotBlank()) {
                                    inputText = ""
                                    vm.send(text)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("发送第一条消息开始对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (!message.reasoning_content.isNullOrBlank()) {
                Text(
                    text = message.reasoning_content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SamplingParamsPanel(
    sampling: SamplingSettings,
    thinkingEnabled: Boolean,
    toolsEnabled: Boolean,
    onSamplingChange: (SamplingSettings) -> Unit,
    onThinkingChange: (Boolean) -> Unit,
    onToolsChange: (Boolean) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("采样参数", style = MaterialTheme.typography.labelLarge)

            LabeledSlider(
                label = "Temperature  ${"%.2f".format(sampling.temperature ?: 0.7f)}",
                value = (sampling.temperature ?: 0.7f).toFloat(),
                range = 0f..2f,
                onValueChange = { onSamplingChange(sampling.copy(temperature = it.toDouble())) }
            )
            LabeledSlider(
                label = "Top-P  ${"%.2f".format(sampling.top_p ?: 0.9f)}",
                value = (sampling.top_p ?: 0.9f).toFloat(),
                range = 0f..1f,
                onValueChange = { onSamplingChange(sampling.copy(top_p = it.toDouble())) }
            )
            LabeledSlider(
                label = "Max Tokens  ${(sampling.max_tokens ?: 2048).toInt()}",
                value = (sampling.max_tokens ?: 2048.0).toFloat(),
                range = 64f..8192f,
                steps = 127,
                onValueChange = { onSamplingChange(sampling.copy(max_tokens = it.toDouble())) }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("思考模式", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = thinkingEnabled, onCheckedChange = onThinkingChange)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("工具调用", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = toolsEnabled, onCheckedChange = onToolsChange)
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}
