package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Which [ThemeMode] the app renders in — read by
 * [com.financetracker.app.ui.theme.PersonalFinanceTheme] to resolve whether it's actually in
 * dark mode right now ([ThemeMode.SYSTEM] still follows the device setting). */
object ThemeSettings {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    private lateinit var prefs: android.content.SharedPreferences
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> get() = _themeMode

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_THEME_MODE, null)
        _themeMode.value = stored?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
    }
}
