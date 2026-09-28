package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Anticipated recurring expenses the user has explicitly removed from "Upcoming expenses"
 * (Dashboard > Expenses tile drill-down) — never real transactions, so there's nothing to delete
 * from the database; this just remembers not to anticipate that specific bill again. Keyed by
 * [com.financetracker.app.util.AnticipatedExpense.dismissKey], which bakes in the calendar month,
 * so a dismissal only lasts until that bill either actually posts or the month rolls over, not
 * forever.
 */
object DismissedRecurringExpenses {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_DISMISSED = "dismissed_recurring_expenses"

    private lateinit var prefs: android.content.SharedPreferences
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> get() = _dismissed

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _dismissed.value = prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty()
    }

    fun dismiss(key: String) {
        val updated = _dismissed.value + key
        _dismissed.value = updated
        if (::prefs.isInitialized) {
            prefs.edit().putStringSet(KEY_DISMISSED, updated).apply()
        }
    }
}
