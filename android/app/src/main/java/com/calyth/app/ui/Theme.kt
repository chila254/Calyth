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

private val LightColors = lightColorScheme(
    primary = Color(0xFF6366f1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFe0e7ff),
    secondary = Color(0xFFF4F4F5),
    onSecondary = Color(0xFF3F3F46),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF71717A),
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun CalythTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
