package com.calyth.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json".toMediaType()

    private var baseUrl = "https://calyth.onrender.com"

    fun setServerUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    suspend fun getModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .get()
                .build()

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

    suspend fun sendMessage(model: String, message: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("model", model)
                put("message", message)
            }

            val request = Request.Builder()
                .url("$baseUrl/chat")
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            response.body?.string() ?: "No response"
        } catch (e: Exception) {
            Log.e("Calyth", "Failed to send message", e)
            "Error: ${e.message}"
        }
    }
}
