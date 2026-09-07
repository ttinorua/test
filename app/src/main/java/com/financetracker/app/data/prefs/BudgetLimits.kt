package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * User-configured monthly spending limits: one optional overall limit, plus optional
 * limits on individual categories. These are compared against actual spend elsewhere
 * (the dashboard) — this object only stores the numbers the user set.
 */
object BudgetLimits {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_OVERALL_BUDGET = "overall_monthly_budget"
    private const val KEY_CATEGORY_BUDGETS = "category_budgets"

    private lateinit var prefs: android.content.SharedPreferences

    private val _overallMonthlyBudget = MutableStateFlow<Double?>(null)
    val overallMonthlyBudget: StateFlow<Double?> get() = _overallMonthlyBudget

    private val _categoryBudgets = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val categoryBudgets: StateFlow<Map<Long, Double>> get() = _categoryBudgets

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _overallMonthlyBudget.value = prefs.getString(KEY_OVERALL_BUDGET, null)?.toDoubleOrNull()
        _categoryBudgets.value = prefs.getStringSet(KEY_CATEGORY_BUDGETS, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val (idPart, amountPart) = entry.split(":", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val id = idPart.toLongOrNull() ?: return@mapNotNull null
                val amount = amountPart.toDoubleOrNull() ?: return@mapNotNull null
                id to amount
            }
            .toMap()
    }

    fun setOverallMonthlyBudget(amount: Double?) {
        _overallMonthlyBudget.value = amount
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_OVERALL_BUDGET, amount?.toString()).apply()
        }
    }

    /** Pass null to remove the budget for [categoryId]. */
    fun setCategoryBudget(categoryId: Long, amount: Double?) {
        _categoryBudgets.value = if (amount == null) {
            _categoryBudgets.value - categoryId
        } else {
            _categoryBudgets.value + (categoryId to amount)
        }
        persistCategoryBudgets()
    }

    private fun persistCategoryBudgets() {
        if (!::prefs.isInitialized) return
        val serialized = _categoryBudgets.value.map { (id, amount) -> "$id:$amount" }.toSet()
        prefs.edit().putStringSet(KEY_CATEGORY_BUDGETS, serialized).apply()
    }
}
