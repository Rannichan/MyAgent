@file:OptIn(ExperimentalMaterial3Api::class)

package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.LlmConfig
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun LlmSettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSave: (LlmConfig) -> Unit,
    onSetModel: (String) -> Unit,
    onFetchModels: (LlmConfig) -> Unit
) {
    val cfg = state.llmConfig
    var baseUrl by rememberSaveable(cfg.base_url) { mutableStateOf(cfg.base_url) }
    var apiKey by rememberSaveable(cfg.api_key) { mutableStateOf(cfg.api_key) }
    var model by rememberSaveable(cfg.model) { mutableStateOf(cfg.model) }
    var provider by rememberSaveable(cfg.provider) { mutableStateOf(cfg.provider) }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("LLM 设置") },
                actions = {
                    IconButton(onClick = {
                        val updated = cfg.copy(
                            base_url = baseUrl,
                            api_key = apiKey,
                            model = model,
                            provider = provider
                        )
                        onSave(updated)
                        onSetModel(model)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("Provider（vllm / llamacpp / openai）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("http://localhost:8000/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (apiKeyVisible) "隐藏" else "显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("默认模型") },
                    placeholder = { Text("qwen3-8b") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showModels = true }) {
                            Icon(Icons.Default.UnfoldMore, contentDescription = "选择模型")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = showModels && state.modelOptions.isNotEmpty(),
                    onDismissRequest = { showModels = false }
                ) {
                    state.modelOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                model = option
                                showModels = false
                            }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onFetchModels(
                            cfg.copy(
                                base_url = baseUrl,
                                api_key = apiKey,
                                model = model,
                                provider = provider
                            )
                        )
                    },
                    enabled = !state.isLoadingModels
                ) {
                    if (state.isLoadingModels) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("拉取模型列表")
                    }
                }
                Text(
                    text = if (state.modelOptions.isEmpty()) "暂无已发现模型" else "已发现 ${state.modelOptions.size} 个模型",
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            if (!state.error.isNullOrBlank()) {
                Text(state.error)
            }
        }
    }
}
