package com.example.ankits

import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val color: Int = 0
)
