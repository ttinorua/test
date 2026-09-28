package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val ALL_ACCOUNTS_SENTINEL = "all"

/**
 * User-configured monthly spending limits, each scoped to either one account or "All accounts"
 * (a null accountId): one optional overall limit per scope, plus optional limits on individual
 * categories per scope. These are compared against actual spend elsewhere (the dashboard) —
 * this object only stores the numbers the user set.
 */
object BudgetLimits {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_OVERALL_BUDGET = "overall_monthly_budget"
    private const val KEY_CATEGORY_BUDGETS = "category_budgets"
    private const val KEY_OVERALL_BUDGETS_V2 = "overall_monthly_budgets_v2"
    private const val KEY_CATEGORY_BUDGETS_V2 = "category_budgets_v2"

    private lateinit var prefs: android.content.SharedPreferences

    private val _overallBudgets = MutableStateFlow<Map<Long?, Double>>(emptyMap())
    /** Keyed by accountId, with null meaning "All accounts". */
    val overallBudgets: StateFlow<Map<Long?, Double>> get() = _overallBudgets

    private val _categoryBudgets = MutableStateFlow<Map<Pair<Long, Long?>, Double>>(emptyMap())
    /** Keyed by (categoryId, accountId), with a null accountId meaning "All accounts". */
    val categoryBudgets: StateFlow<Map<Pair<Long, Long?>, Double>> get() = _categoryBudgets

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val storedOverall = prefs.getStringSet(KEY_OVERALL_BUDGETS_V2, null)
        if (storedOverall != null) {
            _overallBudgets.value = deserializeOverall(storedOverall)
        } else {
            // One-time migration from the old single-value (All accounts only) storage.
            val legacy = prefs.getString(KEY_OVERALL_BUDGET, null)?.toDoubleOrNull()
            _overallBudgets.value = if (legacy != null) mapOf(null to legacy) else emptyMap()
            persistOverallBudgets()
        }

        val storedCategory = prefs.getStringSet(KEY_CATEGORY_BUDGETS_V2, null)
        if (storedCategory != null) {
            _categoryBudgets.value = deserializeCategory(storedCategory)
        } else {
            val legacy = prefs.getStringSet(KEY_CATEGORY_BUDGETS, emptySet())
                .orEmpty()
                .mapNotNull { entry ->
                    val (idPart, amountPart) = entry.split(":", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                    val id = idPart.toLongOrNull() ?: return@mapNotNull null
                    val amount = amountPart.toDoubleOrNull() ?: return@mapNotNull null
                    (id to null as Long?) to amount
                }
                .toMap()
            _categoryBudgets.value = legacy
            persistCategoryBudgets()
        }
    }

    fun overallBudgetFor(accountId: Long?): Double? = _overallBudgets.value[accountId]

    /** Pass null to remove the overall budget for this scope. */
    fun setOverallBudget(accountId: Long?, amount: Double?) {
        _overallBudgets.value = if (amount == null) {
            _overallBudgets.value - accountId
        } else {
            _overallBudgets.value + (accountId to amount)
        }
        persistOverallBudgets()
    }

    fun categoryBudgetFor(categoryId: Long, accountId: Long?): Double? = _categoryBudgets.value[categoryId to accountId]

    /** Pass null to remove the budget for [categoryId] in this scope. */
    fun setCategoryBudget(categoryId: Long, accountId: Long?, amount: Double?) {
        val key = categoryId to accountId
        _categoryBudgets.value = if (amount == null) {
            _categoryBudgets.value - key
        } else {
            _categoryBudgets.value + (key to amount)
        }
        persistCategoryBudgets()
    }

    private fun persistOverallBudgets() {
        if (!::prefs.isInitialized) return
        val serialized = _overallBudgets.value.map { (accountId, amount) ->
            "${accountId?.toString() ?: ALL_ACCOUNTS_SENTINEL}:$amount"
        }.toSet()
        prefs.edit().putStringSet(KEY_OVERALL_BUDGETS_V2, serialized).apply()
    }

    private fun persistCategoryBudgets() {
        if (!::prefs.isInitialized) return
        val serialized = _categoryBudgets.value.map { (key, amount) ->
            val (categoryId, accountId) = key
            "$categoryId:${accountId?.toString() ?: ALL_ACCOUNTS_SENTINEL}:$amount"
        }.toSet()
        prefs.edit().putStringSet(KEY_CATEGORY_BUDGETS_V2, serialized).apply()
    }

    private fun deserializeOverall(raw: Set<String>): Map<Long?, Double> =
        raw.mapNotNull { entry ->
            val parts = entry.split(":", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val accountId = parts[0].takeIf { it != ALL_ACCOUNTS_SENTINEL }?.toLongOrNull()
            if (parts[0] != ALL_ACCOUNTS_SENTINEL && accountId == null) return@mapNotNull null
            val amount = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            accountId to amount
        }.toMap()

    private fun deserializeCategory(raw: Set<String>): Map<Pair<Long, Long?>, Double> =
        raw.mapNotNull { entry ->
            val parts = entry.split(":", limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
            val categoryId = parts[0].toLongOrNull() ?: return@mapNotNull null
            val accountId = parts[1].takeIf { it != ALL_ACCOUNTS_SENTINEL }?.toLongOrNull()
            if (parts[1] != ALL_ACCOUNTS_SENTINEL && accountId == null) return@mapNotNull null
            val amount = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            (categoryId to accountId) to amount
        }.toMap()
}
