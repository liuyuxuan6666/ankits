package com.example.ankits

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "New Chat",
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val systemPrompt: String = "",
    val model: String = "",
    val maxContextTokens: Int = 4000
)
