@file:OptIn(ExperimentalMaterial3Api::class)

package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsMainScreen(
    onNpc: () -> Unit,
    onAgent: () -> Unit,
    onUser: () -> Unit,
    onLlm: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsNavItem(
                    title = "NPC 设置",
                    subtitle = "管理 NPC 角色配置",
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    onClick = onNpc
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 72.dp)) }
            item {
                SettingsNavItem(
                    title = "Agent 设置",
                    subtitle = "管理 Agent 角色配置",
                    leadingIcon = { Icon(Icons.Outlined.SmartToy, contentDescription = null) },
                    onClick = onAgent
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 72.dp)) }
            item {
                SettingsNavItem(
                    title = "用户设置",
                    subtitle = "编辑用户信息文本",
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Message, contentDescription = null) },
                    onClick = onUser
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 72.dp)) }
            item {
                SettingsNavItem(
                    title = "LLM 设置",
                    subtitle = "配置模型 API",
                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    onClick = onLlm
                )
            }
        }
    }
}

@Composable
fun SettingsNavItem(
    title: String,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        leadingContent = leadingIcon,
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
