package com.example.ankits

import android.content.Context

/**
 * Abstraction for text-to-speech engines.
 *
 * The default implementation [SystemTtsEngine] wraps Android's built-in TTS.
 * To add custom model support, implement this interface and swap the engine
 * returned by [TtsEngineFactory.create].
 */
interface TtsEngine {
    fun init(context: Context, onReady: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit)
    fun speak(text: String)
    fun stop()
    fun shutdown()
    fun isSpeaking(): Boolean
    fun setPitch(pitch: Float)
    fun setSpeed(speed: Float)
}

object TtsEngineFactory {
    fun create(): TtsEngine = SystemTtsEngine()
}
