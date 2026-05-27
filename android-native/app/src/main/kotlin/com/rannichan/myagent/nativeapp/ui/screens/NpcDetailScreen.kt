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
import com.rannichan.myagent.nativeapp.data.model.NpcProfile
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun NpcDetailScreen(
    npcId: String,
    state: UiState,
    onBack: () -> Unit,
    onSave: (NpcProfile) -> Unit
) {
    val isNew = npcId == "__new__"
    val existing = state.npcs.firstOrNull { it.id == npcId }

    var id by rememberSaveable { mutableStateOf(if (isNew) "" else existing?.id ?: npcId) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var systemPrompt by rememberSaveable { mutableStateOf(existing?.system_prompt ?: "") }
    var opening by rememberSaveable { mutableStateOf(existing?.opening ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text(if (isNew) "新建 NPC" else existing?.name ?: npcId) },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                    IconButton(onClick = {
                        onSave(
                            NpcProfile(
                                id = id.ifBlank { name },
                                name = name,
                                system_prompt = systemPrompt,
                                opening = opening
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
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("系统提示词") },
                minLines = 5,
                maxLines = 15,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = opening,
                onValueChange = { opening = it },
                label = { Text("开场白") },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除 NPC") },
            text = { Text("确定要删除 $id 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    // Delete handled by parent through state: caller can provide onDelete callback
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}
