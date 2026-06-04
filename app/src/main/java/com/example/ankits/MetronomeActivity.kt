package com.example.ankits

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityMetronomeBinding
import com.google.android.material.chip.Chip

class MetronomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMetronomeBinding
    private val engine = MetronomeEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val tapTimestamps = mutableListOf<Long>()

    private var bpmRepeatRunnable: Runnable? = null
    private var beatResetRunnable: Runnable? = null
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMetronomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()
        setupToolbar()
        setupBpmControls()
        setupTimeSignatureChips()
        setupSoundConfig()
        setupTapTempo()
        setupPlayButton()
        updateBpmDisplay()
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            engine.stop()
            clearKeepScreenOn()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
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
        binding.settingsBtn.setOnClickListener { /* TODO: open settings */ }
    }

    // --- BPM controls ---

    private fun setupBpmControls() {
        binding.bpmMinusBtn.setOnClickListener { adjustBpm(-1) }
        binding.bpmMinusBtn.setOnLongClickListener { startBpmRepeat(-1); true }
        binding.bpmMinusBtn.setOnTouchListener { _, event -> handleBpmTouch(event); false }

        binding.bpmPlusBtn.setOnClickListener { adjustBpm(1) }
        binding.bpmPlusBtn.setOnLongClickListener { startBpmRepeat(1); true }
        binding.bpmPlusBtn.setOnTouchListener { _, event -> handleBpmTouch(event); false }
    }

    private fun handleBpmTouch(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            stopBpmRepeat()
        }
    }

    private fun adjustBpm(delta: Int) {
        val newBpm = (engine.bpm + delta).coerceIn(20, 300)
        if (newBpm != engine.bpm) {
            engine.setTempo(newBpm)
            updateBpmDisplay()
        }
    }

    private fun startBpmRepeat(delta: Int) {
        val runnable = object : Runnable {
            override fun run() {
                adjustBpm(delta)
                bpmRepeatRunnable?.let { handler.postDelayed(it, 80) }
            }
        }
        bpmRepeatRunnable = runnable
        handler.postDelayed(runnable, 400)
    }

    private fun stopBpmRepeat() {
        bpmRepeatRunnable?.let { handler.removeCallbacks(it) }
        bpmRepeatRunnable = null
    }

    private fun updateBpmDisplay() {
        binding.bpmText.text = engine.bpm.toString()
    }

    // --- Time signature chips ---

    private fun setupTimeSignatureChips() {
        val numeratorValues = (1..8).toList()
        val denominatorValues = listOf(2, 4, 8, 16)

        numeratorValues.forEach { value ->
            val chip = Chip(binding.numeratorChips.context)
            chip.text = value.toString()
            chip.isCheckable = true
            chip.isChecked = value == engine.beatsPerBar
            chip.setOnClickListener {
                engine.setTimeSignature(value, engine.beatUnit)
                updateBeatCounter()
            }
            binding.numeratorChips.addView(chip)
        }

        denominatorValues.forEach { value ->
            val chip = Chip(binding.denominatorChips.context)
            chip.text = value.toString()
            chip.isCheckable = true
            chip.isChecked = value == engine.beatUnit
            chip.setOnClickListener {
                engine.setTimeSignature(engine.beatsPerBar, value)
            }
            binding.denominatorChips.addView(chip)
        }

        // Collapsible card
        binding.timeSigHeader.setOnClickListener {
            toggleVisibility(binding.timeSigContent, binding.timeSigChevron)
        }
    }

    // --- Sound config ---

    private fun setupSoundConfig() {
        binding.accentFreqSeek.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    val freq = (progress + 200).toDouble()
                    binding.accentFreqLabel.text = "${freq.toInt()}Hz"
                    if (fromUser) engine.accentFreq = freq
                }
                override fun onStartTrackingTouch(seek: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(seek: android.widget.SeekBar) {}
            }
        )

        binding.unaccentFreqSeek.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    val freq = (progress + 200).toDouble()
                    binding.unaccentFreqLabel.text = "${freq.toInt()}Hz"
                    if (fromUser) engine.unaccentFreq = freq
                }
                override fun onStartTrackingTouch(seek: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(seek: android.widget.SeekBar) {}
            }
        )

        // Trigger initial label
        binding.accentFreqSeek.progress = 680
        binding.unaccentFreqSeek.progress = 240

        binding.soundHeader.setOnClickListener {
            toggleVisibility(binding.soundContent, binding.soundChevron)
        }
    }

    // --- Tap tempo ---

    private fun setupTapTempo() {
        binding.tapTempoBtn.setOnClickListener {
            val now = SystemClock.elapsedRealtime()

            // Reset if last tap was more than 2 seconds ago
            if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > 2000) {
                tapTimestamps.clear()
            }

            tapTimestamps.add(now)
            // Keep only last 5 taps
            while (tapTimestamps.size > 5) {
                tapTimestamps.removeAt(0)
            }

            if (tapTimestamps.size >= 2) {
                var totalInterval = 0L
                for (i in 1 until tapTimestamps.size) {
                    totalInterval += tapTimestamps[i] - tapTimestamps[i - 1]
                }
                val avgInterval = totalInterval / (tapTimestamps.size - 1)
                val computedBpm = (60000.0 / avgInterval).toInt().coerceIn(20, 300)

                engine.setTempo(computedBpm)
                updateBpmDisplay()
            }
        }
    }

    // --- Play button ---

    private fun setupPlayButton() {
        binding.playBtn.setOnClickListener {
            if (isPlaying) {
                stopPlayback()
            } else {
                startPlayback()
            }
        }
    }

    private fun startPlayback() {
        val success = engine.initAudioTrack()
        if (!success) return

        engine.start { beat ->
            // Flash beat indicator
            val isAccent = beat == 0
            val color = if (isAccent) R.color.primary else R.color.on_surface_variant
            setBeatIndicatorColor(color)

            beatResetRunnable?.let { handler.removeCallbacks(it) }
            beatResetRunnable = Runnable {
                setBeatIndicatorColor(R.color.tool_icon_default)
            }
            handler.postDelayed(beatResetRunnable!!, 80)

            updateBeatCounter()
        }

        isPlaying = true
        binding.playBtn.text = "■ 停止"
        setKeepScreenOn()
        updateBeatCounter() // reset display
    }

    private fun stopPlayback() {
        engine.stop()
        isPlaying = false
        binding.playBtn.text = "▶ 开始"
        clearKeepScreenOn()
        setBeatIndicatorColor(R.color.tool_icon_default)
        binding.beatCounter.text = "${engine.beatsPerBar} / ${engine.beatsPerBar}" // show as stopped/ready
        updateBeatCounterToReady()
    }

    private fun setBeatIndicatorColor(colorRes: Int) {
        val drawable = binding.beatIndicator.background
        if (drawable is GradientDrawable) {
            drawable.setColor(ContextCompat.getColor(this, colorRes))
        }
    }

    private fun updateBeatCounter() {
        binding.beatCounter.text = "${engine.currentBeat + 1} / ${engine.beatsPerBar}"
    }

    private fun updateBeatCounterToReady() {
        binding.beatCounter.text = "1 / ${engine.beatsPerBar}"
    }

    // --- Utility ---

    private fun toggleVisibility(view: View, chevron: android.widget.TextView) {
        val expanded = view.visibility == View.VISIBLE
        view.visibility = if (expanded) View.GONE else View.VISIBLE
        chevron.text = if (expanded) "▸" else "▾"
    }

    private fun setKeepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun clearKeepScreenOn() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
