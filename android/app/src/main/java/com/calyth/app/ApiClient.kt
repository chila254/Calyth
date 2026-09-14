package com.calyth.app

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json".toMediaType()
    private val sseFactory = EventSources.createFactory(client)

    private var baseUrl = "https://calyth.onrender.com"

    fun setServerUrl(url: String) { baseUrl = url.trimEnd('/') }

    suspend fun getModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/models").get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ModelInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    provider = obj.getString("provider"),
                    description = obj.getString("description")
                )
            }
        } catch (e: Exception) {
            Log.e("Calyth", "Failed to fetch models", e)
            listOf(
                ModelInfo("openai/gpt-oss-20b", "GPT-OSS 20B", "Groq", "Fast, general-purpose"),
                ModelInfo("openai/gpt-oss-120b", "GPT-OSS 120B", "Groq", "Larger, more capable"),
                ModelInfo("qwen/qwen3.6-27b", "Qwen 3.6 27B", "Groq", "Strong reasoning"),
                ModelInfo("groq/compound", "Groq Compound", "Groq", "Multi-step reasoning"),
                ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "Google", "Fast, free tier"),
                ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", "Google", "High quality")
            )
        }
    }

    suspend fun sendMessage(model: String, message: String, system: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("model", model)
                put("message", message)
                if (system != null) put("system", system)
            }
            val request = Request.Builder().url("$baseUrl/chat")
                .post(json.toString().toRequestBody(JSON_MEDIA)).build()
            val response = client.newCall(request).execute()
            response.body?.string() ?: "No response"
        } catch (e: Exception) {
            Log.e("Calyth", "Failed to send message", e)
            "Error: ${e.message}"
        }
    }

    fun sendStreaming(
        model: String,
        message: String,
        system: String? = null,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ): EventSource {
        val json = JSONObject().apply {
            put("model", model)
            put("message", message)
            if (system != null) put("system", system)
        }
        val request = Request.Builder().url("$baseUrl/chat/stream")
            .post(json.toString().toRequestBody(JSON_MEDIA)).build()

        return sseFactory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val raw = data
                if (raw == "[DONE]" || raw == "\"[DONE]\"") {
                    onDone()
                    return
                }
                try {
                    val token = raw.removeSurrounding("\"")
                    onToken(token)
                } catch (_: Exception) {
                    onToken(raw)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                onError(t?.message ?: "Stream error")
            }

            override fun onClosed(eventSource: EventSource) {
                onDone()
            }
        })
    }

    suspend fun searchWeb(model: String, message: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("model", model)
                put("message", message)
            }
            val request = Request.Builder().url("$baseUrl/search")
                .post(json.toString().toRequestBody(JSON_MEDIA)).build()
            val response = client.newCall(request).execute()
            response.body?.string() ?: "No response"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun uploadImage(model: String, message: String, imageBase64: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("model", model)
                put("message", message)
                put("imageBase64", imageBase64)
            }
            val request = Request.Builder().url("$baseUrl/upload")
                .post(json.toString().toRequestBody(JSON_MEDIA)).build()
            val response = client.newCall(request).execute()
            response.body?.string() ?: "No response"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun imageToBase64(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun shareChat(title: String, messages: List<Pair<String, Boolean>>): String? = withContext(Dispatchers.IO) {
        try {
            val msgsArray = JSONArray()
            messages.forEach { (content, isUser) ->
                msgsArray.put(JSONObject().apply {
                    put("role", if (isUser) "user" else "assistant")
                    put("content", content)
                })
            }
            val json = JSONObject().apply {
                put("id", UUID.randomUUID().toString().take(8))
                put("title", title)
                put("messages", msgsArray)
                put("createdAt", System.currentTimeMillis())
            }
            val request = Request.Builder().url("$baseUrl/share")
                .post(json.toString().toRequestBody(JSON_MEDIA)).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val result = JSONObject(body)
            "$baseUrl/shared/${result.getString("id")}/html"
        } catch (e: Exception) {
            null
        }
    }
}
