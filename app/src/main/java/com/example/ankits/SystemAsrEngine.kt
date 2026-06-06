package com.example.ankits

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SystemAsrEngine : AsrEngine {

    private var speechRecognizer: SpeechRecognizer? = null

    override fun init(
        context: Context,
        onReady: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("语音识别不可用，请检查系统语音服务是否已启用")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        onReady()
    }

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEndOfSpeech: () -> Unit,
        onError: (String) -> Unit
    ) {
        val recognizer = speechRecognizer ?: run {
            onError("系统语音引擎未就绪")
            return
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onEndOfSpeech()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: return
                if (matches.isNotEmpty()) {
                    onPartial(matches[0])
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?: return
                if (matches.isNotEmpty()) {
                    onFinal(matches[0])
                }
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "录音错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误，请重试"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK -> "网络不可用，请检查网络连接"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音，请重试"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音引擎繁忙，请稍后重试"
                    SpeechRecognizer.ERROR_SERVER -> "语音服务出错"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音"
                    else -> "未知错误 ($error)"
                }
                onError(msg)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.startListening(intent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
    }

    override fun shutdown() {
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
    }

    override fun isListening(): Boolean = false
}
