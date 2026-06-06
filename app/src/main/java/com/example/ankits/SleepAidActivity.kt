package com.example.ankits

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
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
    private var service: SleepAidService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private val prefs by lazy { getSharedPreferences("sleep_aid", MODE_PRIVATE) }

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

    private var selectedTimerMinutes = 30
    private var isCustomTimer = false
    private var fadeOutEnabled = true

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

    private var statePushedToService = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as SleepAidService.LocalBinder).getService()
            service?.onStateChanged = { handler.post { syncUiFromService() } }
            service?.onTimerTick = { remaining ->
                handler.post { updateTimerDisplay(remaining) }
            }
            if (service?.isPlaying == true) {
                pullStateFromService()
            } else if (!statePushedToService) {
                pushStateToService()
                statePushedToService = true
            }
            syncUiFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.onStateChanged = null
            service?.onTimerTick = null
            service = null
        }
    }

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

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, SleepAidService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        syncUiFromService()
    }

    override fun onPause() {
        super.onPause()
        saveSessionState()
    }

    override fun onStop() {
        super.onStop()
        service?.onStateChanged = null
        service?.onTimerTick = null
        unbindService(serviceConnection)
    }

    override fun onDestroy() {
        super.onDestroy()
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
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
            }

            val toggle = SwitchCompat(this).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    service?.engine?.getChannel(type)?.enabled = isChecked
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
                        if (fromUser) {
                            service?.engine?.getChannel(type)?.volume = progress / 100f
                        }
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
            service?.shuffleEnabled = checked
        }
        binding.myAudioHeader.setOnClickListener {
            toggleVisibility(binding.myAudioContent, binding.myAudioChevron)
        }
    }

    private fun importAudioFromUri(uri: Uri) {
        showToast(getString(R.string.sleep_aid_importing))
        Thread {
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
                    handler.post { showToast(getString(R.string.sleep_aid_import_failed)) }
                    return@Thread
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

                handler.post {
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
                }
            } catch (e: SecurityException) {
                handler.post {
                    showToast(getString(R.string.sleep_aid_import_permission_error))
                }
            } catch (e: Exception) {
                handler.post {
                    showToast(getString(R.string.sleep_aid_import_failed))
                }
            }
        }.start()
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
                if (service?.isPlaying == true) {
                    service?.syncImportedAudio(
                        importedAudioList.map { it.filePath },
                        index,
                        shuffleEnabled
                    )
                    service?.playImportedAudio(index)
                }
            }

            row.addView(name)
            row.addView(deleteBtn)
            binding.importedAudioList.addView(row)
        }
    }

    private fun deleteImportedAudio(index: Int) {
        val audio = importedAudioList[index]
        if (index == currentTrackIndex) {
            service?.stopImportedAudio()
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
                    binding.customTimerRow.visibility = View.GONE
                    updateTimerSelection()
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
                updateTimerSelection()
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
            if (isCustomTimer) updateTimerSelection()
        }

        binding.fadeOutToggle.setOnCheckedChangeListener { _, checked ->
            fadeOutEnabled = checked
        }

        binding.timerHeader.setOnClickListener {
            toggleVisibility(binding.timerContent, binding.timerChevron)
        }
    }

    private fun updateTimerSelection() {
        val durationMs = selectedTimerMinutes * 60 * 1000L
        service?.setTimer(durationMs, fadeOutEnabled)
        binding.timerRemainingText.text = getString(R.string.sleep_aid_timer_off)
        binding.timerRemainingText.setTextColor(
            ContextCompat.getColor(this, R.color.on_surface_variant)
        )
    }

    private fun updateTimerDisplay(remainingMs: Long) {
        if (remainingMs <= 0) {
            binding.timerRemainingText.text = getString(R.string.sleep_aid_timer_off)
            binding.timerRemainingText.setTextColor(
                ContextCompat.getColor(this, R.color.on_surface_variant)
            )
            return
        }
        val totalSec = (remainingMs / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        binding.timerRemainingText.text = getString(R.string.sleep_aid_timer_remaining, min, sec)

        val color = if (service?.isFadingOut == true) R.color.error else R.color.on_surface_variant
        binding.timerRemainingText.setTextColor(ContextCompat.getColor(this, color))
    }

    // --- Play/Stop ---

    private fun setupPlayButton() {
        binding.playBtn.setOnClickListener {
            val s = service ?: return@setOnClickListener

            if (s.isPlaying) {
                s.stopPlayback()
                syncUiFromService()
            } else {
                val hasBuiltin = s.engine.channels.any { it.enabled }
                val hasImported = importedAudioList.isNotEmpty()

                if (!hasBuiltin && !hasImported) {
                    showToast(getString(R.string.sleep_aid_no_selection))
                    return@setOnClickListener
                }

                // Set timer
                val durationMs = if (binding.timerChips.checkedChipId != View.NO_ID) {
                    selectedTimerMinutes * 60 * 1000L
                } else {
                    0L
                }
                s.setTimer(durationMs, fadeOutEnabled)

                // Sync imported audio
                s.syncImportedAudio(
                    importedAudioList.map { it.filePath },
                    currentTrackIndex,
                    shuffleEnabled
                )

                // Start the service explicitly so it survives unbinding
                startService(Intent(this, SleepAidService::class.java))
                statePushedToService = true

                if (!s.startPlayback()) {
                    showToast(getString(R.string.sleep_aid_audio_init_failed))
                    return@setOnClickListener
                }
                syncUiFromService()
                setKeepScreenOn()
            }
        }

        binding.skipPrevBtn.setOnClickListener {
            service?.skipToPrevious()
            service?.let { currentTrackIndex = it.currentTrackIndex }
            syncUiFromService()
            refreshImportedAudioListUI()
        }

        binding.skipNextBtn.setOnClickListener {
            service?.skipToNext()
            service?.let { currentTrackIndex = it.currentTrackIndex }
            syncUiFromService()
            refreshImportedAudioListUI()
        }
    }

    private fun syncUiFromService() {
        val s = service ?: return
        val hasMultipleTracks = importedAudioList.size > 1
        if (s.isPlaying) {
            binding.playBtn.text = getString(R.string.sleep_aid_stop)
            setKeepScreenOn()
        } else {
            binding.playBtn.text = getString(R.string.sleep_aid_play)
            clearKeepScreenOn()
        }
        binding.skipPrevBtn.visibility = if (hasMultipleTracks) View.VISIBLE else View.GONE
        binding.skipNextBtn.visibility = if (hasMultipleTracks) View.VISIBLE else View.GONE
    }

    private fun pushStateToService() {
        val s = service ?: return
        for (type in SoundType.entries) {
            val enabled = soundToggles[type]?.isChecked ?: false
            val volume = (soundSeekBars[type]?.progress ?: 50) / 100f
            s.engine.getChannel(type).enabled = enabled
            s.engine.getChannel(type).volume = volume
        }
        s.shuffleEnabled = shuffleEnabled
        s.syncImportedAudio(importedAudioList.map { it.filePath }, currentTrackIndex, shuffleEnabled)
    }

    private fun pullStateFromService() {
        val s = service ?: return
        for (type in SoundType.entries) {
            val ch = s.engine.getChannel(type)
            soundToggles[type]?.isChecked = ch.enabled
            soundSeekBars[type]?.progress = (ch.volume * 100).toInt()
        }
        shuffleEnabled = s.shuffleEnabled
        binding.shuffleToggle.isChecked = shuffleEnabled
        currentTrackIndex = s.currentTrackIndex
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

    // --- Session State Persistence ---

    private fun saveSessionState() {
        val editor = prefs.edit()
        for (type in SoundType.entries) {
            editor.putBoolean("session_sound_${type.name}_enabled", soundToggles[type]?.isChecked ?: false)
            editor.putFloat("session_sound_${type.name}_volume", service?.engine?.getChannel(type)?.volume ?: defaultVolumes[type]!!)
        }
        editor.putInt("session_timer_minutes", selectedTimerMinutes)
        editor.putBoolean("session_timer_custom", isCustomTimer)
        editor.putBoolean("session_fade_out", fadeOutEnabled)
        editor.putBoolean("session_shuffle", shuffleEnabled)
        editor.apply()
    }

    private fun loadSessionState() {
        for (type in SoundType.entries) {
            val enabled = prefs.getBoolean("session_sound_${type.name}_enabled", false)
            val volume = prefs.getFloat("session_sound_${type.name}_volume", defaultVolumes[type]!!)
            soundToggles[type]?.isChecked = enabled
            soundSeekBars[type]?.progress = (volume * 100).toInt()
            service?.engine?.getChannel(type)?.apply {
                this.enabled = enabled
                this.volume = volume
            }
        }

        selectedTimerMinutes = prefs.getInt("session_timer_minutes", 30)
        isCustomTimer = prefs.getBoolean("session_timer_custom", false)
        fadeOutEnabled = prefs.getBoolean("session_fade_out", true)
        shuffleEnabled = prefs.getBoolean("session_shuffle", false)

        applyTimerUIState()
        binding.fadeOutToggle.isChecked = fadeOutEnabled
        binding.shuffleToggle.isChecked = shuffleEnabled
    }

    // --- Habits Persistence ---

    private fun saveHabits() {
        val editor = prefs.edit()
        for (type in SoundType.entries) {
            editor.putBoolean("habit_sound_${type.name}_enabled", soundToggles[type]?.isChecked ?: false)
            editor.putFloat("habit_sound_${type.name}_volume", service?.engine?.getChannel(type)?.volume ?: defaultVolumes[type]!!)
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
            soundToggles[type]?.isChecked = enabled
            soundSeekBars[type]?.progress = (volume * 100).toInt()
            service?.engine?.getChannel(type)?.apply {
                this.enabled = enabled
                this.volume = volume
            }
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
                val volume = defaultVolumes[type]!!
                soundToggles[type]?.isChecked = false
                soundSeekBars[type]?.progress = (volume * 100).toInt()
                service?.engine?.getChannel(type)?.apply {
                    this.volume = volume
                    this.enabled = false
                }
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
        for (chip in timerChips) {
            chip.isChecked = false
        }

        if (isCustomTimer) {
            customTimerChip?.isChecked = true
            binding.customTimerRow.visibility = View.VISIBLE
            binding.timerMinutesPicker.value = selectedTimerMinutes
        } else {
            val idx = chipDurations.indexOfFirst { it.first == selectedTimerMinutes }
            if (idx >= 0) {
                timerChips[idx].isChecked = true
                binding.customTimerRow.visibility = View.GONE
            }
        }
        binding.timerRemainingText.text = getString(R.string.sleep_aid_timer_off)
        binding.timerRemainingText.setTextColor(
            ContextCompat.getColor(this, R.color.on_surface_variant)
        )
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
