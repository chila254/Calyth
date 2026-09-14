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
import java.io.File

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
data class ChatRequest(val model: String = "openai/gpt-oss-20b", val message: String)

@Serializable
data class GroqRequest(val model: String, val messages: List<Message>)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class GroqResponse(val choices: List<Choice> = emptyList(), val error: GroqError? = null)

@Serializable
data class GroqError(val message: String? = null, val code: String? = null)

@Serializable
data class Choice(val message: Message)

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

// ── App Module ────────────────────────────────────────────────────────

fun Application.module() {
    val jsonConfig = Json { ignoreUnknownKeys = true; isLenient = true }
    install(ServerContentNegotiation) {
        json(jsonConfig)
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
    }
    val groqKey = env("GROQ_API_KEY")
    val geminiKey = env("GEMINI_API_KEY")
    val client = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(jsonConfig)
        }
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

        // ── Chat endpoint ─────────────────────────────────────────────
        post("/chat") {
            val request = call.receive<ChatRequest>()
            val modelId = request.model

            val reply = when {
                modelId.startsWith("gemini") -> {
                    if (geminiKey.isBlank()) {
                        "Error: GEMINI_API_KEY not configured"
                    } else {
                        callGemini(client, geminiKey, modelId, request.message)
                    }
                }
                else -> {
                    if (groqKey.isBlank()) {
                        "Error: GROQ_API_KEY not configured"
                    } else {
                        callGroq(client, groqKey, modelId, request.message)
                    }
                }
            }

            call.respondText(reply)
        }
    }
}

// ── Provider calls ────────────────────────────────────────────────────

private suspend fun callGroq(
    client: HttpClient,
    apiKey: String,
    model: String,
    message: String
): String {
    val groqRequest = GroqRequest(
        model = model,
        messages = listOf(Message(role = "user", content = message))
    )
    val response = client.post("https://api.groq.com/openai/v1/chat/completions") {
        header("Authorization", "Bearer $apiKey")
        contentType(ContentType.Application.Json)
        setBody(groqRequest)
    }.body<GroqResponse>()

    return when {
        response.error != null -> "Error: ${response.error.message}"
        response.choices.isNotEmpty() -> response.choices.first().message.content
        else -> "No response from model"
    }
}

private suspend fun callGemini(
    client: HttpClient,
    apiKey: String,
    model: String,
    message: String
): String {
    val geminiRequest = GeminiRequest(
        contents = listOf(Content(parts = listOf(Part(text = message))))
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
