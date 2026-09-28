package com.financetracker.app.data.bank

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.repository.FinanceRepository

private data class LegacyRename(
    val oldMainCategory: String,
    val oldName: String,
    val type: TransactionType,
    val newMainCategory: String,
    val newName: String,
    val newColorHex: String
)

/**
 * One-time cleanup for the app's own original hand-picked category names (see the commit that
 * replaced them with [BankdataDefaultCategories], "Seed the real Enable Banking / Sydbank
 * category taxonomy") that turned out to have a single, unambiguous real-taxonomy equivalent —
 * safe to rename automatically, unlike the old generic buckets in [LegacyCategories] that could
 * mean several different real categories and need per-transaction re-matching instead of a blind
 * rename.
 *
 * Reassigns every transaction still pointing at the old category to the real one, then deletes
 * the old category — the reassignment always happens first since Category's FK is
 * onDelete = SET_NULL, and deleting first would silently uncategorize anything still pointing at
 * it. Idempotent and cheap to call on every launch: once an old category has no transactions left
 * (or never existed on this install, e.g. a fresh install seeded straight from
 * [BankdataDefaultCategories]), there's nothing left to do.
 */
object CategoryCleanup {

    private val RENAMES = listOf(
        LegacyRename("Income", "Salary", TransactionType.INCOME, "Income", "Pay, benefits and pension", "#2E7D32"),
        LegacyRename("Income", "Other Income", TransactionType.INCOME, "Income", "Other income", "#66BB6A"),
        LegacyRename(
            "Clothing and pers. care prod.", "Healthcare", TransactionType.EXPENSE,
            "Clothing and pers. care prod.", "Dentist, doctor and medication", "#FB8C00"
        )
    )

    suspend fun migrateLegacyDuplicates(repository: FinanceRepository) {
        val categories = repository.getCategories()
        val pendingRenames = RENAMES.mapNotNull { rename ->
            categories.firstOrNull {
                it.mainCategory == rename.oldMainCategory && it.name == rename.oldName && it.type == rename.type
            }?.let { rename to it }
        }
        if (pendingRenames.isEmpty()) return

        val allTransactions = repository.getAllTransactions()
        for ((rename, old) in pendingRenames) {
            val target = repository.getOrCreateCategory(
                rename.newMainCategory, rename.newName, rename.type, rename.newColorHex
            )
            allTransactions
                .filter { it.categoryId == old.id }
                .forEach { repository.updateTransaction(it.copy(categoryId = target.id)) }
            repository.deleteCategory(old)
        }
    }
}
