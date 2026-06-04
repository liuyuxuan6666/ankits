package com.example.ankits

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ankits.databinding.ActivityPomodoroSettingsBinding

class PomodoroSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPomodoroSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPomodoroSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleWindowInsets()
        binding.backBtn.setOnClickListener { finish() }

        refreshStats()
        refreshHistory()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        refreshHistory()
    }

    private fun refreshStats() {
        val stats = PomodoroHistoryManager.getTodayStats(this)
        binding.todaySessionsText.text = getString(
            R.string.pomodoro_today_sessions, stats.completedSessions, stats.totalSessions
        )
        binding.todayMinutesText.text = getString(
            R.string.pomodoro_today_minutes, stats.totalFocusMinutes
        )
        val total = 8
        val progress = if (total > 0) (stats.completedSessions * 100 / total) else 0
        binding.todayProgressBar.progress = progress
    }

    private fun refreshHistory() {
        binding.historyList.removeAllViews()
        val sessions = PomodoroHistoryManager.loadSessions(this, daysBack = 30)

        if (sessions.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.pomodoro_history_empty)
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                setPadding(0, 4.dpToPx(), 0, 0)
            }
            binding.historyList.addView(emptyView)
            return
        }

        var lastDate = ""
        for (session in sessions.take(30)) {
            val dateStr = formatDate(session.timestampMillis)
            if (dateStr != lastDate) {
                lastDate = dateStr
                val dateHeader = TextView(this).apply {
                    text = dateStr
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                    setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
                }
                binding.historyList.addView(dateHeader)
            }

            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_pomodoro_history, binding.historyList, false)
            val icon = row.findViewById<TextView>(R.id.historyIcon)
            val text = row.findViewById<TextView>(R.id.historyText)
            val duration = row.findViewById<TextView>(R.id.historyDuration)
            val status = row.findViewById<TextView>(R.id.historyStatus)

            icon.text = when (session.type) {
                SessionType.WORK -> "\uD83C\uDF45"
                SessionType.SHORT_BREAK -> "\u2615"
                SessionType.LONG_BREAK -> "\uD83C\uDF34"
            }
            text.text = getString(
                when (session.type) {
                    SessionType.WORK -> R.string.pomodoro_work
                    SessionType.SHORT_BREAK -> R.string.pomodoro_short_break
                    SessionType.LONG_BREAK -> R.string.pomodoro_long_break
                }
            )
            duration.text = "${session.plannedDurationMin}${getString(R.string.pomodoro_min)}"
            status.text = getString(
                if (session.completed) R.string.pomodoro_completed
                else R.string.pomodoro_abandoned
            )
            status.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (session.completed) R.color.primary else R.color.on_surface_variant
                )
            )

            binding.historyList.addView(row)
        }
    }

    private fun formatDate(millis: Long): String {
        val cal = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance()
        cal.timeInMillis = millis

        return if (cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
            cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
        ) {
            "今天"
        } else {
            "${cal.get(java.util.Calendar.YEAR)}-${(cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')}-${cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}"
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.rootLayout.setPadding(0, statusBar.top, 0, navBar.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
