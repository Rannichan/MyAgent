@file:OptIn(ExperimentalMaterial3Api::class)

package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.Conversation
import com.rannichan.myagent.nativeapp.data.model.Mode
import com.rannichan.myagent.nativeapp.ui.AppViewModel
import com.rannichan.myagent.nativeapp.ui.UiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    state: UiState,
    vm: AppViewModel,
    onOpenConversation: (String) -> Unit
) {
    // Dialog state
    var showRolePicker by remember { mutableStateOf(false) }
    var longPressedId by remember { mutableStateOf<String?>(null) }

    val filtered = state.conversations.filter { it.mode == state.mode }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("对话") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showRolePicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建对话")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Mode segmented button
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = state.mode == Mode.agent,
                    onClick = { vm.setMode(Mode.agent) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Agent") }
                )
                SegmentedButton(
                    selected = state.mode == Mode.npc,
                    onClick = { vm.setMode(Mode.npc) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("NPC") }
                )
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无对话，点击 + 新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        ConversationItem(
                            conversation = conv,
                            onClick = { onOpenConversation(conv.id) },
                            onLongClick = { longPressedId = conv.id }
                        )
                        // Context menu for this item
                        if (longPressedId == conv.id) {
                            ConversationContextMenu(
                                onDismiss = { longPressedId = null },
                                onDelete = {
                                    longPressedId = null
                                    vm.deleteConversation(conv.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Role picker dialog
    if (showRolePicker) {
        RolePickerDialog(
            mode = state.mode,
            npcs = state.npcs.map { it.id to it.name },
            agents = state.agents.map { it.id to it.name },
            onDismiss = { showRolePicker = false },
            onSelect = { roleId ->
                showRolePicker = false
                val id = vm.createConversationForRole(state.mode, roleId)
                onOpenConversation(id)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = conversation.title.ifBlank { "无标题" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val last = conversation.messages.lastOrNull()
            if (last != null) {
                Text(
                    text = "${last.role}: ${last.content.take(60)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}

@Composable
private fun ConversationContextMenu(
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = onDelete
        )
    }
}

@Composable
private fun RolePickerDialog(
    mode: Mode,
    npcs: List<Pair<String, String>>,
    agents: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val list = if (mode == Mode.npc) npcs else agents
    val title = if (mode == Mode.npc) "选择 NPC" else "选择 Agent"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (list.isEmpty()) {
                Text("暂无角色，请先在设置中添加")
            } else {
                LazyColumn {
                    items(list) { (id, name) ->
                        TextButton(
                            onClick = { onSelect(id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name.ifBlank { id })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
