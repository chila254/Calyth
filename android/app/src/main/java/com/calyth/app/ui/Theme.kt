package com.calyth.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6366f1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4338ca),
    secondary = Color(0xFF1e1e2e),
    onSecondary = Color(0xFFe4e4e7),
    background = Color(0xFF09090b),
    onBackground = Color(0xFFfafafa),
    surface = Color(0xFF18181b),
    onSurface = Color(0xFFfafafa),
    surfaceVariant = Color(0xFF27272a),
    onSurfaceVariant = Color(0xFFa1a1aa),
    error = Color(0xFFef4444),
    onError = Color.White
)

@Composable
fun CalythTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
