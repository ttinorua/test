package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Global reporting preferences that don't touch the database — purely how
 * dashboard/overview totals group transactions into periods.
 */
object BudgetSettings {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_SHIFT_SALARY = "shift_salary_to_next_month"

    private lateinit var prefs: android.content.SharedPreferences
    private val _shiftSalaryToNextMonth = MutableStateFlow(false)
    val shiftSalaryToNextMonth: StateFlow<Boolean> get() = _shiftSalaryToNextMonth

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _shiftSalaryToNextMonth.value = prefs.getBoolean(KEY_SHIFT_SALARY, false)
    }

    fun setShiftSalaryToNextMonth(enabled: Boolean) {
        _shiftSalaryToNextMonth.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_SHIFT_SALARY, enabled).apply()
        }
    }
}
