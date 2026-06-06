package com.example.ankits

import android.content.Context

class FallbackTtsEngine : TtsEngine {
    private val systemEngine = SystemTtsEngine()
    private var sherpaEngine: SherpaOnnxTtsEngine? = null
    private var activeEngine: TtsEngine? = null
    private var initAttempted = false

    private var onReady: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun init(
        context: Context,
        onReady: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (initAttempted) return
        initAttempted = true

        this.onReady = onReady
        this.onDone = onDone
        this.onError = onError

        systemEngine.init(
            context = context,
            onReady = {
                activeEngine = systemEngine
                onReady()
            },
            onDone = onDone,
            onError = { systemMsg ->
                val sherpa = SherpaOnnxTtsEngine()
                sherpaEngine = sherpa
                sherpa.init(
                    context = context,
                    onReady = {
                        activeEngine = sherpa
                        onReady()
                    },
                    onDone = onDone,
                    onError = { sherpaMsg ->
                        onError("系统TTS不可用 ($systemMsg)，本地TTS加载失败: $sherpaMsg")
                    }
                )
            }
        )
    }

    override fun speak(text: String) {
        activeEngine?.speak(text)
            ?: onError?.invoke("TTS引擎未就绪")
    }

    override fun stop() {
        activeEngine?.stop()
    }

    override fun shutdown() {
        systemEngine.shutdown()
        sherpaEngine?.shutdown()
        activeEngine = null
    }

    override fun isSpeaking(): Boolean = activeEngine?.isSpeaking() ?: false

    override fun setPitch(pitch: Float) {
        systemEngine.setPitch(pitch)
        sherpaEngine?.setPitch(pitch)
    }

    override fun setSpeed(speed: Float) {
        systemEngine.setSpeed(speed)
        sherpaEngine?.setSpeed(speed)
    }
}
