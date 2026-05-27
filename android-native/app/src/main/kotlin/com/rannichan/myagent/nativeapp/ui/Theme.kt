package com.rannichan.myagent.nativeapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.rannichan.myagent.nativeapp.data.model.Mode

// Agent: indigo/blue seed
private val AgentLightColors = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001258),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2C),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
)

private val AgentDarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF00228C),
    primaryContainer = Color(0xFF0038C5),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE4E2E6),
)

// NPC: teal/green seed
private val NpcLightColors = lightColorScheme(
    primary = Color(0xFF006C4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4D6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9D8),
    onSecondaryContainer = Color(0xFF0A1F16),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
)

private val NpcDarkColors = darkColorScheme(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D8),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3DF),
)

val LocalDarkMode = compositionLocalOf { false }

@Composable
fun AppTheme(
    mode: Mode,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        mode == Mode.npc && darkTheme -> NpcDarkColors
        mode == Mode.npc -> NpcLightColors
        darkTheme -> AgentDarkColors
        else -> AgentLightColors
    }
    CompositionLocalProvider(LocalDarkMode provides darkTheme) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
