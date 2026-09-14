package com.calyth.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calyth.app.ApiClient
import com.calyth.app.ModelInfo
import kotlinx.coroutines.launch

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val model: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(listOf<ModelInfo>()) }
    var selectedModel by remember { mutableStateOf("openai/gpt-oss-20b") }
    var showSettings by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8082") }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        models = ApiClient.getModels()
        if (models.isNotEmpty() && selectedModel !in models.map { it.id }) {
            selectedModel = models.first().id
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Calyth", fontWeight = FontWeight.SemiBold)
                        Text(
                            models.find { it.id == selectedModel }?.let { "${it.name} (${it.provider})" }
                                ?: "AI Assistant",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    // Model dropdown
                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(
                                models.find { it.id == selectedModel }?.name ?: "Model",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.name, fontSize = 14.sp)
                                            Text(
                                                "${model.provider} — ${model.description}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedModel = model.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Settings panel
            if (showSettings) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server URL", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                ApiClient.setServerUrl(serverUrl)
                                showSettings = false
                                scope.launch { models = ApiClient.getModels() }
                            }
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "How can I help you?",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Light,
                                    color = Color(0xFF888888)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Choose a model and start chatting.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF555555)
                                )
                            }
                        }
                    }
                }

                items(messages) { msg ->
                    MessageBubble(msg)
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Thinking...",
                                fontSize = 13.sp,
                                color = Color(0xFF888888)
                            )
                        }
                    }
                }
            }

            // Input
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...", fontSize = 14.sp) },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val msg = inputText.trim()
                                inputText = ""
                                focusManager.clearFocus()
                                scope.launch {
                                    isLoading = true
                                    messages = messages + ChatMessage(msg, true)
                                    val reply = ApiClient.sendMessage(selectedModel, msg)
                                    messages = messages + ChatMessage(reply, false, selectedModel)
                                    isLoading = false
                                }
                            }
                        }),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF2a2a3e),
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val msg = inputText.trim()
                                inputText = ""
                                focusManager.clearFocus()
                                scope.launch {
                                    isLoading = true
                                    messages = messages + ChatMessage(msg, true)
                                    val reply = ApiClient.sendMessage(selectedModel, msg)
                                    messages = messages + ChatMessage(reply, false, selectedModel)
                                    isLoading = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (message.isUser) Color(0xFF1a365d) else Color(0xFF1e293b)
    val textColor = Color(0xFFe2e8f0)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Model badge for assistant messages
            if (!message.isUser && message.model != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        message.model.contains("gemini") -> Color(0xFF1a3a1a)
                        else -> Color(0xFF1e3a5f)
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        message.model,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = when {
                            message.model.contains("gemini") -> Color(0xFF86efac)
                            else -> Color(0xFF7dd3fc)
                        }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (message.isUser) 12.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 12.dp
                ),
                color = bgColor
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = textColor
                )
            }
        }
    }
}
