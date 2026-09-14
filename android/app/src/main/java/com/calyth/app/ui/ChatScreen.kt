package com.calyth.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.List
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

data class ChatMessage(val content: String, val isUser: Boolean, val model: String? = null, val timestamp: Long = System.currentTimeMillis(), val imageBase64: String? = null)
data class Conversation(val id: Long = System.currentTimeMillis(), val title: String = "New chat", val messages: MutableList<ChatMessage> = mutableListOf(), val folder: String? = null)

object ChatStore {
    fun save(ctx: Context, chats: List<Conversation>) {
        val arr = JSONArray()
        chats.forEach { c ->
            val msgs = JSONArray()
            c.messages.forEach { m -> msgs.put(JSONObject().apply { put("c", m.content); put("u", m.isUser); put("m", m.model ?: ""); put("t", m.timestamp); put("i", m.imageBase64 ?: "") }) }
            arr.put(JSONObject().apply { put("id", c.id); put("t", c.title); put("msgs", msgs) })
        }
        ctx.getSharedPreferences("calyth", Context.MODE_PRIVATE).edit().putString("chats", arr.toString()).apply()
    }
    fun load(ctx: Context): List<Conversation> {
        val json = ctx.getSharedPreferences("calyth", Context.MODE_PRIVATE).getString("chats", "[]") ?: "[]"
        return JSONArray(json).let { arr -> (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val msgs = o.getJSONArray("msgs")
            Conversation(o.getLong("id"), o.getString("t"), (0 until msgs.length()).map { j ->
                val m = msgs.getJSONObject(j); ChatMessage(m.getString("c"), m.getBoolean("u"), m.getString("m").ifBlank { null }, m.optLong("t", 0), m.optString("i").ifBlank { null })
            }.toMutableList())
        } }
    }
}

private val Purple = Color(0xFF6366f1)
private val Purple2 = Color(0xFF818cf8)
private val DarkBg = Color(0xFF09090b)
private val DarkSurface = Color(0xFF18181b)
private val DarkCard = Color(0xFF1e1e2e)
private val DarkBorder = Color(0xFF27272a)
private val LightBg = Color(0xFFF8FAFC)
private val LightSurface = Color(0xFFFFFFFF)
private val LightCard = Color(0xFFF1F5F9)
private val LightBorder = Color(0xFFE2E8F0)
private val Subtext = Color(0xFF71717a)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val saved = remember { ChatStore.load(ctx) }
    var conversations by remember { mutableStateOf(saved) }
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
    var isDark by remember { mutableStateOf(true) }
    var serverUrl by remember { mutableStateOf("https://calyth.onrender.com") }
    var pendingImage by remember { mutableStateOf<Pair<String, String>?>(null) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val card = if (isDark) DarkCard else LightCard
    val border = if (isDark) DarkBorder else LightBorder
    val fg = if (isDark) Color(0xFFfafafa) else Color(0xFF0f172a)
    val fg2 = if (isDark) Color(0xFFa1a1aa) else Color(0xFF475569)

    LaunchedEffect(conversations) { ChatStore.save(ctx, conversations) }
    LaunchedEffect(showDrawer) { if (showDrawer) drawerState.open() else drawerState.close() }
    LaunchedEffect(Unit) { ApiClient.setServerUrl(serverUrl); models = ApiClient.getModels(); if (models.isNotEmpty()) selectedModel = models.first().id }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { ctx.contentResolver.openInputStream(it)?.use { s -> pendingImage = (it.lastPathSegment ?: "image") to ApiClient.imageToBase64(s) } }
    }

    fun sendMessage(text: String) {
        val img = pendingImage?.second; pendingImage = null
        scope.launch {
            if (activeChat == null) { val nc = Conversation(); conversations = listOf(nc) + conversations; activeChat = nc; messages = emptyList() }
            messages = messages + ChatMessage(text, true, imageBase64 = img)
            activeChat?.messages?.add(ChatMessage(text, true, imageBase64 = img))
            activeChat = activeChat?.copy(title = text.take(40) + if (img != null) " 📷" else "")
            isLoading = true
            val onUpdate: (String) -> Unit = { token ->
                val last = messages.lastOrNull()
                if (last != null && !last.isUser) { messages = messages.dropLast(1) + last.copy(content = last.content + token) }
                else { messages = messages + ChatMessage(token, false, selectedModel) }
            }
            val onDone: () -> Unit = { isLoading = false }
            val onError: (String) -> Unit = { e -> messages = messages + ChatMessage("Error: $e", false); isLoading = false }
            if (webSearchEnabled) { val r = ApiClient.searchWeb(selectedModel, text); messages = messages + ChatMessage(r, false, selectedModel); isLoading = false }
            else if (img != null) { val r = ApiClient.uploadImage(selectedModel, text, img); messages = messages + ChatMessage(r, false, selectedModel); isLoading = false }
            else {
                try {
                    ApiClient.sendStreaming(selectedModel, text, null, onUpdate, onDone, onError)
                } catch (e: Exception) {
                    // Fallback to non-streaming /chat endpoint
                    val r = ApiClient.sendMessage(selectedModel, text, null)
                    messages = messages + ChatMessage(r, false, selectedModel)
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(showDrawer) { if (showDrawer) drawerState.open() else drawerState.close() }

    ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = showDrawer, drawerContent = {
        ModalDrawerSheet(modifier = Modifier.width(280.dp), drawerContainerColor = surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Calyth", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = fg)
                IconButton(onClick = { showDrawer = false }) { Icon(Icons.Default.Close, "Close", tint = fg2) }
            }
            HorizontalDivider(color = border)
            Spacer(Modifier.height(8.dp))
            // Theme toggle
            Row(Modifier.fillMaxWidth().clickable { isDark = !isDark }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isDark) Icons.Default.Settings else Icons.Default.Settings, null, tint = fg2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp)); Text(if (isDark) "Dark mode" else "Light mode", fontSize = 14.sp, color = fg2)
            }
            // New chat
            FilledTonalButton(onClick = { val nc = Conversation(); conversations = listOf(nc) + conversations; activeChat = nc; messages = emptyList(); showDrawer = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("New Chat", fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Recent", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Subtext, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(conversations) { chat ->
                    val isActive = activeChat?.id == chat.id
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).clickable { activeChat = chat; messages = chat.messages.toList(); showDrawer = false }, color = if (isActive) Purple.copy(alpha = .1f) else Color.Transparent, shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, null, tint = if (isActive) Purple2 else fg2, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(chat.title, fontSize = 13.sp, color = if (isActive) Purple2 else fg2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { conversations = conversations.filter { it.id != chat.id }; if (activeChat?.id == chat.id) { activeChat = null; messages = emptyList() } }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, "Delete", tint = Subtext, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
        }
    }) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column { val m = models.find { it.id == selectedModel }; Text(m?.name ?: "Calyth", fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(m?.provider ?: "AI Assistant", fontSize = 11.sp, color = Subtext) }
                }, navigationIcon = { IconButton(onClick = { showDrawer = !showDrawer }) { Icon(Icons.Default.Menu, "Menu", tint = fg2) } },
                    actions = {
                        var exp by remember { mutableStateOf(false) }
                        Box { TextButton(onClick = { exp = true }) { Text(models.find { it.id == selectedModel }?.name?.take(12) ?: "Model", color = Purple, fontSize = 12.sp) }
                            DropdownMenu(exp, { exp = false }) { models.forEach { m -> DropdownMenuItem(text = { Column { Text(m.name, fontSize = 13.sp); Text("${m.provider} · ${m.description}", fontSize = 10.sp, color = Subtext) } }, onClick = { selectedModel = m.id; exp = false }) } }
                        }
                        IconButton(onClick = { webSearchEnabled = !webSearchEnabled }) { Icon(Icons.Default.Search, "Search web", tint = if (webSearchEnabled) Purple else fg2, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, "Filter", tint = if (showSearch) Purple else fg2, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { if (messages.isNotEmpty()) { val t = messages.joinToString("\n\n") { m -> "${if (m.isUser) "You" else "Calyth"}: ${m.content}" }; ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) }, "Share")) } }) { Icon(Icons.Default.Share, "Share", tint = fg2, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Settings, "Settings", tint = fg2, modifier = Modifier.size(20.dp)) }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = surface))
            }, containerColor = bg
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Search
                AnimatedVisibility(showSearch) { Surface(color = surface, tonalElevation = 2.dp) { OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), placeholder = { Text("Search...", fontSize = 13.sp) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = border, cursorColor = Purple)) } }
                // Settings
                AnimatedVisibility(showSettings) { Surface(color = surface, tonalElevation = 2.dp) { Column(Modifier.padding(16.dp)) { Text("Backend URL", fontSize = 12.sp, color = fg2); Spacer(Modifier.height(4.dp)); OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = border, cursorColor = Purple)); Spacer(Modifier.height(8.dp)); Button(onClick = { ApiClient.setServerUrl(serverUrl); showSettings = false; scope.launch { models = ApiClient.getModels() } }, colors = ButtonDefaults.buttonColors(containerColor = Purple), shape = RoundedCornerShape(10.dp)) { Text("Connect") } } } }

                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFFa855f7)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                            Spacer(Modifier.height(14.dp)); Text("How can I help you?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = fg)
                            Spacer(Modifier.height(4.dp)); Text("Choose a model and start chatting.", fontSize = 13.sp, color = fg2)
                            Spacer(Modifier.height(16.dp))
                            listOf("Explain something" to "Explain quantum computing in simple terms", "Write code" to "Write a Python function to sort a list", "Compare ideas" to "What are the pros and cons of microservices?", "Draft an email" to "Help me write a professional email").forEach { (l, p) ->
                                OutlinedButton(onClick = { sendMessage(p) }, modifier = Modifier.padding(horizontal = 32.dp, vertical = 2.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, border)) { Text(l, fontSize = 12.sp, color = fg2) }
                            }
                        }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        items(messages) { msg -> MsgBubble(msg, models, isDark, onRegen = { if (!isLoading) { val idx = messages.indexOf(msg); val um = messages.subList(0, idx + 1).lastOrNull { it.isUser }; if (um != null) { messages = messages.subList(0, idx); scope.launch { sendMessage(um.content) } } } }, onCopy = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("msg", msg.content)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) }
                        if (isLoading) item { TypingIndicator(isDark) }
                    }
                }

                // Input
                Surface(Modifier.fillMaxWidth(), color = bg) {
                    Column(Modifier.padding(12.dp)) {
                        pendingImage?.let { (n, _) -> Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bolt, null, tint = Purple, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(n, fontSize = 12.sp, color = fg2, modifier = Modifier.weight(1f)); IconButton(onClick = { pendingImage = null }, Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = Subtext, modifier = Modifier.size(14.dp)) } } }
                        Surface(Modifier.fillMaxWidth(), color = surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, border)) {
                            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.Bottom) {
                                IconButton(onClick = { imagePicker.launch("image/*") }, Modifier.size(32.dp)) { Icon(Icons.Default.Add, "Attach", tint = fg2, modifier = Modifier.size(18.dp)) }
                                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Calyth...", fontSize = 14.sp, color = Subtext) }, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank() && !isLoading) { val m = inputText.trim(); inputText = ""; focusManager.clearFocus(); sendMessage(m) } }), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = Purple, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent), textStyle = MaterialTheme.typography.bodyMedium.copy(color = fg))
                                FilledIconButton(onClick = { if (inputText.isNotBlank() && !isLoading) { val m = inputText.trim(); inputText = ""; focusManager.clearFocus(); sendMessage(m) } }, modifier = Modifier.size(34.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (inputText.isNotBlank()) Purple else border)) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                        }
                        Text("Enter to send · Attach with paperclip", Modifier.fillMaxWidth().padding(top = 4.dp), fontSize = 11.sp, color = Subtext, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun MsgBubble(msg: ChatMessage, models: List<ModelInfo>, isDark: Boolean, onRegen: () -> Unit, onCopy: () -> Unit) {
    val isUser = msg.isUser
    val purple = Brush.linearGradient(listOf(Purple, Purple2))
    val grey = Brush.linearGradient(listOf(DarkBorder, Color(0xFF3f3f46)))
    val fg = if (isDark) Color(0xFFe4e4e7) else Color(0xFF1e293b)
    val fg2 = if (isDark) Color(0xFFa1a1aa) else Color(0xFF64748b)
    val card = if (isDark) DarkCard else LightCard
    val border = if (isDark) DarkBorder else LightBorder

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) { Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(grey), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Purple2, modifier = Modifier.size(14.dp)) }; Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 280.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isUser) "You" else "Calyth", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg2)
                msg.model?.let { m -> val p = models.find { it.id == m }?.provider; Spacer(Modifier.width(6.dp)); Surface(shape = RoundedCornerShape(4.dp), color = if (p == "Google") Color(0xFF1a3a1a) else Color(0xFF1e293b)) { Text(m, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 9.sp, color = if (p == "Google") Color(0xFF4ade80) else Purple2) } }
            }
            Spacer(Modifier.height(3.dp))
            Surface(shape = RoundedCornerShape(if (isUser) 12.dp else 4.dp, if (isUser) 4.dp else 12.dp, 12.dp, 12.dp), color = if (isUser) Purple else card) {
                Text(msg.content, Modifier.padding(10.dp), fontSize = 14.sp, lineHeight = 20.sp, color = if (isUser) Color.White else fg)
            }
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                AssistChip(onClick = onCopy, label = { Text("Copy", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(12.dp)) }, modifier = Modifier.height(24.dp), shape = RoundedCornerShape(6.dp), colors = AssistChipDefaults.assistChipColors(containerColor = border))
                if (!isUser) AssistChip(onClick = onRegen, label = { Text("Retry", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Bolt, null, Modifier.size(12.dp)) }, modifier = Modifier.height(24.dp), shape = RoundedCornerShape(6.dp), colors = AssistChipDefaults.assistChipColors(containerColor = border))
            }
        }
        if (isUser) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(purple), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(14.dp)) } }
    }
}

@Composable
fun TypingIndicator(isDark: Boolean) {
    val card = if (isDark) DarkCard else LightCard
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(DarkBorder, Color(0xFF3f3f46)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Purple2, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = card) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                repeat(3) { i -> val t = rememberInfiniteTransition(); val a by t.animateFloat(.3f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse)); Box(Modifier.padding(horizontal = 2.dp).size(6.dp).clip(CircleShape).background(Purple.copy(alpha = a))) }
            }
        }
    }
}
