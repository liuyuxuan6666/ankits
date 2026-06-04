package com.example.ankits

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityPomodoroBinding

class PomodoroActivity : AppCompatActivity(), PomodoroCallbacks {

    private lateinit var binding: ActivityPomodoroBinding
    private val engine = PomodoroEngine(this)
    private var settings = PomodoroSettings()

    private var workDuration: Int = 25
    private var shortBreakDuration: Int = 5
    private var longBreakDuration: Int = 15
    private var longBreakInterval: Int = 4

    private var isDurationExpanded = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPomodoroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()
        loadSettings()

        binding.backBtn.setOnClickListener { handleBackPress() }
        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, PomodoroSettingsActivity::class.java))
        }
        binding.startBtn.setOnClickListener { startSession() }
        binding.pauseResumeBtn.setOnClickListener { togglePauseResume() }
        binding.stopBtn.setOnClickListener { confirmStopSession() }

        setupSessionTypeRadio()
        setupDurationControls()
        setupReminderControls()
        updateTimerDisplay()
        showIdleUI()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
        if (engine.state == PomodoroState.RUNNING) {
            engine.pause()
            updateButtonStates()
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
        binding.vibrateToggle.isChecked = settings.vibrateOnEnd
        binding.soundToggle.isChecked = settings.soundOnEnd
        binding.lockScreenToggle.isChecked = settings.lockScreen
    }

    override fun onBackPressed() {
        handleBackPress()
    }

    private fun handleBackPress() {
        if (settings.lockScreen && engine.state == PomodoroState.RUNNING) {
            Toast.makeText(this, R.string.pomodoro_lock_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        finish()
    }

    // ---- PomodoroCallbacks ----

    override fun onTick(remainingSec: Int, totalSec: Int) {
        binding.timerView.updateProgress(remainingSec, totalSec)
    }

    override fun onStateChanged(newState: PomodoroState) {
        runOnUiThread {
            when (newState) {
                PomodoroState.COMPLETED -> onTimerFinished()
                else -> {}
            }
            updateButtonStates()
        }
    }

    // ---- Session Control ----

    private fun getSelectedSessionType(): SessionType {
        return when {
            binding.radioWork.isChecked -> SessionType.WORK
            binding.radioShortBreak.isChecked -> SessionType.SHORT_BREAK
            else -> SessionType.LONG_BREAK
        }
    }

    private fun getDurationForType(type: SessionType): Int {
        return when (type) {
            SessionType.WORK -> workDuration
            SessionType.SHORT_BREAK -> shortBreakDuration
            SessionType.LONG_BREAK -> longBreakDuration
        }
    }

    private fun startSession() {
        val type = getSelectedSessionType()
        val duration = getDurationForType(type)
        engine.start(type, duration)

        val label = when (type) {
            SessionType.WORK -> getString(R.string.pomodoro_work)
            SessionType.SHORT_BREAK -> getString(R.string.pomodoro_short_break)
            SessionType.LONG_BREAK -> getString(R.string.pomodoro_long_break)
        }
        binding.timerView.setLabel(label)

        val color = when (type) {
            SessionType.WORK -> ContextCompat.getColor(this, R.color.primary)
            SessionType.SHORT_BREAK -> ContextCompat.getColor(this, R.color.error)
            SessionType.LONG_BREAK -> ContextCompat.getColor(this, R.color.error)
        }
        binding.timerView.setProgressColor(color)
        binding.timerView.updateProgress(duration * 60, duration * 60)

        updateButtonStates()
        showRunningUI()
        animateTransition(true)
    }

    private fun togglePauseResume() {
        when (engine.state) {
            PomodoroState.RUNNING -> engine.pause()
            PomodoroState.PAUSED -> engine.resume()
            else -> {}
        }
        updateButtonStates()
    }

    private fun confirmStopSession() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pomodoro_stop_confirm_title))
            .setMessage(getString(R.string.pomodoro_stop_confirm_message))
            .setPositiveButton(getString(R.string.pomodoro_confirm_yes)) { _, _ ->
                stopSession()
            }
            .setNegativeButton(getString(R.string.pomodoro_confirm_no), null)
            .show()
    }

    private fun stopSession() {
        val session = engine.stop()
        PomodoroHistoryManager.saveSession(this, session)
        binding.timerView.reset()
        binding.timerView.setLabel("")
        updateButtonStates()
        showIdleUI()
        animateTransition(false)
    }

    private fun onTimerFinished() {
        val session = engine.stop()
        PomodoroHistoryManager.saveSession(this, session)
        playFinishAlert()
        binding.timerView.playPulseAnimation()
        binding.timerView.setLabel("")
        binding.timerView.updateProgress(0, session.plannedDurationMin * 60)
        updateButtonStates()
        showIdleUI()
        animateTransition(false)
    }

    private fun animateTransition(toRunning: Boolean) {
        if (toRunning) {
            binding.timerView.scaleX = 0.6f
            binding.timerView.scaleY = 0.6f
            binding.timerView.alpha = 0f
            binding.timerView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(300)
                .start()
        } else {
            binding.timerView.animate()
                .scaleX(0.6f)
                .scaleY(0.6f)
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    binding.timerView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }
    }

    private fun playFinishAlert() {
        if (settings.soundOnEnd) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone.play()
        }
        if (settings.vibrateOnEnd) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // ---- UI Modes ----

    private fun showIdleUI() {
        binding.settingsScrollView.visibility = View.VISIBLE
        val scrollParams = binding.settingsScrollView.layoutParams as android.widget.LinearLayout.LayoutParams
        scrollParams.weight = 1f
        binding.settingsScrollView.layoutParams = scrollParams

        val timerParams = binding.timerContainer.layoutParams as android.widget.LinearLayout.LayoutParams
        timerParams.weight = 0f
        binding.timerContainer.layoutParams = timerParams
        binding.timerContainer.requestLayout()
    }

    private fun showRunningUI() {
        binding.settingsScrollView.visibility = View.GONE
        val scrollParams = binding.settingsScrollView.layoutParams as android.widget.LinearLayout.LayoutParams
        scrollParams.weight = 0f
        binding.settingsScrollView.layoutParams = scrollParams

        val timerParams = binding.timerContainer.layoutParams as android.widget.LinearLayout.LayoutParams
        timerParams.weight = 1f
        binding.timerContainer.layoutParams = timerParams
        binding.timerContainer.requestLayout()
    }

    // ---- Button States ----

    private fun updateButtonStates() {
        when (engine.state) {
            PomodoroState.IDLE -> {
                binding.startBtn.visibility = View.VISIBLE
                binding.pauseResumeBtn.visibility = View.GONE
                binding.stopBtn.visibility = View.GONE
            }
            PomodoroState.RUNNING -> {
                binding.startBtn.visibility = View.GONE
                binding.pauseResumeBtn.visibility = View.VISIBLE
                binding.pauseResumeBtn.text = getString(R.string.pomodoro_pause)
                binding.stopBtn.visibility = View.VISIBLE
            }
            PomodoroState.PAUSED -> {
                binding.startBtn.visibility = View.GONE
                binding.pauseResumeBtn.visibility = View.VISIBLE
                binding.pauseResumeBtn.text = getString(R.string.pomodoro_resume)
                binding.stopBtn.visibility = View.VISIBLE
            }
            PomodoroState.COMPLETED -> {
                binding.startBtn.visibility = View.VISIBLE
                binding.pauseResumeBtn.visibility = View.GONE
                binding.stopBtn.visibility = View.GONE
            }
        }
    }

    // ---- Session Type Radio ----

    private fun setupSessionTypeRadio() {
        binding.sessionTypeGroup.setOnCheckedChangeListener { _, _ ->
            updateTimerDisplay()
            updateDurationLabels()
        }
    }

    // ---- Duration Controls ----

    private fun setupDurationControls() {
        binding.durationHeader.setOnClickListener {
            isDurationExpanded = !isDurationExpanded
            binding.durationChevron.text = if (isDurationExpanded) "\u25BE" else "\u25B8"
            binding.durationContent.visibility = if (isDurationExpanded) View.VISIBLE else View.GONE
        }

        binding.workMinusBtn.setOnClickListener { adjustWorkDuration(-1) }
        binding.workPlusBtn.setOnClickListener { adjustWorkDuration(1) }
        binding.shortBreakMinusBtn.setOnClickListener { adjustShortBreakDuration(-1) }
        binding.shortBreakPlusBtn.setOnClickListener { adjustShortBreakDuration(1) }
        binding.longBreakMinusBtn.setOnClickListener { adjustLongBreakDuration(-1) }
        binding.longBreakPlusBtn.setOnClickListener { adjustLongBreakDuration(1) }
        binding.intervalMinusBtn.setOnClickListener { adjustInterval(-1) }
        binding.intervalPlusBtn.setOnClickListener { adjustInterval(1) }

        updateDurationLabels()
    }

    // ---- Reminder Controls ----

    private fun setupReminderControls() {
        binding.vibrateToggle.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(vibrateOnEnd = isChecked)
        }
        binding.soundToggle.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(soundOnEnd = isChecked)
        }
        binding.lockScreenToggle.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(lockScreen = isChecked)
        }
    }

    private fun adjustWorkDuration(delta: Int) {
        workDuration = (workDuration + delta).coerceIn(1, 120)
        settings = settings.copy(workDurationMin = workDuration)
        updateDurationLabels()
        updateTimerDisplay()
    }

    private fun adjustShortBreakDuration(delta: Int) {
        shortBreakDuration = (shortBreakDuration + delta).coerceIn(1, 60)
        settings = settings.copy(shortBreakMin = shortBreakDuration)
        updateDurationLabels()
    }

    private fun adjustLongBreakDuration(delta: Int) {
        longBreakDuration = (longBreakDuration + delta).coerceIn(1, 60)
        settings = settings.copy(longBreakMin = longBreakDuration)
        updateDurationLabels()
    }

    private fun adjustInterval(delta: Int) {
        longBreakInterval = (longBreakInterval + delta).coerceIn(1, 10)
        settings = settings.copy(longBreakInterval = longBreakInterval)
        updateDurationLabels()
    }

    private fun updateDurationLabels() {
        binding.workDurationText.text = "$workDuration ${getString(R.string.pomodoro_min)}"
        binding.shortBreakDurationText.text = "$shortBreakDuration ${getString(R.string.pomodoro_min)}"
        binding.longBreakDurationText.text = "$longBreakDuration ${getString(R.string.pomodoro_min)}"
        binding.intervalText.text = "$longBreakInterval"
    }

    // ---- Timer Display ----

    private fun updateTimerDisplay() {
        val type = getSelectedSessionType()
        val duration = getDurationForType(type)
        binding.timerView.updateProgress(duration * 60, duration * 60)
    }

    // ---- Settings Persistence ----

    private fun loadSettings() {
        val prefs = getSharedPreferences("pomodoro", Context.MODE_PRIVATE)
        workDuration = prefs.getInt("work_duration", 25)
        shortBreakDuration = prefs.getInt("short_break_duration", 5)
        longBreakDuration = prefs.getInt("long_break_duration", 15)
        longBreakInterval = prefs.getInt("long_break_interval", 4)
        settings = PomodoroSettings(
            workDurationMin = workDuration,
            shortBreakMin = shortBreakDuration,
            longBreakMin = longBreakDuration,
            longBreakInterval = longBreakInterval,
            vibrateOnEnd = prefs.getBoolean("vibrate_on_end", true),
            soundOnEnd = prefs.getBoolean("sound_on_end", true),
            lockScreen = prefs.getBoolean("lock_screen", false)
        )
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("pomodoro", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("work_duration", workDuration)
            .putInt("short_break_duration", shortBreakDuration)
            .putInt("long_break_duration", longBreakDuration)
            .putInt("long_break_interval", longBreakInterval)
            .putBoolean("vibrate_on_end", settings.vibrateOnEnd)
            .putBoolean("sound_on_end", settings.soundOnEnd)
            .putBoolean("lock_screen", settings.lockScreen)
            .apply()
    }

    // ---- Window Insets ----

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.rootLayout.setPadding(0, statusBar.top, 0, navBar.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
