package com.example.ankits

import android.content.Context

class FallbackAsrEngine : AsrEngine {
    private val systemEngine = SystemAsrEngine()
    private var sherpaEngine: SherpaOnnxAsrEngine? = null
    private var activeEngine: AsrEngine? = null
    private var initAttempted = false

    override fun init(
        context: Context,
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (initAttempted) return
        initAttempted = true

        systemEngine.init(
            context = context,
            onReady = {
                activeEngine = systemEngine
                onReady()
            },
            onError = { systemMsg ->
                val sherpa = SherpaOnnxAsrEngine()
                sherpaEngine = sherpa
                sherpa.init(
                    context = context,
                    onReady = {
                        activeEngine = sherpa
                        onReady()
                    },
                    onError = { sherpaMsg ->
                        onError("系统识别不可用 ($systemMsg)，本地识别: $sherpaMsg")
                    }
                )
            }
        )
    }

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEndOfSpeech: () -> Unit,
        onError: (String) -> Unit
    ) {
        activeEngine?.startListening(onPartial, onFinal, onEndOfSpeech, onError)
            ?: onError("语音识别引擎未就绪")
    }

    override fun stopListening() {
        activeEngine?.stopListening()
    }

    override fun shutdown() {
        systemEngine.shutdown()
        sherpaEngine?.shutdown()
        activeEngine = null
    }

    override fun isListening(): Boolean = activeEngine?.isListening() ?: false
}
