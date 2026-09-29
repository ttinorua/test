package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

/** App-wide appearance: light/dark mode and whether to use Android 12+ wallpaper colors. */
object ThemeSettings {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "theme_dynamic_color"

    private lateinit var prefs: android.content.SharedPreferences
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> get() = _themeMode

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> get() = _dynamicColor

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeMode.value = prefs.getString(KEY_THEME_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        }
    }
}
