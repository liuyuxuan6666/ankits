package com.example.ankits

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityTtsBinding

class TtsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTtsBinding
    private val engine: TtsEngine = TtsEngineFactory.create()
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTtsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()

        binding.backBtn.setOnClickListener { finish() }
        binding.settingsBtn.setOnClickListener { /* TODO */ }
        binding.playBtn.setOnClickListener { togglePlay() }
        binding.stopBtn.setOnClickListener { stopSpeaking() }
        binding.clearBtn.setOnClickListener { binding.textInput.text?.clear() }

        binding.speedSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) engine.setSpeed(sliderToRange(progress))
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.pitchSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) engine.setPitch(sliderToRange(progress))
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        engine.init(
            context = this,
            onReady = { setReadyState() },
            onDone = { runOnUiThread { setReadyState() } },
            onError = { msg -> runOnUiThread { setErrorState(msg) } }
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

    private fun togglePlay() {
        if (isPlaying) {
            stopSpeaking()
        } else {
            startSpeaking()
        }
    }

    private fun startSpeaking() {
        val text = binding.textInput.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.tts_empty_text, Toast.LENGTH_SHORT).show()
            return
        }
        setSpeakingState()
        engine.speak(text)
    }

    private fun stopSpeaking() {
        engine.stop()
        setReadyState()
    }

    private fun setSpeakingState() {
        isPlaying = true
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_on)
        binding.statusLabel.text = getString(R.string.tts_status_speaking)
        binding.playBtn.setImageResource(R.drawable.ic_tts)
        binding.playBtn.backgroundTintList =
            ColorStateList.valueOf(getColor(R.color.error))
        binding.playBtn.imageTintList = ColorStateList.valueOf(getColor(R.color.white))
        binding.stopBtn.isEnabled = true
    }

    private fun setReadyState() {
        isPlaying = false
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_off)
        binding.statusLabel.text = getString(R.string.tts_status_ready)
        binding.playBtn.setImageResource(R.drawable.ic_tts)
        binding.playBtn.backgroundTintList =
            ColorStateList.valueOf(getColor(R.color.primary))
        binding.playBtn.imageTintList = ColorStateList.valueOf(getColor(R.color.white))
        binding.stopBtn.isEnabled = false
    }

    private fun setErrorState(message: String) {
        isPlaying = false
        binding.statusDot.setBackgroundResource(R.drawable.status_dot_off)
        binding.statusLabel.text = message
        binding.playBtn.isEnabled = false
        binding.stopBtn.isEnabled = false
        binding.playBtn.backgroundTintList =
            ColorStateList.valueOf(getColor(R.color.outline))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun sliderToRange(progress: Int): Float = 0.5f + progress * 0.15f
}
