package com.example.ankits

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

enum class PomodoroState { IDLE, RUNNING, PAUSED, COMPLETED }

interface PomodoroCallbacks {
    fun onTick(remainingSec: Int, totalSec: Int)
    fun onStateChanged(newState: PomodoroState)
}

class PomodoroEngine(private val callbacks: PomodoroCallbacks) {

    var state: PomodoroState = PomodoroState.IDLE
        private set
    var sessionType: SessionType = SessionType.WORK
        private set
    var plannedDurationMin: Int = 25
        private set
    var remainingSec: Int = 0
        private set
    var totalSec: Int = 0
        private set
    var startedAtMillis: Long = 0L
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var lastTickMillis: Long = 0L
    private var isTicking = false

    fun start(type: SessionType, durationMin: Int) {
        sessionType = type
        plannedDurationMin = durationMin
        totalSec = durationMin * 60
        remainingSec = totalSec
        startedAtMillis = System.currentTimeMillis()
        state = PomodoroState.RUNNING
        callbacks.onStateChanged(state)
        startTicking()
    }

    fun pause() {
        if (state != PomodoroState.RUNNING) return
        state = PomodoroState.PAUSED
        stopTicking()
        callbacks.onStateChanged(state)
    }

    fun resume() {
        if (state != PomodoroState.PAUSED) return
        state = PomodoroState.RUNNING
        callbacks.onStateChanged(state)
        startTicking()
    }

    fun stop(): PomodoroSession {
        stopTicking()
        val elapsedSec = ((System.currentTimeMillis() - startedAtMillis) / 1000).toInt()
        val actualDurationSec = if (remainingSec > 0) elapsedSec else plannedDurationMin * 60
        val completed = remainingSec <= 0
        val session = PomodoroSession(
            id = java.util.UUID.randomUUID().toString(),
            type = sessionType,
            plannedDurationMin = plannedDurationMin,
            actualDurationSec = actualDurationSec,
            completed = completed,
            timestampMillis = startedAtMillis
        )
        state = PomodoroState.IDLE
        callbacks.onStateChanged(state)
        return session
    }

    fun compensateTimeOff(offDurationSec: Int) {
        if (state != PomodoroState.RUNNING && state != PomodoroState.PAUSED) return
        remainingSec = (remainingSec - offDurationSec).coerceAtLeast(0)
        if (remainingSec <= 0) {
            finishTimer()
        }
    }

    private fun startTicking() {
        if (isTicking) return
        isTicking = true
        lastTickMillis = SystemClock.elapsedRealtime()
        tick()
    }

    private fun stopTicking() {
        isTicking = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun tick() {
        if (!isTicking) return

        val now = SystemClock.elapsedRealtime()
        val elapsed = ((now - lastTickMillis) / 1000).toInt()
        if (elapsed >= 1) {
            lastTickMillis += elapsed * 1000L
            remainingSec = (remainingSec - elapsed).coerceAtLeast(0)
            callbacks.onTick(remainingSec, totalSec)

            if (remainingSec <= 0) {
                finishTimer()
                return
            }
        }

        handler.postDelayed({ tick() }, 200)
    }

    private fun finishTimer() {
        stopTicking()
        state = PomodoroState.COMPLETED
        remainingSec = 0
        callbacks.onStateChanged(state)
    }
}
