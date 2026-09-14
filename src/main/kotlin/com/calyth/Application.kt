package com.calyth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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
                    if (System.getenv(key) == null) System.setProperty(key, value)
                }
            }
        }
    }
}

fun env(key: String): String = System.getenv(key) ?: System.getProperty(key) ?: ""

fun main() {
    loadEnv()
    val port = (System.getenv("PORT") ?: System.getProperty("PORT") ?: "8082").toInt()
    embeddedServer(Netty, port = port) { module() }.start(wait = true)
}

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

@Serializable data class ChatRequest(val model: String = "openai/gpt-oss-20b", val message: String, val system: String? = null)
@Serializable data class ChatRequestWithHistory(val model: String = "openai/gpt-oss-20b", val message: String, val history: List<Message> = emptyList(), val system: String? = null)
@Serializable data class GroqRequest(val model: String, val messages: List<Message>, val stream: Boolean = false)
@Serializable data class Message(val role: String, val content: String)
@Serializable data class GroqResponse(val choices: List<Choice> = emptyList(), val error: GroqError? = null)
@Serializable data class GroqError(val message: String? = null, val code: String? = null)
@Serializable data class Choice(val message: Message? = null, val delta: Delta? = null, val finish_reason: String? = null)
@Serializable data class Delta(val role: String? = null, val content: String? = null)
@Serializable data class StreamChunk(val choices: List<Choice> = emptyList())

@Serializable data class GeminiRequest(val contents: List<Content>)
@Serializable data class Content(val parts: List<Part>)
@Serializable data class Part(val text: String)
@Serializable data class GeminiResponse(val candidates: List<Candidate> = emptyList())
@Serializable data class Candidate(val content: Content)

@Serializable data class SharedChat(val id: String, val title: String, val messages: List<Message>, val createdAt: Long)
@Serializable data class VisionRequest(val model: String, val message: String, val imageBase64: String? = null)

val sharedChats = ConcurrentHashMap<String, SharedChat>()

fun Application.module() {
    val jsonConfig = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    install(ServerContentNegotiation) { json(jsonConfig) }
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
        install(ClientContentNegotiation) { json(jsonConfig) }
        engine { requestTimeout = 120_000 }
    }

    routing {
        get("/") {
            call.respondText(
                this::class.java.classLoader.getResource("index.html")!!.readText(),
                ContentType.Text.Html
            )
        }
        get("/health") { call.respondText("OK") }
        get("/models") { call.respond(availableModels) }

        post("/chat") {
            val req = call.receive<ChatRequest>()
            val msgs = buildMessages(req.system, req.message)
            val reply = if (req.model.startsWith("gemini")) callGemini(client, geminiKey, req.model, req.system, req.message)
                        else callGroq(client, groqKey, req.model, msgs)
            call.respondText(reply)
        }

        post("/chat/history") {
            val req = call.receive<ChatRequestWithHistory>()
            val msgs = mutableListOf<Message>()
            if (!req.system.isNullOrBlank()) msgs.add(Message("system", req.system))
            msgs.addAll(req.history)
            msgs.add(Message("user", req.message))
            val reply = if (req.model.startsWith("gemini")) callGeminiWithHistory(client, geminiKey, req.model, msgs)
                        else callGroq(client, groqKey, req.model, msgs)
            call.respondText(reply)
        }

        post("/chat/stream") {
            val req = call.receive<ChatRequest>()
            if (req.model.startsWith("gemini")) {
                call.respondText(callGemini(client, geminiKey, req.model, req.system, req.message))
                return@post
            }
            if (groqKey.isBlank()) { call.respondText("Error: GROQ_API_KEY not configured"); return@post }

            val result = streamGroqResponse(groqKey, req.model, req.system, req.message)
            call.respondText(result, ContentType.Text.EventStream)
        }

        post("/search") {
            val req = call.receive<ChatRequest>()
            val msgs = listOf(Message("user", "Search the web and answer: ${req.message}"))
            val reply = callGroq(client, groqKey, "groq/compound", msgs)
            call.respondText(reply)
        }

        post("/upload") {
            val req = call.receive<VisionRequest>()
            val reply = if (req.model.startsWith("gemini")) callGeminiVision(client, geminiKey, req.model, req.message, req.imageBase64)
                        else callGroqVision(client, groqKey, req.model, req.message, req.imageBase64)
            call.respondText(reply)
        }

        post("/share") {
            val req = call.receive<SharedChat>()
            val id = if (req.id.isNotBlank()) req.id else UUID.randomUUID().toString().take(8)
            sharedChats[id] = req.copy(id = id)
            call.respond(mapOf("id" to id, "url" to "/shared/$id"))
        }

        get("/shared/{id}") {
            val chat = sharedChats[call.parameters["id"] ?: ""]
            if (chat != null) call.respond(chat) else call.respondText("Not found", status = HttpStatusCode.NotFound)
        }

        get("/shared/{id}/html") {
            val chat = sharedChats[call.parameters["id"] ?: ""]
            if (chat != null) {
                val html = buildString {
                    append("<!DOCTYPE html><html><head><title>${chat.title}</title>")
                    append("<style>body{font-family:Inter,sans-serif;max-width:700px;margin:40px auto;padding:0 20px;background:#09090b;color:#fafafa}")
                    append(".msg{margin:16px 0;padding:12px;border-radius:10px;line-height:1.6}")
                    append(".user{background:#6366f1;color:white;margin-left:40px}")
                    append(".assistant{background:#1e1e2e;margin-right:40px}")
                    append("</style></head><body>")
                    append("<h1>${chat.title}</h1>")
                    chat.messages.forEach { m ->
                        val cls = if (m.role == "user") "user" else "assistant"
                        val name = if (m.role == "user") "You" else "Calyth"
                        append("<div class='msg $cls'><strong>$name</strong><br>${m.content.replace("\n","<br>")}</div>")
                    }
                    append("<p style='text-align:center;color:#71717a;margin-top:40px'>Shared via Calyth</p>")
                    append("</body></html>")
                }
                call.respondText(html, ContentType.Text.Html)
            } else call.respondText("Not found", status = HttpStatusCode.NotFound)
        }
    }
}

fun buildMessages(system: String?, message: String): List<Message> {
    val msgs = mutableListOf<Message>()
    if (!system.isNullOrBlank()) msgs.add(Message("system", system))
    msgs.add(Message("user", message))
    return msgs
}

private fun streamGroqResponse(apiKey: String, model: String, system: String?, message: String): String {
    val msgs = buildMessages(system, message)
    val bodyJson = buildString {
        append("{\"model\":\"$model\",\"stream\":true,\"messages\":[")
        msgs.forEachIndexed { i, m ->
            if (i > 0) append(",")
            append("{\"role\":\"${m.role}\",\"content\":\"${m.content.jsonEscape()}\"}")
        }
        append("]}")
    }

    val url = URL("https://api.groq.com/openai/v1/chat/completions")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Authorization", "Bearer $apiKey")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 30_000
    conn.readTimeout = 120_000
    conn.outputStream.use { it.write(bodyJson.toByteArray()) }

    val result = StringBuilder()
    val reader = BufferedReader(InputStreamReader(conn.inputStream))
    var line: String?
    while (reader.readLine().also { line = it } != null) {
        val l = line ?: continue
        if (l.startsWith("data: ")) {
            val data = l.removePrefix("data: ").trim()
            if (data == "[DONE]") break
            try {
                val chunk = Json { ignoreUnknownKeys = true; isLenient = true }
                    .decodeFromString<StreamChunk>(data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null) result.append(content)
                if (chunk.choices.firstOrNull()?.finish_reason != null) break
            } catch (_: Exception) {}
        }
    }
    conn.disconnect()
    return result.toString()
}

fun String.jsonEscape(): String = this.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

private suspend fun callGroq(client: HttpClient, apiKey: String, model: String, messages: List<Message>): String {
    if (apiKey.isBlank()) return "Error: GROQ_API_KEY not configured"
    val response = client.post("https://api.groq.com/openai/v1/chat/completions") {
        header("Authorization", "Bearer $apiKey")
        contentType(ContentType.Application.Json)
        setBody(GroqRequest(model = model, messages = messages))
    }.body<GroqResponse>()
    return when {
        response.error != null -> "Error: ${response.error.message}"
        response.choices.isNotEmpty() -> response.choices.first().message?.content ?: ""
        else -> "No response from model"
    }
}

private suspend fun callGemini(client: HttpClient, apiKey: String, model: String, system: String?, message: String): String {
    if (apiKey.isBlank()) return "Error: GEMINI_API_KEY not configured"
    val fullMessage = if (!system.isNullOrBlank()) "$system\n\n$message" else message
    val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
        url { parameter("key", apiKey) }
        contentType(ContentType.Application.Json)
        setBody(GeminiRequest(listOf(Content(listOf(Part(fullMessage))))))
    }.body<GeminiResponse>()
    return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from Gemini"
}

private suspend fun callGeminiWithHistory(client: HttpClient, apiKey: String, model: String, messages: List<Message>): String {
    if (apiKey.isBlank()) return "Error: GEMINI_API_KEY not configured"
    val contents = messages.filter { it.role != "system" }.map { Content(listOf(Part(it.content))) }
    val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
        url { parameter("key", apiKey) }
        contentType(ContentType.Application.Json)
        setBody(GeminiRequest(contents))
    }.body<GeminiResponse>()
    return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from Gemini"
}

private suspend fun callGroqVision(client: HttpClient, apiKey: String, model: String, message: String, imageBase64: String?): String {
    if (apiKey.isBlank()) return "Error: GROQ_API_KEY not configured"
    if (imageBase64.isNullOrBlank()) return callGroq(client, apiKey, model, listOf(Message("user", message)))
    val content = listOf(
        mapOf("type" to "text", "text" to message),
        mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
    )
    val body = mapOf("model" to model, "messages" to listOf(mapOf("role" to "user", "content" to content)))
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

private suspend fun callGeminiVision(client: HttpClient, apiKey: String, model: String, message: String, imageBase64: String?): String {
    if (apiKey.isBlank()) return "Error: GEMINI_API_KEY not configured"
    val parts = mutableListOf(Part(message))
    if (!imageBase64.isNullOrBlank()) parts.add(Part("data:image/jpeg;base64,$imageBase64"))
    val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
        url { parameter("key", apiKey) }
        contentType(ContentType.Application.Json)
        setBody(GeminiRequest(listOf(Content(parts))))
    }.body<GeminiResponse>()
    return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from Gemini"
}
