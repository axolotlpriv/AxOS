package com.nettools.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF38BDF8)
private val Accent2 = Color(0xFF22D3EE)

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = Accent2,
    background = Color(0xFF0B1220),
    surface = Color(0xFF121C2E),
    surfaceVariant = Color(0xFF1B2740),
    onPrimary = Color(0xFF04121F),
    onBackground = Color(0xFFE6EDF6),
    onSurface = Color(0xFFE6EDF6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0277BD),
    secondary = Color(0xFF00838F),
)

@Composable
fun NetToolsTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
