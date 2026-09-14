package com.calyth.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calyth.app.ApiClient
import com.calyth.app.ModelInfo
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null
)

data class Conversation(
    val id: Long = System.currentTimeMillis(),
    val title: String = "New chat",
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val folder: String? = null
)

object ChatStore {
    private const val KEY_CONVERSATIONS = "calyth_conversations"
    private const val KEY_FOLDERS = "calyth_folders"

    fun save(context: Context, conversations: List<Conversation>, folders: List<String>) {
        val prefs = context.getSharedPreferences("calyth", Context.MODE_PRIVATE)
        val arr = JSONArray()
        conversations.forEach { c ->
            val msgs = JSONArray()
            c.messages.forEach { m ->
                msgs.put(JSONObject().apply {
                    put("content", m.content)
                    put("isUser", m.isUser)
                    put("model", m.model ?: "")
                    put("ts", m.timestamp)
                    put("img", m.imageBase64 ?: "")
                })
            }
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("messages", msgs)
                put("folder", c.folder ?: "")
            })
        }
        prefs.edit().putString(KEY_CONVERSATIONS, arr.toString())
            .putString(KEY_FOLDERS, JSONArray(folders).toString())
            .apply()
    }

    fun load(context: Context): Pair<List<Conversation>, List<String>> {
        val prefs = context.getSharedPreferences("calyth", Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONVERSATIONS, "[]") ?: "[]"
        val foldersJson = prefs.getString(KEY_FOLDERS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val conversations = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val msgs = obj.getJSONArray("messages")
            val messages = (0 until msgs.length()).map { j ->
                val m = msgs.getJSONObject(j)
                ChatMessage(
                    content = m.getString("content"),
                    isUser = m.getBoolean("isUser"),
                    model = m.getString("model").ifBlank { null },
                    timestamp = m.optLong("ts", 0),
                    imageBase64 = m.optString("img").ifBlank { null }
                )
            }.toMutableList()
            Conversation(
                id = obj.getLong("id"),
                title = obj.getString("title"),
                messages = messages,
                folder = obj.optString("folder").ifBlank { null }
            )
        }
        val folders = (0 until JSONArray(foldersJson).length()).map { JSONArray(foldersJson).getString(it) }
        return conversations to folders
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (savedConversations, savedFolders) = remember { ChatStore.load(context) }
    var conversations by remember { mutableStateOf(savedConversations) }
    var folders by remember { mutableStateOf(savedFolders) }
    var activeChat by remember { mutableStateOf<Conversation?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(listOf<ModelInfo>()) }
    var selectedModel by remember { mutableStateOf("openai/gpt-oss-20b") }
    var showDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var webSearchEnabled by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var serverUrl by remember { mutableStateOf("https://calyth.onrender.com") }
    var pendingImage by remember { mutableStateOf<Pair<String, String>?>(null) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(conversations, folders) { ChatStore.save(context, conversations, folders) }

    var isRecording by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val fileName = it.lastPathSegment ?: "image"
            inputStream?.use { stream -> pendingImage = fileName to ApiClient.imageToBase64(stream) }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    LaunchedEffect(showDrawer) { if (showDrawer) drawerState.open() else drawerState.close() }

    LaunchedEffect(Unit) {
        ApiClient.setServerUrl(serverUrl)
        models = ApiClient.getModels()
        if (models.isNotEmpty() && selectedModel == "openai/gpt-oss-20b") selectedModel = models.first().id
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    val filteredMessages = if (searchQuery.isNotBlank()) messages.filter { it.content.contains(searchQuery, ignoreCase = true) } else messages

    fun sendMessage(text: String) {
        val img = pendingImage?.second
        pendingImage = null
        scope.launch {
            if (activeChat == null) {
                val newChat = Conversation()
                conversations = listOf(newChat) + conversations
                activeChat = newChat
                messages = emptyList()
            }
            val chatMsg = ChatMessage(text, true, imageBase64 = img)
            messages = messages + chatMsg
            activeChat?.messages?.add(chatMsg)
            activeChat = activeChat?.copy(title = (text.take(40) + if (img != null) " 📷" else ""))
            isLoading = true

            val onUpdate: (String) -> Unit = { token ->
                val last = messages.lastOrNull()
                if (last != null && !last.isUser && last.model == selectedModel) {
                    messages = messages.dropLast(1) + last.copy(content = last.content + token)
                    activeChat?.messages?.lastOrNull()?.let { lastMsg ->
                        if (!lastMsg.isUser) {
                            val idx = activeChat!!.messages.indexOf(lastMsg)
                            if (idx >= 0) activeChat!!.messages[idx] = lastMsg.copy(content = lastMsg.content + token)
                        }
                    }
                } else {
                    val newMsg = ChatMessage(token, false, selectedModel)
                    messages = messages + newMsg
                    activeChat?.messages?.add(newMsg)
                }
            }
            val onDone: () -> Unit = { isLoading = false }
            val onError: (String) -> Unit = { err ->
                messages = messages + ChatMessage("Error: $err", false)
                isLoading = false
            }

            if (webSearchEnabled) {
                val reply = ApiClient.searchWeb(selectedModel, text)
                messages = messages + ChatMessage(reply, false, selectedModel)
                activeChat?.messages?.add(ChatMessage(reply, false, selectedModel))
                isLoading = false
            } else if (img != null) {
                val reply = ApiClient.uploadImage(selectedModel, text, img)
                messages = messages + ChatMessage(reply, false, selectedModel)
                activeChat?.messages?.add(ChatMessage(reply, false, selectedModel))
                isLoading = false
            } else {
                ApiClient.sendStreaming(selectedModel, text, null, onUpdate, onDone, onError)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showDrawer,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp), drawerContainerColor = if (isDarkTheme) Color(0xFF18181b) else Color.White) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡ Calyth", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    IconButton(onClick = { showDrawer = false }) { Icon(Icons.Default.KeyboardArrowLeft, "Close", tint = Color(0xFF71717a)) }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, "Theme", tint = Color(0xFFa1a1aa), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDarkTheme) "Dark mode" else "Light mode", fontSize = 13.sp, color = Color(0xFFa1a1aa), modifier = Modifier.clickable { isDarkTheme = !isDarkTheme })
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable {
                        val newChat = Conversation(); conversations = listOf(newChat) + conversations; activeChat = newChat; messages = emptyList(); showDrawer = false
                    },
                    color = Color(0xFF27272a), shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFFa1a1aa), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("New Chat", fontSize = 13.sp, color = Color(0xFFa1a1aa))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Recent", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF71717a), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

                LazyColumn {
                    items(conversations) { chat ->
                        val isActive = activeChat?.id == chat.id
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clickable { activeChat = chat; messages = chat.messages.toList(); showDrawer = false },
                            color = if (isActive) Color(0xFF6366f1).copy(alpha = 0.12f) else Color.Transparent, shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(Modifier.padding(10.dp, 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("💬", fontSize = 14.sp); Spacer(Modifier.width(8.dp))
                                Text(chat.title, fontSize = 13.sp, color = if (isActive) Color(0xFF818cf8) else Color(0xFFa1a1aa), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { conversations = conversations.filter { it.id != chat.id }; if (activeChat?.id == chat.id) { activeChat = null; messages = emptyList() } }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, "Delete", tint = Color(0xFF52525b), modifier = Modifier.size(14.dp))
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
                            Text(model?.name ?: "Calyth", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(model?.provider ?: "AI Assistant", fontSize = 11.sp, color = Color(0xFF71717a))
                        }
                    },
                    navigationIcon = { IconButton(onClick = { showDrawer = !showDrawer }) { Icon(Icons.Default.Menu, "Menu", tint = Color(0xFFa1a1aa)) } },
                    actions = {
                        var modelExpanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { modelExpanded = true }) { Text(models.find { it.id == selectedModel }?.name?.take(15) ?: "Model", color = Color(0xFF6366f1), fontSize = 12.sp) }
                            DropdownMenu(modelExpanded, { modelExpanded = false }) {
                                models.forEach { m -> DropdownMenuItem(text = { Column { Text(m.name, fontSize = 13.sp); Text("${m.provider} · ${m.description}", fontSize = 10.sp, color = Color(0xFF71717a)) } }, onClick = { selectedModel = m.id; modelExpanded = false }) }
                            }
                        }
                        IconButton(onClick = { webSearchEnabled = !webSearchEnabled }) { Icon(Icons.Default.Search, "Web Search", tint = if (webSearchEnabled) Color(0xFF6366f1) else Color(0xFFa1a1aa), modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Info, "Search", tint = if (showSearch) Color(0xFF6366f1) else Color(0xFFa1a1aa), modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { if (messages.isNotEmpty()) { val text = messages.joinToString("\n\n") { m -> "${if (m.isUser) "You" else "Calyth"}: ${m.content}" }; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share chat")) } }) { Icon(Icons.Default.Share, "Export", tint = Color(0xFFa1a1aa), modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Settings, "Settings", tint = Color(0xFFa1a1aa), modifier = Modifier.size(20.dp)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isDarkTheme) Color(0xFF18181b) else Color.White)
                )
            },
            containerColor = if (isDarkTheme) Color(0xFF09090b) else Color(0xFFFAFAFA)
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                AnimatedVisibility(visible = showSearch) {
                    Surface(color = if (isDarkTheme) Color(0xFF18181b) else Color.White, tonalElevation = 2.dp) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search...", fontSize = 13.sp) }, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6366f1), unfocusedBorderColor = Color(0xFF27272a), cursorColor = Color(0xFF6366f1)))
                            Spacer(Modifier.width(8.dp)); Text("${filteredMessages.size}", fontSize = 12.sp, color = Color(0xFF71717a))
                        }
                    }
                }

                AnimatedVisibility(visible = showSettings) {
                    Surface(color = if (isDarkTheme) Color(0xFF18181b) else Color.White, tonalElevation = 2.dp) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Backend URL", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFa1a1aa))
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6366f1), unfocusedBorderColor = Color(0xFF27272a), cursorColor = Color(0xFF6366f1)))
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { ApiClient.setServerUrl(serverUrl); showSettings = false; scope.launch { models = ApiClient.getModels() } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366f1)), shape = RoundedCornerShape(10.dp)) { Text("Connect") }
                        }
                    }
                }

                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF6366f1), Color(0xFFa855f7)))), contentAlignment = Alignment.Center) { Text("⚡", fontSize = 28.sp) }
                            Spacer(Modifier.height(16.dp))
                            Text("How can I help you?", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkTheme) Color.White else Color.Black)
                            Spacer(Modifier.height(6.dp)); Text("Choose a model and start chatting.", fontSize = 14.sp, color = Color(0xFF71717a))
                            Spacer(Modifier.height(20.dp))
                            listOf("💡 Explain something" to "Explain quantum computing in simple terms", "💻 Write code" to "Write a Python function to sort a list", "🔍 Compare ideas" to "What are the pros and cons of microservices?", "✉️ Draft an email" to "Help me write a professional email").forEach { (label, prompt) ->
                                Surface(modifier = Modifier.padding(horizontal = 32.dp, vertical = 3.dp).fillMaxWidth().clickable { sendMessage(prompt) }, color = if (isDarkTheme) Color(0xFF27272a) else Color(0xFFF4F4F5), shape = RoundedCornerShape(20.dp)) {
                                    Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp, color = Color(0xFFa1a1aa))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                        items(messages) { msg ->
                            AndroidMessageBubble(msg, models, isDarkTheme, onRegenerate = {
                                if (!isLoading) {
                                    val idx = messages.indexOf(msg)
                                    if (idx >= 0) {
                                        val userMsg = messages.subList(0, idx + 1).lastOrNull { it.isUser }
                                        if (userMsg != null) { messages = messages.subList(0, idx); scope.launch { sendMessage(userMsg.content) } }
                                    }
                                }
                            }, onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("msg", msg.content))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            })
                        }
                        if (isLoading) item { AndroidTypingIndicator(isDarkTheme) }
                    }
                }

                Surface(modifier = Modifier.fillMaxWidth(), color = if (isDarkTheme) Color(0xFF09090b) else Color(0xFFFAFAFA)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        pendingImage?.let { (name, _) ->
                            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, null, tint = Color(0xFF6366f1), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text(name, fontSize = 12.sp, color = Color(0xFFa1a1aa), modifier = Modifier.weight(1f))
                                IconButton(onClick = { pendingImage = null }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, "Remove", tint = Color(0xFF71717a), modifier = Modifier.size(14.dp)) }
                            }
                        }

                        Surface(modifier = Modifier.fillMaxWidth(), color = if (isDarkTheme) Color(0xFF18181b) else Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (isDarkTheme) Color(0xFF27272a) else Color(0xFFE4E4E7))) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                                Row { IconButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, "Attach", tint = Color(0xFFa1a1aa), modifier = Modifier.size(18.dp)) } }
                                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Calyth...", fontSize = 14.sp, color = if (isDarkTheme) Color(0xFF71717a) else Color(0xFFA1A1AA)) }, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank() && !isLoading) { val msg = inputText.trim(); inputText = ""; focusManager.clearFocus(); sendMessage(msg) } }), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = Color(0xFF6366f1), focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDarkTheme) Color.White else Color.Black))
                                Surface(modifier = Modifier.size(36.dp).padding(4.dp).clip(RoundedCornerShape(10.dp)).clickable { if (inputText.isNotBlank() && !isLoading) { val msg = inputText.trim(); inputText = ""; focusManager.clearFocus(); sendMessage(msg) } }, color = if (inputText.isNotBlank()) Color(0xFF6366f1) else if (isDarkTheme) Color(0xFF27272a) else Color(0xFFF4F4F5)) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.padding(8.dp).size(16.dp))
                                }
                            }
                        }
                        Text("Enter to send · Attach with ➕", modifier = Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 11.sp, color = Color(0xFF52525b), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun AndroidMessageBubble(message: ChatMessage, models: List<ModelInfo>, isDarkTheme: Boolean, onRegenerate: () -> Unit, onCopy: () -> Unit) {
    val isUser = message.isUser
    val avatarBg = if (isUser) Brush.linearGradient(listOf(Color(0xFF6366f1), Color(0xFF818cf8))) else Brush.linearGradient(listOf(Color(0xFF27272a), Color(0xFF3f3f46)))
    val avatarIcon = if (isUser) "👤" else if (message.model != null) "⚡" else "⚠"
    val name = if (isUser) "You" else "Calyth"
    val textColor = if (isDarkTheme) Color(0xFFe4e4e7) else Color(0xFF3F3F46)
    val subtextColor = if (isDarkTheme) Color(0xFFa1a1aa) else Color(0xFF71717a)

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 12.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) { Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(avatarBg), contentAlignment = Alignment.Center) { Text(avatarIcon, fontSize = 13.sp) }; Spacer(Modifier.width(8.dp)) }
        Column(modifier = Modifier.widthIn(max = 290.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = subtextColor)
                if (message.model != null) {
                    val provider = models.find { it.id == message.model }?.provider
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = when { provider == "Google" -> Color(0xFF1a3a1a); else -> Color(0xFF1e293b) }) {
                        Text(message.model, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, color = when { provider == "Google" -> Color(0xFF4ade80); else -> Color(0xFF818cf8) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(topStart = if (isUser) 14.dp else 4.dp, topEnd = if (isUser) 4.dp else 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp), color = if (isUser) Color(0xFF6366f1) else if (isDarkTheme) Color(0xFF1e1e2e) else Color(0xFFF4F4F5)) {
                Text(text = message.content, modifier = Modifier.padding(12.dp), fontSize = 14.sp, lineHeight = 21.sp, color = textColor)
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = onCopy, label = { Text("Copy", fontSize = 10.sp) }, modifier = Modifier.height(24.dp), shape = RoundedCornerShape(6.dp), colors = AssistChipDefaults.assistChipColors(containerColor = if (isDarkTheme) Color(0xFF27272a) else Color(0xFFF4F4F5)))
                if (!isUser) AssistChip(onClick = onRegenerate, label = { Text("Regen", fontSize = 10.sp) }, modifier = Modifier.height(24.dp), shape = RoundedCornerShape(6.dp), colors = AssistChipDefaults.assistChipColors(containerColor = if (isDarkTheme) Color(0xFF27272a) else Color(0xFFF4F4F5)))
            }
        }
        if (isUser) { Spacer(Modifier.width(8.dp)); Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(avatarBg), contentAlignment = Alignment.Center) { Text(avatarIcon, fontSize = 13.sp) } }
    }
}

@Composable
fun AndroidTypingIndicator(isDarkTheme: Boolean) {
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF27272a), Color(0xFF3f3f46)))), contentAlignment = Alignment.Center) { Text("⚡", fontSize = 13.sp) }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = if (isDarkTheme) Color(0xFF1e1e2e) else Color(0xFFF4F4F5)) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                repeat(3) { i ->
                    val infiniteTransition = rememberInfiniteTransition()
                    val alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = i * 200), repeatMode = RepeatMode.Reverse))
                    Box(modifier = Modifier.padding(horizontal = 2.dp).size(7.dp).clip(CircleShape).background(Color(0xFF6366f1).copy(alpha = alpha)))
                }
            }
        }
    }
}
