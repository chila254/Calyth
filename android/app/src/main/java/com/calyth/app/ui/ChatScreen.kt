package com.calyth.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calyth.app.ApiClient
import com.calyth.app.ModelInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: Long = System.currentTimeMillis(),
    val title: String = "New chat",
    val messages: MutableList<ChatMessage> = mutableListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    var conversations by remember { mutableStateOf(listOf<Conversation>()) }
    var activeChat by remember { mutableStateOf<Conversation?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(listOf<ModelInfo>()) }
    var selectedModel by remember { mutableStateOf("openai/gpt-oss-20b") }
    var showDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("https://calyth.onrender.com") }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(showDrawer) {
        if (showDrawer) drawerState.open() else drawerState.close()
    }

    LaunchedEffect(Unit) {
        ApiClient.setServerUrl(serverUrl)
        models = ApiClient.getModels()
        if (models.isNotEmpty()) selectedModel = models.first().id
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(text: String) {
        scope.launch {
            if (activeChat == null) {
                val newChat = Conversation()
                conversations = listOf(newChat) + conversations
                activeChat = newChat
                messages = emptyList()
            }
            val chatMsg = ChatMessage(text, true)
            messages = messages + chatMsg
            activeChat?.messages?.add(chatMsg)
            activeChat = activeChat?.copy(title = text.take(40))
            isLoading = true
            val reply = ApiClient.sendMessage(selectedModel, text)
            val replyMsg = ChatMessage(reply, false, selectedModel)
            messages = messages + replyMsg
            activeChat?.messages?.add(replyMsg)
            isLoading = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showDrawer,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = Color(0xFF18181b)
            ) {
                // Logo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚡ Calyth",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = { showDrawer = false }) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            "Close",
                            tint = Color(0xFF71717a)
                        )
                    }
                }

                // New chat button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clickable {
                            val newChat = Conversation()
                            conversations = listOf(newChat) + conversations
                            activeChat = newChat
                            messages = emptyList()
                            showDrawer = false
                        },
                    color = Color(0xFF27272a),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFFa1a1aa), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Chat", fontSize = 13.sp, color = Color(0xFFa1a1aa))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Chat list
                Text(
                    "Recent",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF71717a),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    letterSpacing = 0.5.sp
                )

                LazyColumn {
                    items(conversations) { chat ->
                        val isActive = activeChat?.id == chat.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clickable {
                                    activeChat = chat
                                    messages = chat.messages.toList()
                                    showDrawer = false
                                },
                            color = if (isActive) Color(0xFF6366f1).copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(10.dp, 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💬", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    chat.title,
                                    fontSize = 13.sp,
                                    color = if (isActive) Color(0xFF818cf8) else Color(0xFFa1a1aa),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // Delete button
                                IconButton(
                                    onClick = {
                                        conversations = conversations.filter { it.id != chat.id }
                                        if (activeChat?.id == chat.id) {
                                            activeChat = null
                                            messages = emptyList()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Delete",
                                        tint = Color(0xFF52525b),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            val model = models.find { it.id == selectedModel }
                            Text(
                                model?.name ?: "Calyth",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Text(
                                model?.provider ?: "AI Assistant",
                                fontSize = 11.sp,
                                color = Color(0xFF71717a)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showDrawer = !showDrawer }) {
                            Icon(Icons.Default.Menu, "Menu", tint = Color(0xFFa1a1aa))
                        }
                    },
                    actions = {
                        // Model selector
                        var modelExpanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { modelExpanded = true }) {
                                Text(
                                    models.find { it.id == selectedModel }?.name?.take(15) ?: "Model",
                                    color = Color(0xFF6366f1),
                                    fontSize = 12.sp
                                )
                            }
                            DropdownMenu(modelExpanded, { modelExpanded = false }) {
                                models.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(m.name, fontSize = 13.sp)
                                                Text(
                                                    "${m.provider} · ${m.description}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF71717a)
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedModel = m.id
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Copy last message
                        IconButton(onClick = {
                            if (messages.isNotEmpty()) {
                                val last = messages.last()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("message", last.content)
                                clipboard.setPrimaryClip(clip)
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = Color(0xFFa1a1aa), modifier = Modifier.size(20.dp))
                        }

                        // Export
                        IconButton(onClick = {
                            if (messages.isNotEmpty()) {
                                val text = messages.joinToString("\n\n") { m ->
                                    "${if (m.isUser) "You" else "Calyth"}: ${m.content}"
                                }
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share chat"))
                            }
                        }) {
                            Icon(Icons.Default.Share, "Export", tint = Color(0xFFa1a1aa), modifier = Modifier.size(20.dp))
                        }

                        // Settings
                        IconButton(onClick = { showSettings = !showSettings }) {
                            Icon(Icons.Default.Settings, "Settings", tint = Color(0xFFa1a1aa), modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF18181b)
                    )
                )
            },
            containerColor = Color(0xFF09090b)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Settings panel
                AnimatedVisibility(visible = showSettings) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF18181b),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Backend URL", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFa1a1aa))
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF6366f1),
                                    unfocusedBorderColor = Color(0xFF27272a),
                                    cursorColor = Color(0xFF6366f1)
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    ApiClient.setServerUrl(serverUrl)
                                    showSettings = false
                                    scope.launch { models = ApiClient.getModels() }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366f1)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }

                // Welcome screen
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Gradient icon
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF6366f1), Color(0xFFa855f7))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚡", fontSize = 28.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "How can I help you?",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Choose a model and start chatting.",
                                fontSize = 14.sp,
                                color = Color(0xFF71717a)
                            )
                            Spacer(Modifier.height(20.dp))

                            // Quick actions
                            val quickActions = listOf(
                                "💡 Explain something" to "Explain quantum computing in simple terms",
                                "💻 Write code" to "Write a Python function to sort a list",
                                "🔍 Compare ideas" to "What are the pros and cons of microservices?",
                                "✉️ Draft an email" to "Help me write a professional email"
                            )
                            quickActions.forEach { (label, prompt) ->
                                Surface(
                                    modifier = Modifier
                                        .padding(horizontal = 32.dp, vertical = 3.dp)
                                        .fillMaxWidth()
                                        .clickable { sendMessage(prompt) },
                                    color = Color(0xFF27272a),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        fontSize = 12.sp,
                                        color = Color(0xFFa1a1aa)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Messages
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(messages) { msg ->
                            MessageBubble(msg, models)
                        }

                        if (isLoading) {
                            item {
                                TypingIndicator()
                            }
                        }
                    }
                }

                // Stop button
                if (isLoading) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 4.dp),
                        color = Color(0xFF27272a),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { isLoading = false }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "Stop",
                                tint = Color(0xFFa1a1aa),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Stop generating", fontSize = 13.sp, color = Color(0xFFa1a1aa))
                        }
                    }
                }

                // Input
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF09090b)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF18181b),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF27272a))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text("Message Calyth...", fontSize = 14.sp, color = Color(0xFF71717a))
                                    },
                                    maxLines = 4,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (inputText.isNotBlank() && !isLoading) {
                                            val msg = inputText.trim()
                                            inputText = ""
                                            focusManager.clearFocus()
                                            sendMessage(msg)
                                        }
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = Color(0xFF6366f1),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )

                                Surface(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (inputText.isNotBlank() && !isLoading) {
                                                val msg = inputText.trim()
                                                inputText = ""
                                                focusManager.clearFocus()
                                                sendMessage(msg)
                                            }
                                        },
                                    color = if (inputText.isNotBlank()) Color(0xFF6366f1) else Color(0xFF27272a)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        "Send",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(16.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            "Press Enter to send",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            fontSize = 11.sp,
                            color = Color(0xFF52525b),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, models: List<ModelInfo>) {
    val isUser = message.isUser
    val avatarBg = if (isUser) {
        Brush.linearGradient(listOf(Color(0xFF6366f1), Color(0xFF818cf8)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF27272a), Color(0xFF3f3f46)))
    }
    val avatarIcon = if (isUser) "👤" else if (message.model != null) "⚡" else "⚠"
    val name = if (isUser) "You" else "Calyth"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(avatarIcon, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFa1a1aa))
                if (message.model != null) {
                    val provider = models.find { it.id == message.model }?.provider
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            provider == "google" -> Color(0xFF1a3a1a)
                            else -> Color(0xFF1e293b)
                        }
                    ) {
                        Text(
                            message.model,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            color = when {
                                provider == "google" -> Color(0xFF4ade80)
                                else -> Color(0xFF818cf8)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 14.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 14.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp
                ),
                color = if (isUser) Color(0xFF6366f1) else Color(0xFF1e1e2e)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Color(0xFFe4e4e7)
                )
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(avatarIcon, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF27272a), Color(0xFF3f3f46)))),
            contentAlignment = Alignment.Center
        ) {
            Text("⚡", fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1e1e2e)
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                repeat(3) { i ->
                    val infiniteTransition = rememberInfiniteTransition()
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = i * 200),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366f1).copy(alpha = alpha))
                    )
                }
            }
        }
    }
}
