package com.calyth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

fun loadEnv() {
    val envFile = File(".env")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val key = trimmed.substring(0, eq).trim()
                    val value = trimmed.substring(eq + 1).trim()
                    if (System.getenv(key) == null) {
                        System.setProperty(key, value)
                    }
                }
            }
        }
    }
}

fun env(key: String): String = System.getenv(key) ?: System.getProperty(key) ?: ""

fun main() {
    loadEnv()
    val port = (System.getenv("PORT") ?: System.getProperty("PORT") ?: "8082").toInt()
    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

// ── Models & Providers ────────────────────────────────────────────────

@Serializable
data class ModelInfo(val id: String, val name: String, val provider: String, val description: String)

val availableModels = listOf(
    ModelInfo("openai/gpt-oss-20b", "GPT-OSS 20B", "Groq", "Fast, general-purpose model"),
    ModelInfo("openai/gpt-oss-120b", "GPT-OSS 120B", "Groq", "Larger, more capable GPT"),
    ModelInfo("qwen/qwen3.6-27b", "Qwen 3.6 27B", "Groq", "Strong reasoning & coding"),
    ModelInfo("groq/compound", "Groq Compound", "Groq", "Multi-step reasoning"),
    ModelInfo("groq/compound-mini", "Groq Compound Mini", "Groq", "Lightweight multi-step reasoning"),
    ModelInfo("allam-2-7b", "Allam 2 7B", "Groq", "Arabic-first, lightweight"),
    ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "Google", "Fast, free tier (needs GEMINI_API_KEY)"),
    ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", "Google", "High quality (needs GEMINI_API_KEY)")
)

// ── Request / Response DTOs ───────────────────────────────────────────

@Serializable
data class ChatRequest(val model: String = "openai/gpt-oss-20b", val message: String, val system: String? = null, val stream: Boolean = false)

@Serializable
data class ChatRequestWithHistory(val model: String = "openai/gpt-oss-20b", val message: String, val history: List<Message> = emptyList(), val system: String? = null)

@Serializable
data class GroqRequest(val model: String, val messages: List<Message>, val stream: Boolean = false)

@Serializable
data class GroqStreamRequest(val model: String, val messages: List<Message>, val stream: Boolean = true)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class GroqResponse(val choices: List<Choice> = emptyList(), val error: GroqError? = null)

@Serializable
data class GroqError(val message: String? = null, val code: String? = null)

@Serializable
data class Choice(val message: Message? = null, val delta: Delta? = null, val finish_reason: String? = null)

@Serializable
data class Delta(val role: String? = null, val content: String? = null)

@Serializable
data class StreamChunk(val choices: List<Choice> = emptyList())

// ── Gemini DTOs ───────────────────────────────────────────────────────

@Serializable
data class GeminiRequest(val contents: List<Content>)

@Serializable
data class Content(val parts: List<Part>)

@Serializable
data class Part(val text: String)

@Serializable
data class GeminiResponse(val candidates: List<Candidate> = emptyList())

@Serializable
data class Candidate(val content: Content)

// ── Conversation Sharing ──────────────────────────────────────────────

@Serializable
data class SharedChat(val id: String, val title: String, val messages: List<Message>, val createdAt: Long)

// ── File Upload DTO ───────────────────────────────────────────────────

@Serializable
data class VisionRequest(val model: String, val message: String, val imageBase64: String? = null)

// ── In-memory shared chats store ──────────────────────────────────────

val sharedChats = ConcurrentHashMap<String, SharedChat>()

// ── App Module ────────────────────────────────────────────────────────

fun Application.module() {
    val jsonConfig = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    install(ServerContentNegotiation) {
        json(jsonConfig)
    }
    install(SSE)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Delete)
    }
    val groqKey = env("GROQ_API_KEY")
    val geminiKey = env("GEMINI_API_KEY")
    val client = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(jsonConfig)
        }
        install(SSE)
    }

    routing {
        // ── Serve frontend ────────────────────────────────────────────
        get("/") {
            call.respondText(
                this::class.java.classLoader.getResource("index.html")!!.readText(),
                ContentType.Text.Html
            )
        }

        get("/health") {
            call.respondText("OK")
        }

        // ── List available models ─────────────────────────────────────
        get("/models") {
            call.respond(availableModels)
        }

        // ── Chat endpoint (non-streaming) ─────────────────────────────
        post("/chat") {
            val request = call.receive<ChatRequest>()
            val modelId = request.model

            val messages = mutableListOf<Message>()
            if (!request.system.isNullOrBlank()) {
                messages.add(Message(role = "system", content = request.system))
            }
            messages.add(Message(role = "user", content = request.message))

            val reply = when {
                modelId.startsWith("gemini") -> {
                    if (geminiKey.isBlank()) "Error: GEMINI_API_KEY not configured"
                    else callGemini(client, geminiKey, modelId, request.system, request.message)
                }
                else -> {
                    if (groqKey.isBlank()) "Error: GROQ_API_KEY not configured"
                    else callGroq(client, groqKey, modelId, messages)
                }
            }
            call.respondText(reply)
        }

        // ── Chat with history endpoint ────────────────────────────────
        post("/chat/history") {
            val request = call.receive<ChatRequestWithHistory>()
            val modelId = request.model

            val messages = mutableListOf<Message>()
            if (!request.system.isNullOrBlank()) {
                messages.add(Message(role = "system", content = request.system))
            }
            messages.addAll(request.history)
            messages.add(Message(role = "user", content = request.message))

            val reply = when {
                modelId.startsWith("gemini") -> {
                    if (geminiKey.isBlank()) "Error: GEMINI_API_KEY not configured"
                    else callGeminiWithHistory(client, geminiKey, modelId, messages)
                }
                else -> {
                    if (groqKey.isBlank()) "Error: GROQ_API_KEY not configured"
                    else callGroq(client, groqKey, modelId, messages)
                }
            }
            call.respondText(reply)
        }

        // ── Streaming chat endpoint (SSE) ─────────────────────────────
        post("/chat/stream") {
            val request = call.receive<ChatRequest>()
            val modelId = request.model

            val messages = mutableListOf<Message>()
            if (!request.system.isNullOrBlank()) {
                messages.add(Message(role = "system", content = request.system))
            }
            messages.add(Message(role = "user", content = request.message))

            if (modelId.startsWith("gemini")) {
                // Gemini doesn't support streaming well via this approach, fall back to non-streaming
                val reply = callGemini(client, geminiKey, modelId, request.system, request.message)
                call.respondText(reply)
                return@post
            }

            if (groqKey.isBlank()) {
                call.respondText("Error: GROQ_API_KEY not configured")
                return@post
            }

            call.respondSse {
                try {
                    client.sse("https://api.groq.com/openai/v1/chat/completions") {
                        setHeader("Authorization", "Bearer $groqKey")
                        setHeader("Content-Type", "application/json")
                        send(
                            buildString {
                                append("{")
                                append("\"model\":\"$modelId\",")
                                append("\"stream\":true,")
                                append("\"messages\":[")
                                messages.forEachIndexed { i, m ->
                                    if (i > 0) append(",")
                                    append("{\"role\":\"${m.role}\",\"content\":\"${m.content.jsonEscape()}\"}")
                                }
                                append("]")
                                append("}")
                            }
                        )
                        incoming.collect { event ->
                            val data = event.data ?: return@collect
                            if (data == "[DONE]") {
                                send(SseEvent("[DONE]", event = "done"))
                                return@collect
                            }
                            try {
                                val chunk = Json { ignoreUnknownKeys = true; isLenient = true }
                                    .decodeFromString<StreamChunk>(data)
                                val content = chunk.choices.firstOrNull()?.delta?.content
                                if (content != null) {
                                    send(SseEvent(content, event = "token"))
                                }
                                if (chunk.choices.firstOrNull()?.finish_reason != null) {
                                    send(SseEvent("[DONE]", event = "done"))
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    send(SseEvent("Error: ${e.message}", event = "error"))
                }
            }
        }

        // ── Web search endpoint ───────────────────────────────────────
        post("/search") {
            val request = call.receive<ChatRequest>()
            val searchPrompt = "Search the web and answer: ${request.message}"
            val messages = listOf(Message(role = "user", content = searchPrompt))
            val reply = callGroq(client, groqKey, "groq/compound", messages)
            call.respondText(reply)
        }

        // ── File upload endpoint (base64 image) ───────────────────────
        post("/upload") {
            val request = call.receive<VisionRequest>()
            val modelId = request.model

            val reply = when {
                modelId.startsWith("gemini") -> {
                    if (geminiKey.isBlank()) "Error: GEMINI_API_KEY not configured"
                    else callGeminiVision(client, geminiKey, modelId, request.message, request.imageBase64)
                }
                else -> {
                    // Groq vision models
                    if (groqKey.isBlank()) "Error: GROQ_API_KEY not configured"
                    else callGroqVision(client, groqKey, modelId, request.message, request.imageBase64)
                }
            }
            call.respondText(reply)
        }

        // ── Share conversation ────────────────────────────────────────
        post("/share") {
            val request = call.receive<SharedChat>()
            val id = if (request.id.isNotBlank()) request.id else UUID.randomUUID().toString().take(8)
            val shared = request.copy(id = id)
            sharedChats[id] = shared
            call.respond(mapOf("id" to id, "url" to "/shared/$id"))
        }

        get("/shared/{id}") {
            val id = call.parameters["id"] ?: ""
            val chat = sharedChats[id]
            if (chat != null) {
                call.respond(chat)
            } else {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
            }
        }

        get("/shared/{id}/html") {
            val id = call.parameters["id"] ?: ""
            val chat = sharedChats[id]
            if (chat != null) {
                val html = buildString {
                    append("<!DOCTYPE html><html><head><title>${chat.title}</title>")
                    append("<style>body{font-family:Inter,sans-serif;max-width:700px;margin:40px auto;padding:0 20px;background:#09090b;color:#fafafa}")
                    append(".msg{margin:16px 0;padding:12px;border-radius:10px}")
                    append(".user{background:#6366f1;color:white;margin-left:40px}")
                    append(".assistant{background:#1e1e2e;margin-right:40px}")
                    append("</style></head><body>")
                    append("<h1>${chat.title}</h1>")
                    chat.messages.forEach { m ->
                        val cls = if (m.role == "user") "user" else "assistant"
                        val name = if (m.role == "user") "You" else "Calyth"
                        append("<div class='msg $cls'><strong>$name</strong><br>${m.content}</div>")
                    }
                    append("<p style='text-align:center;color:#71717a;margin-top:40px'>Shared via Calyth</p>")
                    append("</body></html>")
                }
                call.respondText(html, ContentType.Text.Html)
            } else {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
            }
        }
    }
}

// ── Helper: escape JSON strings ────────────────────────────────────────

fun String.jsonEscape(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

// ── Provider calls ────────────────────────────────────────────────────

private suspend fun callGroq(
    client: HttpClient,
    apiKey: String,
    model: String,
    messages: List<Message>
): String {
    val groqRequest = GroqRequest(model = model, messages = messages)
    val response = client.post("https://api.groq.com/openai/v1/chat/completions") {
        header("Authorization", "Bearer $apiKey")
        contentType(ContentType.Application.Json)
        setBody(groqRequest)
    }.body<GroqResponse>()

    return when {
        response.error != null -> "Error: ${response.error.message}"
        response.choices.isNotEmpty() -> response.choices.first().message?.content ?: ""
        else -> "No response from model"
    }
}

private suspend fun callGemini(
    client: HttpClient,
    apiKey: String,
    model: String,
    system: String?,
    message: String
): String {
    val fullMessage = if (!system.isNullOrBlank()) "$system\n\n$message" else message
    val geminiRequest = GeminiRequest(
        contents = listOf(Content(parts = listOf(Part(text = fullMessage))))
    )
    val response = client.post(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    ) {
        url { parameter("key", apiKey) }
        contentType(ContentType.Application.Json)
        setBody(geminiRequest)
    }.body<GeminiResponse>()

    return response.candidates.firstOrNull()
        ?.content?.parts?.firstOrNull()?.text
        ?: "No response from Gemini"
}

private suspend fun callGeminiWithHistory(
    client: HttpClient,
    apiKey: String,
    model: String,
    messages: List<Message>
): String {
    val contents = messages.filter { it.role != "system" }.map { m ->
        Content(parts = listOf(Part(text = m.content)))
    }
    val geminiRequest = GeminiRequest(contents = contents)
    val response = client.post(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    ) {
        url { parameter("key", apiKey) }
        contentType(ContentType.Application.Json)
        setBody(geminiRequest)
    }.body<GeminiResponse>()

    return response.candidates.firstOrNull()
        ?.content?.parts?.firstOrNull()?.text
        ?: "No response from Gemini"
}

private suspend fun callGroqVision(
    client: HttpClient,
    apiKey: String,
    model: String,
    message: String,
    imageBase64: String?
): String {
    if (imageBase64.isNullOrBlank()) {
        return callGroq(client, apiKey, model, listOf(Message(role = "user", content = message)))
    }
    // For models that support vision via URL content
    val content = listOf(
        mapOf("type" to "text", "text" to message),
        mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
    )
    val body = mapOf(
        "model" to model,
        "messages" to listOf(mapOf("role" to "user", "content" to content))
    )
    val response = client.post("https://api.groq.com/openai/v1/chat/completions") {
        header("Authorization", "Bearer $apiKey")
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body<GroqResponse>()

    return when {
        response.error != null -> "Error: ${response.error.message}"
        response.choices.isNotEmpty() -> response.choices.first().message?.content ?: ""
        else -> "No response from model"
    }
}

private suspend fun callGeminiVision(
    client: HttpClient,
    apiKey: String,
    model: String,
    message: String,
    imageBase64: String?
): String {
    val parts = mutableListOf(Part(text = message))
    if (!imageBase64.isNullOrBlank()) {
        parts.add(Part(text = "data:image/jpeg;base64,$imageBase64"))
    }
    val geminiRequest = GeminiRequest(contents = listOf(Content(parts = parts)))
    val response = client.post(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    ) {
        url { parameter("key", apiKey) }
        contentType(ContentType.Application.Json)
        setBody(geminiRequest)
    }.body<GeminiResponse>()

    return response.candidates.firstOrNull()
        ?.content?.parts?.firstOrNull()?.text
        ?: "No response from Gemini"
}
