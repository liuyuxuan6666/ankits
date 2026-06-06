package com.example.ankits

import android.content.Context

interface AsrEngine {
    fun init(
        context: Context,
        onReady: () -> Unit,
        onError: (String) -> Unit
    )

    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEndOfSpeech: () -> Unit,
        onError: (String) -> Unit
    )

    fun stopListening()
    fun shutdown()
    fun isListening(): Boolean
}

object AsrEngineFactory {
    fun create(): AsrEngine = FallbackAsrEngine()
}
