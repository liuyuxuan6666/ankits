package com.example.ankits

import android.app.AlertDialog
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.SleepAidEngine.SoundType
import com.example.ankits.databinding.ActivitySleepAidBinding
import com.google.android.material.chip.Chip
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.Random
import java.util.UUID

class SleepAidActivity : AppCompatActivity() {

    companion object {
        private var hasVisitedThisSession = false
    }

    private lateinit var binding: ActivitySleepAidBinding
    private val engine = SleepAidEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private val prefs by lazy { getSharedPreferences("sleep_aid", MODE_PRIVATE) }

    private var isPlaying = false
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrackIndex = -1
    private var shuffleEnabled = false

    private data class ImportedAudio(
        val id: String,
        val displayName: String,
        val filePath: String,
        val durationMs: Long,
        val importTimeMs: Long
    )

    private val importedAudioList = mutableListOf<ImportedAudio>()

    private var timerDurationMs = 0L
    private var timerRemainingMs = 0L
    private var timerTickRunnable: Runnable? = null
    private var fadeOutRunnable: Runnable? = null
    private var isFadingOut = false
    private var fadeOutEnabled = true
    private var selectedTimerMinutes = 30
    private var isCustomTimer = false

    private val soundToggles = mutableMapOf<SoundType, SwitchCompat>()
    private val soundSeekBars = mutableMapOf<SoundType, SeekBar>()
    private val timerChips = mutableListOf<Chip>()
    private var customTimerChip: Chip? = null

    private val audioPickLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importAudioFromUri(uri) }

    private val videoPickLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) extractAudioFromVideo(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySleepAidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()
        setupToolbar()
        setupBuiltinSounds()
        setupMyAudio()
        setupTimer()
        setupPlayButton()
        loadImportedAudio()

        if (!hasVisitedThisSession) {
            loadHabitsOrDefaults()
            hasVisitedThisSession = true
        } else {
            loadSessionState()
        }
    }

    override fun onPause() {
        super.onPause()
        saveSessionState()
        if (isPlaying) stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
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
        binding.settingsBtn.setOnClickListener { showSettingsDialog() }
    }

    // --- Built-in Sounds ---

    private val soundNames = mapOf(
        SoundType.WHITE_NOISE to R.string.sleep_aid_white_noise,
        SoundType.PINK_NOISE to R.string.sleep_aid_pink_noise,
        SoundType.BROWN_NOISE to R.string.sleep_aid_brown_noise,
        SoundType.RAIN to R.string.sleep_aid_rain,
        SoundType.OCEAN to R.string.sleep_aid_ocean,
        SoundType.FAN to R.string.sleep_aid_fan,
        SoundType.SINGING_BOWL to R.string.sleep_aid_singing_bowl
    )

    private val defaultVolumes = mapOf(
        SoundType.WHITE_NOISE to 0.3f,
        SoundType.PINK_NOISE to 0.3f,
        SoundType.BROWN_NOISE to 0.35f,
        SoundType.RAIN to 0.5f,
        SoundType.OCEAN to 0.45f,
        SoundType.FAN to 0.4f,
        SoundType.SINGING_BOWL to 0.5f
    )

    private fun setupBuiltinSounds() {
        for (type in SoundType.entries) {
            val channel = engine.getChannel(type)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
            }

            val toggle = SwitchCompat(this).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    channel.enabled = isChecked
                }
            }
            soundToggles[type] = toggle

            val name = TextView(this).apply {
                text = getString(soundNames[type]!!)
                setTextColor(ContextCompat.getColor(this@SleepAidActivity, R.color.on_surface))
                textSize = 13f
                val params = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                params.marginStart = (8 * resources.displayMetrics.density).toInt()
                layoutParams = params
            }

            val volSeek = SeekBar(this).apply {
                max = 100
                val params = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f
                )
                params.marginStart = (12 * resources.displayMetrics.density).toInt()
                layoutParams = params
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) channel.volume = progress / 100f
                    }
                    override fun onStartTrackingTouch(seek: SeekBar) {}
                    override fun onStopTrackingTouch(seek: SeekBar) {}
                })
            }
            soundSeekBars[type] = volSeek

            row.addView(toggle)
            row.addView(name)
            row.addView(volSeek)
            binding.builtinSoundContent.addView(row)
        }

        binding.builtinSoundHeader.setOnClickListener {
            toggleVisibility(binding.builtinSoundContent, binding.builtinSoundChevron)
        }
    }

    // --- My Audio ---

    private fun setupMyAudio() {
        binding.importAudioBtn.setOnClickListener {
            audioPickLauncher.launch(arrayOf("audio/*"))
        }
        binding.importVideoBtn.setOnClickListener {
            videoPickLauncher.launch(arrayOf("video/*"))
        }
        binding.shuffleToggle.setOnCheckedChangeListener { _, checked ->
            shuffleEnabled = checked
        }
        binding.myAudioHeader.setOnClickListener {
            toggleVisibility(binding.myAudioContent, binding.myAudioChevron)
        }
    }

    private fun importAudioFromUri(uri: Uri) {
        try {
            val displayName = queryDisplayName(uri)
            val destDir = File(filesDir, "sleep_aid")
            destDir.mkdirs()
            val ext = displayName.substringAfterLast('.', "mp3")
            val destFile = File(destDir, "${UUID.randomUUID()}.$ext")

            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                showToast(getString(R.string.sleep_aid_import_failed))
                return
            }

            var durationMs = 0L
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(destFile.absolutePath)
                    prepare()
                }
                durationMs = mp.duration.toLong()
                mp.release()
            } catch (_: Exception) {}

            val audio = ImportedAudio(
                id = UUID.randomUUID().toString(),
                displayName = displayName.substringBeforeLast('.'),
                filePath = destFile.absolutePath,
                durationMs = durationMs,
                importTimeMs = System.currentTimeMillis()
            )
            importedAudioList.add(audio)
            saveImportedAudio()
            refreshImportedAudioListUI()
            showToast(getString(R.string.sleep_aid_import_success))
        } catch (e: Exception) {
            showToast(getString(R.string.sleep_aid_import_failed))
        }
    }

    private fun extractAudioFromVideo(uri: Uri) {
        showToast(getString(R.string.sleep_aid_extracting_audio))

        Thread {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(this@SleepAidActivity, uri, null)

                var audioIdx = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        audioIdx = i
                        audioFormat = fmt
                        break
                    }
                }

                if (audioIdx < 0) {
                    handler.post { showToast(getString(R.string.sleep_aid_no_audio_track)) }
                    extractor.release()
                    return@Thread
                }

                extractor.selectTrack(audioIdx)

                val destDir = File(filesDir, "sleep_aid")
                destDir.mkdirs()
                val outFile = File(destDir, "${UUID.randomUUID()}.m4a")
                val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxerTrackIdx = muxer.addTrack(audioFormat!!)
                muxer.start()

                val bufferInfo = MediaCodec.BufferInfo()
                val byteBuffer = ByteBuffer.allocateDirect(256 * 1024)

                while (true) {
                    val sampleSize = extractor.readSampleData(byteBuffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.flags = extractor.sampleFlags
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    muxer.writeSampleData(muxerTrackIdx, byteBuffer, bufferInfo)
                    extractor.advance()
                }

                muxer.stop()
                muxer.release()
                extractor.release()

                handler.post {
                    val displayName = queryDisplayName(uri)
                    val baseName = displayName.substringBeforeLast('.')
                    val audio = ImportedAudio(
                        id = UUID.randomUUID().toString(),
                        displayName = "$baseName (音频)",
                        filePath = outFile.absolutePath,
                        durationMs = 0,
                        importTimeMs = System.currentTimeMillis()
                    )
                    importedAudioList.add(audio)
                    saveImportedAudio()
                    refreshImportedAudioListUI()
                    showToast(getString(R.string.sleep_aid_import_success))
                }
            } catch (e: Exception) {
                handler.post { showToast(getString(R.string.sleep_aid_import_failed)) }
            }
        }.start()
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    private fun refreshImportedAudioListUI() {
        binding.importedAudioList.removeAllViews()

        if (importedAudioList.isEmpty()) {
            binding.importedAudioList.addView(TextView(this).apply {
                text = getString(R.string.sleep_aid_no_audio)
                setTextColor(ContextCompat.getColor(this@SleepAidActivity, R.color.on_surface_variant))
                textSize = 12f
                val pad = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
            })
            return
        }

        for ((index, audio) in importedAudioList.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
                isClickable = true
                isFocusable = true
                background = getDrawable(android.R.attr.selectableItemBackground)

                if (index == currentTrackIndex) {
                    setBackgroundColor(0x1A_1A73E8.toInt())
                }
            }

            val name = TextView(this).apply {
                text = audio.displayName
                setTextColor(ContextCompat.getColor(this@SleepAidActivity, R.color.on_surface))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val deleteBtn = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setBackgroundResource(android.R.attr.selectableItemBackgroundBorderless)
                setOnClickListener { deleteImportedAudio(index) }
            }

            row.setOnClickListener {
                currentTrackIndex = index
                refreshImportedAudioListUI()
                if (isPlaying) playImportedAudio(index)
            }

            row.addView(name)
            row.addView(deleteBtn)
            binding.importedAudioList.addView(row)
        }
    }

    private fun deleteImportedAudio(index: Int) {
        val audio = importedAudioList[index]
        if (index == currentTrackIndex) {
            stopImportedAudio()
            currentTrackIndex = -1
        }
        try { File(audio.filePath).delete() } catch (_: Exception) {}
        importedAudioList.removeAt(index)
        if (currentTrackIndex >= importedAudioList.size) currentTrackIndex = importedAudioList.size - 1
        saveImportedAudio()
        refreshImportedAudioListUI()
    }

    // --- Timer ---

    private val chipDurations = listOf(
        15 to R.string.sleep_aid_timer_15,
        30 to R.string.sleep_aid_timer_30,
        45 to R.string.sleep_aid_timer_45,
        60 to R.string.sleep_aid_timer_60
    )

    private fun setupTimer() {
        for ((min, labelRes) in chipDurations) {
            val chip = Chip(binding.timerChips.context).apply {
                text = getString(labelRes)
                isCheckable = true
                setOnClickListener {
                    isCustomTimer = false
                    selectedTimerMinutes = min
                    timerDurationMs = min * 60 * 1000L
                    binding.customTimerRow.visibility = View.GONE
                    updateTimerDisplay()
                }
            }
            timerChips.add(chip)
            binding.timerChips.addView(chip)
        }

        val customChip = Chip(binding.timerChips.context).apply {
            text = getString(R.string.sleep_aid_timer_custom)
            isCheckable = true
            setOnClickListener {
                isCustomTimer = true
                selectedTimerMinutes = binding.timerMinutesPicker.value
                binding.customTimerRow.visibility = View.VISIBLE
                timerDurationMs = selectedTimerMinutes * 60 * 1000L
                updateTimerDisplay()
            }
        }
        this.customTimerChip = customChip
        timerChips.add(customChip)
        binding.timerChips.addView(customChip)

        binding.timerMinutesPicker.minValue = 1
        binding.timerMinutesPicker.maxValue = 120
        binding.timerMinutesPicker.value = selectedTimerMinutes
        binding.timerMinutesPicker.setOnValueChangedListener { _, _, newVal ->
            selectedTimerMinutes = newVal
            if (isCustomTimer) {
                timerDurationMs = newVal * 60 * 1000L
                updateTimerDisplay()
            }
        }

        binding.fadeOutToggle.setOnCheckedChangeListener { _, checked ->
            fadeOutEnabled = checked
        }

        binding.timerHeader.setOnClickListener {
            toggleVisibility(binding.timerContent, binding.timerChevron)
        }
    }

    private fun updateTimerDisplay() {
        if (timerRemainingMs <= 0 && !isPlaying) {
            binding.timerRemainingText.text = getString(R.string.sleep_aid_timer_off)
            binding.timerRemainingText.setTextColor(
                ContextCompat.getColor(this, R.color.on_surface_variant)
            )
            return
        }
        val totalSec = (timerRemainingMs / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        binding.timerRemainingText.text = getString(R.string.sleep_aid_timer_remaining, min, sec)

        val color = if (isFadingOut) R.color.error else R.color.on_surface_variant
        binding.timerRemainingText.setTextColor(ContextCompat.getColor(this, color))
    }

    // --- Play/Stop ---

    private fun setupPlayButton() {
        binding.playBtn.setOnClickListener {
            if (isPlaying) stopPlayback() else startPlayback()
        }
    }

    private fun startPlayback() {
        val hasBuiltin = engine.channels.any { it.enabled }
        val hasImported = currentTrackIndex in importedAudioList.indices

        if (!hasBuiltin && !hasImported) {
            showToast(getString(R.string.sleep_aid_no_selection))
            return
        }

        if (hasBuiltin) {
            if (!engine.start()) {
                showToast(getString(R.string.sleep_aid_audio_init_failed))
                return
            }
        }

        if (hasImported) {
            playImportedAudio(currentTrackIndex)
        }

        if (timerDurationMs > 0) startTimer(timerDurationMs)

        isPlaying = true
        binding.playBtn.text = getString(R.string.sleep_aid_stop)
        setKeepScreenOn()
    }

    private fun stopPlayback() {
        engine.stop()

        stopImportedAudio()

        timerTickRunnable?.let { handler.removeCallbacks(it) }
        timerTickRunnable = null
        fadeOutRunnable?.let { handler.removeCallbacks(it) }
        fadeOutRunnable = null
        isFadingOut = false
        timerRemainingMs = 0L
        updateTimerDisplay()
        binding.timerChips.clearCheck()

        isPlaying = false
        binding.playBtn.text = getString(R.string.sleep_aid_play)
        clearKeepScreenOn()
    }

    // --- Imported Audio Playback ---

    private fun playImportedAudio(index: Int) {
        stopImportedAudio()
        if (index !in importedAudioList.indices) return

        val audio = importedAudioList[index]
        currentTrackIndex = index

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(audio.filePath)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { advanceToNextTrack() }
            setOnErrorListener { _, _, _ -> advanceToNextTrack(); true }
            prepareAsync()
        }

        refreshImportedAudioListUI()
    }

    private fun stopImportedAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun advanceToNextTrack() {
        if (importedAudioList.isEmpty()) return

        val next = if (shuffleEnabled) {
            if (importedAudioList.size == 1) 0
            else {
                var r = random.nextInt(importedAudioList.size)
                while (r == currentTrackIndex && importedAudioList.size > 1) {
                    r = random.nextInt(importedAudioList.size)
                }
                r
            }
        } else {
            (currentTrackIndex + 1) % importedAudioList.size
        }

        currentTrackIndex = next
        playImportedAudio(next)
    }

    // --- Timer Logic ---

    private fun startTimer(durationMs: Long) {
        timerRemainingMs = durationMs
        isFadingOut = false
        updateTimerDisplay()

        timerTickRunnable?.let { handler.removeCallbacks(it) }
        timerTickRunnable = object : Runnable {
            override fun run() {
                timerRemainingMs -= 1000

                if (timerRemainingMs <= 0) {
                    stopPlayback()
                    timerRemainingMs = 0L
                    updateTimerDisplay()
                    return
                }

                if (fadeOutEnabled && !isFadingOut && timerRemainingMs <= 30000) {
                    beginFadeOut(timerRemainingMs)
                }

                updateTimerDisplay()
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(timerTickRunnable!!, 1000)
    }

    private fun beginFadeOut(durationMs: Long) {
        isFadingOut = true
        updateTimerDisplay()
        engine.startFadeOut()

        val steps = (durationMs / 50).coerceIn(1, 600)
        val stepMs = durationMs / steps
        val increment = 1f / steps

        fadeOutRunnable?.let { handler.removeCallbacks(it) }
        var step = 0
        fadeOutRunnable = object : Runnable {
            override fun run() {
                step++
                engine.fadeProgress = (increment * step).coerceAtMost(1f)
                val mpVol = (1f - engine.fadeProgress).coerceAtLeast(0f)
                try { mediaPlayer?.setVolume(mpVol, mpVol) } catch (_: Exception) {}
                if (step < steps && engine.isPlaying) {
                    handler.postDelayed(this, stepMs)
                }
            }
        }
        handler.postDelayed(fadeOutRunnable!!, stepMs)
    }

    // --- Session State Persistence ---

    private fun saveSessionState() {
        val editor = prefs.edit()
        for (type in SoundType.entries) {
            editor.putBoolean("session_sound_${type.name}_enabled", soundToggles[type]?.isChecked ?: false)
            editor.putFloat("session_sound_${type.name}_volume", engine.getChannel(type).volume)
        }
        editor.putInt("session_timer_minutes", selectedTimerMinutes)
        editor.putBoolean("session_timer_custom", isCustomTimer)
        editor.putBoolean("session_fade_out", fadeOutEnabled)
        editor.putBoolean("session_shuffle", shuffleEnabled)
        editor.putFloat("session_master_volume", engine.masterVolume)
        editor.apply()
    }

    private fun loadSessionState() {
        for (type in SoundType.entries) {
            val enabled = prefs.getBoolean("session_sound_${type.name}_enabled", false)
            val volume = prefs.getFloat("session_sound_${type.name}_volume", defaultVolumes[type]!!)
            val channel = engine.getChannel(type)
            channel.enabled = enabled
            channel.volume = volume
            soundToggles[type]?.isChecked = enabled
            soundSeekBars[type]?.progress = (volume * 100).toInt()
        }

        selectedTimerMinutes = prefs.getInt("session_timer_minutes", 30)
        isCustomTimer = prefs.getBoolean("session_timer_custom", false)
        fadeOutEnabled = prefs.getBoolean("session_fade_out", true)
        shuffleEnabled = prefs.getBoolean("session_shuffle", false)

        applyTimerUIState()
        binding.fadeOutToggle.isChecked = fadeOutEnabled
        binding.shuffleToggle.isChecked = shuffleEnabled
        engine.masterVolume = prefs.getFloat("session_master_volume", 1f)
    }

    // --- Habits Persistence ---

    private fun saveHabits() {
        val editor = prefs.edit()
        for (type in SoundType.entries) {
            editor.putBoolean("habit_sound_${type.name}_enabled", soundToggles[type]?.isChecked ?: false)
            editor.putFloat("habit_sound_${type.name}_volume", engine.getChannel(type).volume)
        }
        editor.putInt("habit_timer_minutes", selectedTimerMinutes)
        editor.putBoolean("habit_timer_custom", isCustomTimer)
        editor.putBoolean("habit_fade_out", fadeOutEnabled)
        editor.putBoolean("habit_shuffle", shuffleEnabled)
        editor.putBoolean("habits_configured", true)
        editor.apply()
    }

    private fun loadHabits(): Boolean {
        if (!prefs.getBoolean("habits_configured", false)) return false

        for (type in SoundType.entries) {
            val enabled = prefs.getBoolean("habit_sound_${type.name}_enabled", false)
            val volume = prefs.getFloat("habit_sound_${type.name}_volume", defaultVolumes[type]!!)
            val channel = engine.getChannel(type)
            channel.enabled = enabled
            channel.volume = volume
            soundToggles[type]?.isChecked = enabled
            soundSeekBars[type]?.progress = (volume * 100).toInt()
        }

        selectedTimerMinutes = prefs.getInt("habit_timer_minutes", 30)
        isCustomTimer = prefs.getBoolean("habit_timer_custom", false)
        fadeOutEnabled = prefs.getBoolean("habit_fade_out", true)
        shuffleEnabled = prefs.getBoolean("habit_shuffle", false)

        applyTimerUIState()
        binding.fadeOutToggle.isChecked = fadeOutEnabled
        binding.shuffleToggle.isChecked = shuffleEnabled
        return true
    }

    private fun loadHabitsOrDefaults() {
        if (!loadHabits()) {
            for (type in SoundType.entries) {
                val channel = engine.getChannel(type)
                channel.volume = defaultVolumes[type]!!
                channel.enabled = false
                soundToggles[type]?.isChecked = false
                soundSeekBars[type]?.progress = (channel.volume * 100).toInt()
            }
            selectedTimerMinutes = 30
            isCustomTimer = false
            fadeOutEnabled = true
            shuffleEnabled = false
            applyTimerUIState()
            binding.fadeOutToggle.isChecked = true
            binding.shuffleToggle.isChecked = false
        }
    }

    private fun clearHabits() {
        val editor = prefs.edit()
        editor.putBoolean("habits_configured", false)
        editor.remove("habit_timer_minutes")
        editor.remove("habit_timer_custom")
        editor.remove("habit_fade_out")
        editor.remove("habit_shuffle")
        for (type in SoundType.entries) {
            editor.remove("habit_sound_${type.name}_enabled")
            editor.remove("habit_sound_${type.name}_volume")
        }
        editor.apply()
    }

    private fun applyTimerUIState() {
        for ((i, chip) in timerChips.withIndex()) {
            chip.isChecked = false
        }

        if (isCustomTimer) {
            customTimerChip?.isChecked = true
            binding.customTimerRow.visibility = View.VISIBLE
            binding.timerMinutesPicker.value = selectedTimerMinutes
            timerDurationMs = selectedTimerMinutes * 60 * 1000L
        } else {
            val idx = chipDurations.indexOfFirst { it.first == selectedTimerMinutes }
            if (idx >= 0) {
                timerChips[idx].isChecked = true
                binding.customTimerRow.visibility = View.GONE
            }
            timerDurationMs = selectedTimerMinutes * 60 * 1000L
        }
        updateTimerDisplay()
    }

    // --- Settings Dialog ---

    private fun showSettingsDialog() {
        val hasHabits = prefs.getBoolean("habits_configured", false)
        val items = mutableListOf(
            getString(R.string.sleep_aid_save_as_habit)
        )
        if (hasHabits) {
            items.add(getString(R.string.sleep_aid_clear_habits))
        }
        items.add(getString(R.string.sleep_aid_restore_defaults))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sleep_aid_settings_title))
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    getString(R.string.sleep_aid_save_as_habit) -> {
                        saveHabits()
                        showToast(getString(R.string.sleep_aid_habit_saved))
                    }
                    getString(R.string.sleep_aid_clear_habits) -> {
                        clearHabits()
                        showToast(getString(R.string.sleep_aid_habits_cleared))
                    }
                    getString(R.string.sleep_aid_restore_defaults) -> {
                        if (hasHabits) {
                            loadHabits()
                        } else {
                            showToast(getString(R.string.sleep_aid_no_habits))
                        }
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    // --- Imported Audio Persistence ---

    private fun saveImportedAudio() {
        val arr = JSONArray()
        for (a in importedAudioList) {
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("displayName", a.displayName)
                put("filePath", a.filePath)
                put("durationMs", a.durationMs)
                put("importTimeMs", a.importTimeMs)
            })
        }
        prefs.edit().putString("imported_audio", arr.toString()).apply()
    }

    private fun loadImportedAudio() {
        try {
            val json = prefs.getString("imported_audio", "[]") ?: "[]"
            val arr = JSONArray(json)
            importedAudioList.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                importedAudioList.add(ImportedAudio(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    filePath = obj.getString("filePath"),
                    durationMs = obj.optLong("durationMs", 0),
                    importTimeMs = obj.optLong("importTimeMs", 0)
                ))
            }
        } catch (_: Exception) {
            importedAudioList.clear()
        }
        refreshImportedAudioListUI()
    }

    // --- Utility ---

    private fun toggleVisibility(view: View, chevron: TextView) {
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

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
