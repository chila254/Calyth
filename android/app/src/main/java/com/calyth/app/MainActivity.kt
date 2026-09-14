package com.calyth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.calyth.app.ui.CalythTheme
import com.calyth.app.ui.ChatScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("calyth", MODE_PRIVATE)
        setContent {
            var isDark by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
            CalythTheme(darkTheme = isDark) {
                ChatScreen()
            }
        }
    }
}
