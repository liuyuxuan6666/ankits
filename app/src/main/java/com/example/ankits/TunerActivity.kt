package com.example.ankits

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityTunerBinding
import kotlin.math.PI
import kotlin.math.sin

class TunerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTunerBinding
    private val pitchDetector = PitchDetector()
    private val handler = Handler(Looper.getMainLooper())

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var isListening = false

    private var toneTrack: AudioTrack? = null
    private var toneThread: Thread? = null
    private var isTonePlaying = false
    private var activeToneBtn: View? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTunerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()
        setupToolbar()
        setupListenButton()
        setupReferencePitch()
        setupGuitarTones()
        setupConfigCards()
    }

    override fun onPause() {
        super.onPause()
        stopListening()
        stopTone()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        stopTone()
        releaseAudioRecord()
        handler.removeCallbacksAndMessages(null)
    }

    // --- Window insets ---

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, statusBar.top, 0, navBar.bottom)
            insets
        }
    }

    // --- Toolbar ---

    private fun setupToolbar() {
        binding.backBtn.setOnClickListener { finish() }
        binding.settingsBtn.setOnClickListener { /* TODO */ }
    }

    // --- Listen button ---

    private fun setupListenButton() {
        binding.listenBtn.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
    }

    private fun startListening() {
        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
            return
        }
        beginCapture()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginCapture()
            }
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun beginCapture() {
        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, sampleRate * 2)

        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        isListening = true
        binding.listenBtn.text = "■ 停止监听"
        binding.centsText.text = "正在监听…"

        val readBuffer = ShortArray(4096)
        captureThread = Thread {
            while (isListening) {
                val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (read > 0) {
                    val chunk = readBuffer.copyOf(read)
                    val result = pitchDetector.detect(chunk)
                    handler.post { onPitchResult(result) }
                }
            }
        }
        captureThread?.start()
    }

    private fun stopListening() {
        isListening = false
        captureThread?.join(200)
        captureThread = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        binding.listenBtn.text = "▶ 开始监听"
        resetPitchDisplay()
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun onPitchResult(result: PitchDetector.PitchResult?) {
        if (result == null) {
            resetPitchDisplay()
            return
        }

        binding.noteText.text = result.noteFull
        binding.freqText.text = "%.1f Hz".format(result.frequency)
        binding.tuningMeter.cents = result.cents

        val inTune = result.isInTune
        val centsColor = when {
            inTune -> android.graphics.Color.parseColor("#4CAF50")
            kotlin.math.abs(result.cents) <= 15f -> android.graphics.Color.parseColor("#FFC107")
            else -> android.graphics.Color.parseColor("#F44336")
        }

        val arrow = if (result.cents > 0) "\u2191" else if (result.cents < 0) "\u2193" else ""
        val sign = if (result.cents > 0) "+" else ""
        val centsStr = if (inTune) "\u2713 在调" else "$arrow $sign${"%.0f".format(result.cents)}\u00A2"
        binding.centsText.text = centsStr
        binding.centsText.setTextColor(centsColor)
        binding.noteText.setTextColor(
            if (inTune) android.graphics.Color.parseColor("#4CAF50")
            else android.graphics.Color.parseColor("#1C1B1F")
        )
    }

    private fun resetPitchDisplay() {
        binding.noteText.text = "--"
        binding.freqText.text = "0.0 Hz"
        binding.tuningMeter.cents = 0f
        binding.centsText.text = "等待声音输入…"
        binding.centsText.setTextColor(
            ContextCompat.getColor(this, R.color.on_surface_variant)
        )
        binding.noteText.setTextColor(
            ContextCompat.getColor(this, R.color.on_surface)
        )
    }

    // --- Reference pitch slider ---

    private fun setupReferencePitch() {
        binding.pitchRefSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                    val freq = 430 + progress
                    binding.pitchRefLabel.text = "${freq}Hz"
                    if (fromUser) pitchDetector.referencePitch = freq.toFloat()
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            }
        )
    }

    // --- Guitar reference tones ---

    private fun setupGuitarTones() {
        val buttonFreqs = listOf(
            binding.toneE2 to 82.41,
            binding.toneA2 to 110.00,
            binding.toneD3 to 146.83,
            binding.toneG3 to 196.00,
            binding.toneB3 to 246.94,
            binding.toneE4 to 329.63
        )
        buttonFreqs.forEach { (btn, freq) ->
            btn.setOnClickListener {
                if (activeToneBtn == btn) {
                    stopTone()
                    resetToneButtonStyle()
                } else {
                    resetToneButtonStyle()
                    startTone(freq)
                    highlightToneButton(btn)
                }
            }
        }
    }

    private fun startTone(freq: Double) {
        stopTone()
        val sampleRate = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, sampleRate * 2)

        toneTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (toneTrack?.state != AudioTrack.STATE_INITIALIZED) {
            toneTrack = null
            return
        }

        isTonePlaying = true
        toneTrack?.play()

        toneThread = Thread {
            val chunk = ShortArray(1024)
            var phase = 0.0
            val amplitude = 0.4
            while (isTonePlaying) {
                for (i in chunk.indices) {
                    chunk[i] = (Short.MAX_VALUE * amplitude * sin(2.0 * PI * freq * phase / sampleRate)).toInt().toShort()
                    phase += 1.0
                }
                toneTrack?.write(chunk, 0, chunk.size)
            }
        }
        toneThread?.start()
    }

    private fun stopTone() {
        isTonePlaying = false
        toneThread?.join(200)
        toneThread = null
        try {
            toneTrack?.pause()
            toneTrack?.flush()
            toneTrack?.release()
        } catch (_: Exception) {}
        toneTrack = null
    }

    private fun highlightToneButton(btn: View) {
        activeToneBtn = btn
        if (btn is com.google.android.material.button.MaterialButton) {
            btn.setBackgroundColor(
                ContextCompat.getColor(this, R.color.primary)
            )
            btn.setTextColor(
                ContextCompat.getColor(this, R.color.on_primary)
            )
        }
    }

    private fun resetToneButtonStyle() {
        val btn = activeToneBtn
        if (btn is com.google.android.material.button.MaterialButton) {
            btn.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            btn.setTextColor(
                ContextCompat.getColor(this, R.color.primary)
            )
            btn.strokeColor = ContextCompat.getColorStateList(this, R.color.outline)
        }
        activeToneBtn = null
    }

    // --- Config cards ---

    private fun setupConfigCards() {
        binding.pitchRefHeader.setOnClickListener {
            toggleCard(binding.pitchRefContent, binding.pitchRefChevron)
        }
        binding.guitarRefHeader.setOnClickListener {
            toggleCard(binding.guitarRefContent, binding.guitarRefChevron)
        }
    }

    private fun toggleCard(content: View, chevron: TextView) {
        val expanded = content.visibility == View.VISIBLE
        content.visibility = if (expanded) View.GONE else View.VISIBLE
        chevron.text = if (expanded) "\u25B8" else "\u25BE"
    }
}
