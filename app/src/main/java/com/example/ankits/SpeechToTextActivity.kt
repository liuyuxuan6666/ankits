package com.example.ankits

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivitySpeechToTextBinding

class SpeechToTextActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeechToTextBinding
    private val engine: AsrEngine = AsrEngineFactory.create()
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

        engine.init(
            context = this,
            onReady = { setIdleState() },
            onError = { msg -> setErrorState(msg) }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.shutdown()
    }

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
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要麦克风权限才能使用语音识别", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (!hasRecordPermission()) {
            requestRecordPermission()
            return
        }

        resultBuilder.clear()
        binding.resultText.text = ""
        binding.partialHint.text = ""
        binding.partialHint.visibility = android.view.View.GONE

        engine.startListening(
            onPartial = { text -> runOnUiThread { onPartialResult(text) } },
            onFinal = { text -> runOnUiThread { onFinalResult(text) } },
            onEndOfSpeech = { runOnUiThread { onEndOfSpeech() } },
            onError = { msg -> runOnUiThread { onRecognitionError(msg) } }
        )
        setListeningState()
    }

    private fun stopListening() {
        engine.stopListening()
        setIdleState()
    }

    private fun onPartialResult(text: String) {
        binding.partialHint.text = text
        binding.partialHint.visibility = android.view.View.VISIBLE
    }

    private fun onFinalResult(text: String) {
        appendText(text)
        binding.partialHint.text = ""
        binding.partialHint.visibility = android.view.View.GONE
        setIdleState()
    }

    private fun onEndOfSpeech() {
        binding.statusLabel.text = "正在识别…"
    }

    private fun onRecognitionError(message: String) {
        binding.partialHint.text = ""
        binding.partialHint.visibility = android.view.View.GONE
        binding.statusLabel.text = message
        setIdleState()
    }

    private fun setListeningState() {
        isListening = true
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_on)
        binding.statusLabel.text = "正在聆听…"
        binding.micBtn.setImageResource(R.drawable.ic_mic)
        binding.micBtn.backgroundTintList =
            android.content.res.ColorStateList.valueOf(getColor(R.color.error))
        binding.micBtn.imageTintList = android.content.res.ColorStateList.valueOf(
            getColor(R.color.white)
        )
    }

    private fun setIdleState() {
        isListening = false
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_off)
        binding.statusLabel.text = getString(R.string.speech_status_idle)
        binding.micBtn.setImageResource(R.drawable.ic_mic)
        binding.micBtn.backgroundTintList =
            android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
        binding.micBtn.imageTintList = android.content.res.ColorStateList.valueOf(
            getColor(R.color.white)
        )
    }

    private fun setErrorState(message: String) {
        isListening = false
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_off)
        binding.statusLabel.text = message
        binding.micBtn.isEnabled = false
        binding.micBtn.backgroundTintList =
            android.content.res.ColorStateList.valueOf(getColor(R.color.outline))
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
}
