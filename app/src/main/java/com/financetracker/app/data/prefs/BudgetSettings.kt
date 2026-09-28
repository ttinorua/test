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
    private const val KEY_EXCLUDE_TRANSFERS = "exclude_transfers_from_spending"
    private const val KEY_ANTICIPATE_RECURRING = "anticipate_recurring_bills"

    private lateinit var prefs: android.content.SharedPreferences
    private val _shiftSalaryToNextMonth = MutableStateFlow(false)
    val shiftSalaryToNextMonth: StateFlow<Boolean> get() = _shiftSalaryToNextMonth

    private val _excludeTransfersFromSpending = MutableStateFlow(false)
    val excludeTransfersFromSpending: StateFlow<Boolean> get() = _excludeTransfersFromSpending

    private val _anticipateRecurringBills = MutableStateFlow(false)
    val anticipateRecurringBills: StateFlow<Boolean> get() = _anticipateRecurringBills

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _shiftSalaryToNextMonth.value = prefs.getBoolean(KEY_SHIFT_SALARY, false)
        _excludeTransfersFromSpending.value = prefs.getBoolean(KEY_EXCLUDE_TRANSFERS, false)
        _anticipateRecurringBills.value = prefs.getBoolean(KEY_ANTICIPATE_RECURRING, false)
    }

    fun setShiftSalaryToNextMonth(enabled: Boolean) {
        _shiftSalaryToNextMonth.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_SHIFT_SALARY, enabled).apply()
        }
    }

    fun setExcludeTransfersFromSpending(enabled: Boolean) {
        _excludeTransfersFromSpending.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_EXCLUDE_TRANSFERS, enabled).apply()
        }
    }

    fun setAnticipateRecurringBills(enabled: Boolean) {
        _anticipateRecurringBills.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_ANTICIPATE_RECURRING, enabled).apply()
        }
    }
}
