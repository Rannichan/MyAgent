package com.rannichan.myagent.nativeapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rannichan.myagent.nativeapp.ui.screens.ChatScreen
import com.rannichan.myagent.nativeapp.ui.screens.SettingsScreen

@Composable
fun AppScaffold(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }

    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Outlined.Chat, contentDescription = null) },
                        label = { Text("会话") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            when (tab) {
                0 -> ChatScreen(state = state, vm = vm, padding = padding)
                else -> SettingsScreen(state = state, vm = vm, padding = padding)
            }
        }
    }
}
