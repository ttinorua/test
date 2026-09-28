package com.financetracker.app.data.prefs

import android.content.Context
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which expense categories the user has marked "Fixe" (a fixed/recurring cost) in Settings —
 * drives [com.financetracker.app.util.anticipatedRecurringExpenses]'s cadence-aware detection the
 * same way the app's own guessed set of "trusted" categories used to, except the user now
 * controls it directly instead of it being hardcoded. Kept in SharedPreferences rather than a
 * Category table column: this app's Room database still uses
 * [com.financetracker.app.data.db.AppDatabase]'s fallbackToDestructiveMigration, so a schema
 * change would wipe every real transaction on the next launch — the same reason
 * [BudgetLimits] and [DismissedRecurringExpenses] keep their own per-category data out of Room.
 */
object FixedExpenseCategories {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_FIXED = "fixed_expense_category_ids"
    private const val KEY_DEFAULTS_SEEDED = "fixed_expense_defaults_seeded"

    /** The categories this app used to hardcode as always-trusted, before this became a
     * user-editable per-category toggle — seeded once, the first time this runs on an install
     * that's never touched the setting, so upgrading doesn't silently stop anticipating bills
     * that were already being anticipated. */
    private val LEGACY_DEFAULT_NAMES = setOf("interest and fees", "consumer loan", "loan and debt (other)", "electricity")
    private const val LEGACY_DEFAULT_MAIN_CATEGORY = "insurance"

    private lateinit var prefs: android.content.SharedPreferences
    private val _fixedCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    val fixedCategoryIds: StateFlow<Set<Long>> get() = _fixedCategoryIds

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _fixedCategoryIds.value = prefs.getStringSet(KEY_FIXED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun setFixed(categoryId: Long, fixed: Boolean) {
        val updated = if (fixed) _fixedCategoryIds.value + categoryId else _fixedCategoryIds.value - categoryId
        _fixedCategoryIds.value = updated
        if (::prefs.isInitialized) {
            prefs.edit().putStringSet(KEY_FIXED, updated.map { it.toString() }.toSet()).apply()
        }
    }

    /** Runs once per install, ever — marks whichever of [categories] match the old hardcoded
     * trusted set as Fixe, then never touches the setting again, so a user who later un-marks
     * one doesn't have it silently re-added. */
    fun seedLegacyDefaultsIfNeeded(categories: List<Category>) {
        if (!::prefs.isInitialized || prefs.getBoolean(KEY_DEFAULTS_SEEDED, false)) return

        val toMark = categories.filter { category ->
            category.type == TransactionType.EXPENSE &&
                (category.mainCategory.trim().lowercase() == LEGACY_DEFAULT_MAIN_CATEGORY ||
                    category.name.trim().lowercase() in LEGACY_DEFAULT_NAMES)
        }.map { it.id }.toSet()

        if (toMark.isNotEmpty()) {
            val updated = _fixedCategoryIds.value + toMark
            _fixedCategoryIds.value = updated
            prefs.edit().putStringSet(KEY_FIXED, updated.map { it.toString() }.toSet()).apply()
        }
        prefs.edit().putBoolean(KEY_DEFAULTS_SEEDED, true).apply()
    }
}
