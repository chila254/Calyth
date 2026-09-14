package com.calyth.app

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(val model: String, val message: String)

@Serializable
data class ModelInfo(val id: String, val name: String, val provider: String, val description: String)

@Serializable
data class ChatResponse(val response: String)
