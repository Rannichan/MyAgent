@file:OptIn(ExperimentalMaterial3Api::class)

package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.AgentProfile
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun AgentDetailScreen(
    agentId: String,
    state: UiState,
    onBack: () -> Unit,
    onSave: (AgentProfile) -> Unit
) {
    val isNew = agentId == "__new__"
    val existing = state.agents.firstOrNull { it.id == agentId }

    var id by rememberSaveable { mutableStateOf(if (isNew) "" else existing?.id ?: agentId) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var agentMd by rememberSaveable { mutableStateOf(existing?.agent_md ?: "") }
    var identityMd by rememberSaveable { mutableStateOf(existing?.identity_md ?: "") }
    var soulMd by rememberSaveable { mutableStateOf(existing?.soul_md ?: "") }
    var memoryMd by rememberSaveable { mutableStateOf(existing?.memory_md ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text(if (isNew) "新建 Agent" else existing?.name ?: agentId) },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                    IconButton(onClick = {
                        onSave(
                            AgentProfile(
                                id = id.ifBlank { name },
                                name = name,
                                agent_md = agentMd,
                                identity_md = identityMd,
                                soul_md = soulMd,
                                memory_md = memoryMd
                            )
                        )
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
            if (isNew) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("ID（唯一标识）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = agentMd,
                onValueChange = { agentMd = it },
                label = { Text("Agent 描述（agent.md）") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = identityMd,
                onValueChange = { identityMd = it },
                label = { Text("身份设定（identity.md）") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = soulMd,
                onValueChange = { soulMd = it },
                label = { Text("灵魂/人格（soul.md）") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = memoryMd,
                onValueChange = { memoryMd = it },
                label = { Text("记忆（memory.md）") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除 Agent") },
            text = { Text("确定要删除 $id 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}
