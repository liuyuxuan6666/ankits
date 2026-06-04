package com.example.ankits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class TodayStats(
    val completedSessions: Int,
    val totalSessions: Int,
    val totalFocusMinutes: Int
)

object PomodoroHistoryManager {

    private const val PREFS_NAME = "pomodoro_history"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 500

    fun saveSession(context: Context, session: PomodoroSession) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val records = loadAllRecords(prefs).toMutableList()
        records.add(0, session)
        if (records.size > MAX_RECORDS) {
            records.subList(MAX_RECORDS, records.size).clear()
        }
        val jsonArray = JSONArray()
        for (r in records) {
            jsonArray.put(sessionToJson(r))
        }
        prefs.edit().putString(KEY_RECORDS, jsonArray.toString()).apply()
    }

    fun loadSessions(context: Context, daysBack: Int = 30): List<PomodoroSession> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cutoff = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)
        return loadAllRecords(prefs).filter { it.timestampMillis >= cutoff }
    }

    fun getTodayStats(context: Context): TodayStats {
        val sessions = loadSessions(context, daysBack = 1)
        val todayStart = getDayStartMillis()
        val todaySessions = sessions.filter { it.timestampMillis >= todayStart }
        val completed = todaySessions.count { it.completed && it.type == SessionType.WORK }
        val total = todaySessions.count { it.type == SessionType.WORK }
        val focusMinutes = todaySessions
            .filter { it.type == SessionType.WORK }
            .sumOf { it.actualDurationSec } / 60
        return TodayStats(completed, total, focusMinutes)
    }

    fun clearHistory(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun loadAllRecords(prefs: android.content.SharedPreferences): List<PomodoroSession> {
        val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        val records = mutableListOf<PomodoroSession>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                records.add(
                    PomodoroSession(
                        id = obj.getString("id"),
                        type = SessionType.valueOf(obj.getString("type")),
                        plannedDurationMin = obj.getInt("plannedDurationMin"),
                        actualDurationSec = obj.getInt("actualDurationSec"),
                        completed = obj.getBoolean("completed"),
                        timestampMillis = obj.getLong("timestampMillis")
                    )
                )
            }
        } catch (_: Exception) { }
        return records
    }

    private fun sessionToJson(session: PomodoroSession): JSONObject {
        return JSONObject().apply {
            put("id", session.id)
            put("type", session.type.name)
            put("plannedDurationMin", session.plannedDurationMin)
            put("actualDurationSec", session.actualDurationSec)
            put("completed", session.completed)
            put("timestampMillis", session.timestampMillis)
        }
    }

    private fun getDayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
