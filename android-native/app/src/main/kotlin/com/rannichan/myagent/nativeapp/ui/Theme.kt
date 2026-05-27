package com.rannichan.myagent.nativeapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.rannichan.myagent.nativeapp.data.model.Mode
import com.rannichan.myagent.nativeapp.data.model.ThemeColorPreset

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
    themePreset: ThemeColorPreset = ThemeColorPreset.role,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themePreset) {
        ThemeColorPreset.role -> when {
            mode == Mode.npc && darkTheme -> NpcDarkColors
            mode == Mode.npc -> NpcLightColors
            darkTheme -> AgentDarkColors
            else -> AgentLightColors
        }
        ThemeColorPreset.blue -> if (darkTheme) AgentDarkColors else AgentLightColors
        ThemeColorPreset.green -> if (darkTheme) NpcDarkColors else NpcLightColors
        ThemeColorPreset.purple -> if (darkTheme) PurpleDarkColors else PurpleLightColors
        ThemeColorPreset.orange -> if (darkTheme) OrangeDarkColors else OrangeLightColors
    }
    CompositionLocalProvider(LocalDarkMode provides darkTheme) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

private val PurpleLightColors = lightColorScheme(
    primary = Color(0xFF7B4DFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DDFF),
    onPrimaryContainer = Color(0xFF27005A),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
)

private val PurpleDarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF4412B3),
    primaryContainer = Color(0xFF5F31E4),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E9),
)

private val OrangeLightColors = lightColorScheme(
    primary = Color(0xFFB85C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF3B1A00),
    secondary = Color(0xFF755845),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF2B1708),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF231A15),
)

private val OrangeDarkColors = darkColorScheme(
    primary = Color(0xFFFFB780),
    onPrimary = Color(0xFF623000),
    primaryContainer = Color(0xFF8C4600),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE4BFA9),
    onSecondary = Color(0xFF432B1B),
    secondaryContainer = Color(0xFF5C412F),
    onSecondaryContainer = Color(0xFFFFDBCB),
    surface = Color(0xFF1A120D),
    onSurface = Color(0xFFF1DFD6),
)
