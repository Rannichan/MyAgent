@file:OptIn(ExperimentalMaterial3Api::class)

package com.rannichan.myagent.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rannichan.myagent.nativeapp.data.model.AppearanceSettings
import com.rannichan.myagent.nativeapp.data.model.ThemeColorPreset
import com.rannichan.myagent.nativeapp.ui.UiState

@Composable
fun AppearanceSettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSave: (AppearanceSettings) -> Unit
) {
    var darkMode by rememberSaveable(state.appearance.dark_mode) { mutableStateOf(state.appearance.dark_mode) }
    var themeColor by rememberSaveable(state.appearance.theme_color) { mutableStateOf(state.appearance.theme_color) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("外观设置") },
                actions = {
                    IconButton(onClick = {
                        onSave(AppearanceSettings(dark_mode = darkMode, theme_color = themeColor))
                        onBack()
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("夜间模式")
                    Text("关闭后使用浅色主题")
                }
                Switch(checked = darkMode, onCheckedChange = { darkMode = it })
            }
            HorizontalDivider()
            Text(
                text = "主题颜色",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
            ThemeColorPreset.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { themeColor = option }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(selected = themeColor == option, onClick = { themeColor = option })
                    Column {
                        Text(themeColorLabel(option))
                        Text(themeColorDescription(option))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun themeColorLabel(preset: ThemeColorPreset): String = when (preset) {
    ThemeColorPreset.role -> "跟随角色"
    ThemeColorPreset.blue -> "蓝色"
    ThemeColorPreset.green -> "绿色"
    ThemeColorPreset.purple -> "紫色"
    ThemeColorPreset.orange -> "橙色"
}

private fun themeColorDescription(preset: ThemeColorPreset): String = when (preset) {
    ThemeColorPreset.role -> "Agent 使用蓝色，NPC 使用绿色"
    ThemeColorPreset.blue -> "统一使用蓝色主题"
    ThemeColorPreset.green -> "统一使用绿色主题"
    ThemeColorPreset.purple -> "统一使用紫色主题"
    ThemeColorPreset.orange -> "统一使用橙色主题"
}
