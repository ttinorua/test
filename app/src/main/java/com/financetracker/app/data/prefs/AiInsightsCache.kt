package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Caches the last AI-generated dashboard insight so it survives navigating away and
 * back without re-billing an API call. Generation itself is always user-initiated
 * (a button), never automatic, to keep AI spend predictable.
 */
object AiInsightsCache {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_TEXT = "ai_insights_text"

    private lateinit var prefs: android.content.SharedPreferences
    private val _insight = MutableStateFlow<String?>(null)
    val insight: StateFlow<String?> get() = _insight

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _insight.value = prefs.getString(KEY_TEXT, null)
    }

    fun save(text: String) {
        _insight.value = text
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_TEXT, text).apply()
        }
    }
}
