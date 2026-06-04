package com.example.ankits

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class SystemTtsEngine : TtsEngine {

    private var tts: TextToSpeech? = null
    private var onDone: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var ready = false

    override fun init(
        context: Context,
        onReady: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        this.onDone = onDone
        this.onError = onError

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fall back to default locale
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        this@SystemTtsEngine.onDone?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        this@SystemTtsEngine.onError?.invoke("TTS 播放出错")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        this@SystemTtsEngine.onError?.invoke("TTS 播放出错 (code=$errorCode)")
                    }
                })
                ready = true
                onReady()
            } else {
                ready = false
                onError("TTS 引擎初始化失败 (status=$status)")
            }
        }
    }

    override fun speak(text: String) {
        if (!ready || tts == null) {
            onError?.invoke("TTS 引擎未就绪")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ankits_tts")
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    override fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    override fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }
}
