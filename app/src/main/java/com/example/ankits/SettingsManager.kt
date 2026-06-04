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

    fun isFavorite(context: Context, toolKey: String): Boolean {
        return getPrefs(context).getBoolean("fav_$toolKey", false)
    }

    fun setFavorite(context: Context, toolKey: String, favorite: Boolean) {
        getPrefs(context).edit().putBoolean("fav_$toolKey", favorite).apply()
    }

    fun getString(context: Context, key: String, default: String = ""): String {
        return getPrefs(context).getString(key, default) ?: default
    }

    fun setString(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getInt(context: Context, key: String, default: Int = 0): Int {
        return getPrefs(context).getInt(key, default)
    }

    fun setInt(context: Context, key: String, value: Int) {
        getPrefs(context).edit().putInt(key, value).apply()
    }

    fun getBool(context: Context, key: String, default: Boolean = true): Boolean {
        return getPrefs(context).getBoolean(key, default)
    }

    fun setBool(context: Context, key: String, value: Boolean) {
        getPrefs(context).edit().putBoolean(key, value).apply()
    }

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
