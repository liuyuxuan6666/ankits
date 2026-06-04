package com.example.ankits

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivitySpeechToTextBinding

class SpeechToTextActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeechToTextBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val resultBuilder = StringBuilder()

    companion object {
        private const val REQUEST_RECORD_AUDIO = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeechToTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        binding.backBtn.setOnClickListener { finish() }
        binding.settingsBtn.setOnClickListener { /* TODO */ }

        binding.micBtn.setOnClickListener { toggleListening() }
        binding.copyBtn.setOnClickListener { copyToClipboard() }
        binding.clearBtn.setOnClickListener { clearText() }

        if (!hasRecordPermission()) {
            requestRecordPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyRecognizer()
    }

    // --- Window insets ---

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, statusBar.top, 0, 0)
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft,
                binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight,
                navBar.bottom
            )
            insets
        }
    }

    // --- Permission ---

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestRecordPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ready to use
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用语音识别", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Speech Recognizer ---

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setErrorState("语音识别不可用，请检查系统语音服务是否已启用")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(RecognitionListenerImpl())
    }

    private fun destroyRecognizer() {
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
    }

    private fun startListening() {
        if (!hasRecordPermission()) {
            requestRecordPermission()
            return
        }

        ensureRecognizer()
        if (speechRecognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
    }

    private fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            resultBuilder.clear()
            binding.resultText.text = ""
            binding.partialHint.text = ""
            binding.partialHint.visibility = android.view.View.GONE
            startListening()
        }
    }

    private fun setListeningState() {
        isListening = true
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_on)
        binding.statusLabel.text = "正在聆听…"
        binding.micBtn.setIconResource(R.drawable.ic_mic)
        binding.micBtn.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(getColor(R.color.error))
        )
        binding.micBtn.iconTint = android.content.res.ColorStateList.valueOf(
            getColor(R.color.white)
        )
    }

    private fun setIdleState() {
        isListening = false
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_off)
        binding.statusLabel.text = getString(R.string.speech_status_idle)
        binding.micBtn.setIconResource(R.drawable.ic_mic)
        binding.micBtn.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
        )
        binding.micBtn.iconTint = android.content.res.ColorStateList.valueOf(
            getColor(R.color.white)
        )
    }

    private fun setErrorState(message: String) {
        isListening = false
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_off)
        binding.statusLabel.text = message
        binding.micBtn.isEnabled = false
        binding.micBtn.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(getColor(R.color.outline))
        )
    }

    private fun appendText(text: String) {
        if (resultBuilder.isNotEmpty() && !resultBuilder.endsWith("\n")) {
            resultBuilder.append("\n")
        }
        resultBuilder.append(text)
        binding.resultText.text = resultBuilder.toString()
        binding.textScroll.post {
            binding.textScroll.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun copyToClipboard() {
        val text = resultBuilder.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可复制的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("speech_to_text", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun clearText() {
        if (isListening) {
            stopListening()
        }
        resultBuilder.clear()
        binding.resultText.text = ""
        binding.partialHint.text = ""
        binding.partialHint.visibility = android.view.View.GONE
    }

    // --- Recognition Listener ---

    private inner class RecognitionListenerImpl : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            setListeningState()
        }

        override fun onBeginningOfSpeech() {
            binding.statusLabel.text = "捕捉到语音…"
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could animate mic button based on volume level, but keep it simple
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            binding.statusLabel.text = "正在识别…"
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: return
            if (matches.isNotEmpty()) {
                binding.partialHint.text = matches[0]
                binding.partialHint.visibility = android.view.View.VISIBLE
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: return
            if (matches.isNotEmpty()) {
                appendText(matches[0])
            }
            binding.partialHint.text = ""
            binding.partialHint.visibility = android.view.View.GONE
            setIdleState()
        }

        override fun onError(error: Int) {
            val message = when (error) {
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
            binding.partialHint.text = ""
            binding.partialHint.visibility = android.view.View.GONE
            binding.statusLabel.text = message
            setIdleState()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
