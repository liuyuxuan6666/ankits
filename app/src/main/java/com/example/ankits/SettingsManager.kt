package com.example.ankits

import android.content.Context

object SettingsManager {
    private const val PREFS_NAME = "ankits_settings"

    fun isToolEnabled(context: Context, toolKey: String): Boolean {
        return getPrefs(context).getBoolean(toolKey, true)
    }

    fun setToolEnabled(context: Context, toolKey: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(toolKey, enabled).apply()
    }

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
