package com.example.ankits

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
