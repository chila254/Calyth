package com.calyth.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4A6FA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1a365d),
    secondary = Color(0xFF16213e),
    onSecondary = Color(0xFFe0e0e0),
    background = Color(0xFF0f0f0f),
    onBackground = Color(0xFFe0e0e0),
    surface = Color(0xFF1a1a2e),
    onSurface = Color(0xFFe0e0e0),
    surfaceVariant = Color(0xFF1e293b),
    onSurfaceVariant = Color(0xFFe2e8f0),
    error = Color(0xFFfca5a5),
    onError = Color(0xFF3b1a1a)
)

@Composable
fun CalythTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
