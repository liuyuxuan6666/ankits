package com.example.ankits

enum class SessionType { WORK, SHORT_BREAK, LONG_BREAK }

data class PomodoroSession(
    val id: String,
    val type: SessionType,
    val plannedDurationMin: Int,
    val actualDurationSec: Int,
    val completed: Boolean,
    val timestampMillis: Long
)

data class PomodoroSettings(
    val workDurationMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    val longBreakInterval: Int = 4,
    val vibrateOnEnd: Boolean = true,
    val soundOnEnd: Boolean = true,
    val lockScreen: Boolean = false
)
